#!/usr/bin/env python3
"""
predict.py — ek Sentinel-1 SAR chip do, paani ka mask aur doobe hue area (sq km) lo.

USAGE
    python predict.py path/to/S1.tif
    python predict.py path/to/S1.tif --out-mask mask.tif --out-geojson water.geojson
    python predict.py path/to/S1.tif --json          # sirf JSON (dashboard/API ke liye)

INPUT  : 2-band GeoTIFF — Sentinel-1 VV + VH, decibel scale (Sen1Floods11 jaisa)
OUTPUT : JSON — flooded area sq km, water pixel %, aur optionally mask GeoTIFF + GeoJSON polygons

KYUN YE FILE ALAG HAI (notebook se): notebook TRAINING ka saboot hai — wo dobara chalane
ke liye nahi hai. Ye inference ka entry point hai: dashboard ka "Satellite" tab isi ko
call karega (BUILD_PLAN B1). Isliye ye chhoti, dependency-light aur CLI-friendly hai.

IMANDAARI (CLAUDE.md ke honesty rules):
  - Ye model **segmentation** karta hai: "is image mein paani KAHAN hai".
    Ye forecast NAHI karta — "kal kahan paani hoga" ye model nahi bata sakta.
  - Area ek estimate hai. Pixel 10m ka hai, to 100 sq m se chhoti cheezein miss hongi.
  - Model ki asli test performance ml/README.md aur ml/outputs/metrics.json mein hai.
    Wahi number bolna, badha ke nahi.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
import rasterio
import torch

HERE = Path(__file__).parent
DEFAULT_CKPT = HERE / "models" / "sar_unet.pth"

# Training ke waqt jo normalization use hui thi, bilkul wahi. Ye badla to model
# ka input distribution badal jaayega aur output bakwaas ho jaayega.
DB_MIN, DB_MAX = -50.0, 1.0


def pick_device() -> torch.device:
    """CUDA > MPS > CPU. Droplet pe CPU hi milega — 512x512 pe wo bhi theek chalta hai."""
    if torch.cuda.is_available():
        return torch.device("cuda")
    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def load_model(ckpt_path: Path, device: torch.device):
    """
    Trained U-Net load karo.
    INPUT : checkpoint path | OUTPUT: (model in eval mode, saved cfg dict)
    """
    import segmentation_models_pytorch as smp

    if not ckpt_path.exists():
        # Weights repo mein nahi hain (93 MB) — Release se aate hain. Jo banda yahan
        # phansa hai use SEEDHA command chahiye, "kahin se download kar lo" nahi.
        sys.exit(
            f"Model nahi mila: {ckpt_path}\n\n"
            "Trained weights repo mein nahi hain (93 MB). Release se le lo:\n\n"
            "  mkdir -p ml/models\n"
            "  curl -L -o ml/models/sar_unet.pth \\\n"
            "    https://github.com/shubhamverma-devx/jalrakshak/releases/download/v1.0/sar_unet.pth\n\n"
            "Ya khud train karo: train_sar_unet.ipynb (ml/README.md dekho)."
        )

    ck = torch.load(ckpt_path, map_location=device, weights_only=False)
    cfg = ck.get("cfg", {})

    model = smp.Unet(
        encoder_name=cfg.get("encoder", "resnet34"),
        encoder_weights=None,          # weights checkpoint se aa rahe hain
        in_channels=cfg.get("in_channels", 2),
        classes=1,
    )
    model.load_state_dict(ck["model"])
    return model.to(device).eval(), cfg


def normalise(arr: np.ndarray) -> np.ndarray:
    """dB -> [0,1]. Training ke saath EXACT same. NaN ko floor pe bhejte hain."""
    arr = np.nan_to_num(arr, nan=DB_MIN, posinf=DB_MAX, neginf=DB_MIN)
    return (np.clip(arr, DB_MIN, DB_MAX) - DB_MIN) / (DB_MAX - DB_MIN)


def pixel_area_km2(src) -> float:
    """
    Ek pixel kitne sq km ka hai.

    KYUN ITNA DHYAAN: Sen1Floods11 chips EPSG:4326 (degrees) mein hain, meters mein nahi.
    Degree ko seedha meter maan lena — ya har jagah "10m x 10m" hardcode kar dena —
    area ko poori tarah galat kar deta hai, aur wo galat number officer ko dikhega.

    - Projected CRS (UTM etc): resolution already meters mein hai, seedha multiply.
    - Geographic CRS (4326): 1 deg latitude ~ 111,320 m; longitude cos(lat) se sikudta hai.
      Isliye image ke CENTRE latitude pe hisaab lagate hain.
    """
    res_x, res_y = abs(src.res[0]), abs(src.res[1])

    if src.crs and src.crs.is_projected:
        return (res_x * res_y) / 1e6

    # Geographic (degrees)
    centre_lat = src.bounds.bottom + (src.bounds.top - src.bounds.bottom) / 2
    m_per_deg_lat = 111_320.0
    m_per_deg_lon = 111_320.0 * math.cos(math.radians(centre_lat))
    return (res_x * m_per_deg_lon) * (res_y * m_per_deg_lat) / 1e6


@torch.no_grad()
def predict(image_path: Path, ckpt: Path, threshold: float):
    """
    Ek SAR chip pe inference.
    OUTPUT: (result dict, water mask uint8 [H,W], rasterio profile)
    """
    device = pick_device()
    model, cfg = load_model(ckpt, device)
    thr = threshold if threshold is not None else cfg.get("threshold", 0.5)

    with rasterio.open(image_path) as src:
        raw = src.read().astype(np.float32)
        profile = src.profile.copy()
        px_km2 = pixel_area_km2(src)
        crs = str(src.crs)
        b = src.bounds
        # Bounds bhi lautate hain — dashboard ka Leaflet map isi pe zoom karta hai.
        # [[south, west], [north, east]] — Leaflet ka fitBounds isi order mein leta hai.
        bounds = [[b.bottom, b.left], [b.top, b.right]]
        centre = [(b.bottom + b.top) / 2, (b.left + b.right) / 2]

    if raw.shape[0] < 2:
        sys.exit(f"2 band chahiye (VV, VH), mile {raw.shape[0]}. Ye Sentinel-1 GRD chip hona chahiye.")
    raw = raw[:2]

    x = torch.from_numpy(normalise(raw)).unsqueeze(0).to(device)

    # U-Net 32 ke multiple maangta hai (5 downsamples). Odd size aane pe pad karo,
    # phir wapas crop — warna shape mismatch pe crash hota hai.
    _, _, h, w = x.shape
    ph, pw = (-h) % 32, (-w) % 32
    if ph or pw:
        x = torch.nn.functional.pad(x, (0, pw, 0, ph), mode="reflect")

    prob = torch.sigmoid(model(x))[0, 0].cpu().numpy()[:h, :w]
    mask = (prob > thr).astype(np.uint8)

    water_px = int(mask.sum())
    total_px = int(mask.size)

    result = {
        "image": str(image_path),
        "crs": crs,
        "bounds": bounds,
        "centre": centre,
        "threshold": round(float(thr), 3),
        "pixel_area_km2": px_km2,
        "water_pixels": water_px,
        "total_pixels": total_px,
        "water_fraction": round(water_px / total_px, 6) if total_px else 0.0,
        "flooded_area_sq_km": round(water_px * px_km2, 4),
        "mean_water_confidence": round(float(prob[mask == 1].mean()), 4) if water_px else 0.0,
        "device": str(device),
    }
    return result, mask, profile


def write_mask(mask: np.ndarray, profile: dict, out_path: Path) -> None:
    """Mask ko GeoTIFF mein likho — georeferencing bachi rahe taaki QGIS/map pe overlay ho sake."""
    profile.update(count=1, dtype="uint8", nodata=None, compress="deflate")
    with rasterio.open(out_path, "w", **profile) as dst:
        dst.write(mask, 1)


def write_geojson(mask: np.ndarray, profile: dict, out_path: Path) -> int:
    """
    Water polygons GeoJSON mein.
    KYUN: dashboard ka Leaflet map raster nahi, polygon overlay leta hai (BUILD_PLAN B1 —
    "map pe flooded polygon overlay"). Yahin se wo feed hoga.
    OUTPUT: kitne polygon bane
    """
    from rasterio.features import shapes

    feats = []
    for geom, val in shapes(mask, mask=mask == 1, transform=profile["transform"]):
        feats.append({"type": "Feature", "geometry": geom, "properties": {"water": int(val)}})

    out_path.write_text(json.dumps({"type": "FeatureCollection", "features": feats}))
    return len(feats)


def main() -> int:
    ap = argparse.ArgumentParser(description="SAR flood segmentation — water mask + flooded area")
    ap.add_argument("image", type=Path, help="2-band Sentinel-1 GeoTIFF (VV, VH) in dB")
    ap.add_argument("--model", type=Path, default=DEFAULT_CKPT)
    ap.add_argument("--threshold", type=float, default=None, help="default: checkpoint se")
    ap.add_argument("--out-mask", type=Path, help="water mask GeoTIFF yahan likho")
    ap.add_argument("--out-geojson", type=Path, help="water polygons GeoJSON yahan likho")
    ap.add_argument("--json", action="store_true", help="sirf JSON print karo")
    args = ap.parse_args()

    if not args.image.exists():
        sys.exit(f"Image nahi mili: {args.image}")

    result, mask, profile = predict(args.image, args.model, args.threshold)

    if args.out_mask:
        write_mask(mask, profile, args.out_mask)
        result["mask_path"] = str(args.out_mask)
    if args.out_geojson:
        result["polygons"] = write_geojson(mask, profile, args.out_geojson)
        result["geojson_path"] = str(args.out_geojson)

    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(f"\n  file            : {args.image.name}")
        print(f"  flooded area    : {result['flooded_area_sq_km']} sq km")
        print(f"  water coverage  : {result['water_fraction'] * 100:.2f}% of chip")
        print(f"  water pixels    : {result['water_pixels']:,} / {result['total_pixels']:,}")
        print(f"  confidence      : {result['mean_water_confidence']} (mean over water px)")
        print(f"  threshold       : {result['threshold']}   device: {result['device']}")
        if args.out_mask:
            print(f"  mask            : {args.out_mask}")
        if args.out_geojson:
            print(f"  geojson         : {args.out_geojson} ({result['polygons']} polygons)")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
