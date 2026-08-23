"""
per_region_eval.py — test set ka region-wise breakdown.

KYUN: overall IoU ek average hai, aur average asli kamzori chhupa deta hai.
Hum ye system ASSAM ke liye bana rahe hain, to sabse zaroori sawaal hai:
"India ke chips pe ye kaisa chalta hai?" Wo number alag se dekhna chahiye —
aur README mein likhna chahiye, chahe wo overall se kharaab ho.
"""
import json
from collections import defaultdict
from pathlib import Path

import numpy as np
import rasterio
import torch
import segmentation_models_pytorch as smp

HERE = Path(__file__).parent.parent
DB_MIN, DB_MAX = -50.0, 1.0
THR = 0.5

dev = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
ck = torch.load(HERE / "models" / "sar_unet.pth", map_location=dev, weights_only=False)
model = smp.Unet("resnet34", encoder_weights=None, in_channels=2, classes=1)
model.load_state_dict(ck["model"]); model.to(dev).eval()

agg = defaultdict(lambda: dict(tp=0, fp=0, fn=0, tn=0, chips=0))

s1_dir = HERE / "data/test/S1"
for p in sorted(s1_dir.glob("*.tif")):
    region = p.name.split("_")[0]
    with rasterio.open(p) as s:
        img = s.read().astype(np.float32)
    with rasterio.open(HERE / "data/test/Label" / p.name.replace("S1Hand", "LabelHand")) as s:
        lab = s.read(1).astype(np.float32)

    img = np.nan_to_num(img, nan=DB_MIN, posinf=DB_MAX, neginf=DB_MIN)
    img = (np.clip(img, DB_MIN, DB_MAX) - DB_MIN) / (DB_MAX - DB_MIN)

    with torch.no_grad():
        pr = torch.sigmoid(model(torch.from_numpy(img).unsqueeze(0).to(dev)))[0, 0].cpu().numpy()

    valid = lab != -1
    pred = (pr > THR)[valid]
    tgt = (lab == 1)[valid]

    a = agg[region]
    a["tp"] += int((pred & tgt).sum()); a["fp"] += int((pred & ~tgt).sum())
    a["fn"] += int((~pred & tgt).sum()); a["tn"] += int((~pred & ~tgt).sum())
    a["chips"] += 1

rows = []
for r, a in agg.items():
    tp, fp, fn, tn = a["tp"], a["fp"], a["fn"], a["tn"]
    e = 1e-9
    rows.append(dict(
        region=r, chips=a["chips"],
        water_pct=round(100 * (tp + fn) / (tp + fp + fn + tn + e), 2),
        iou=round(tp / (tp + fp + fn + e), 4),
        f1=round(2 * tp / (2 * tp + fp + fn + e), 4),
        precision=round(tp / (tp + fp + e), 4),
        recall=round(tp / (tp + fn + e), 4),
    ))
rows.sort(key=lambda d: -d["iou"])

print(f"{'region':<12}{'chips':>6}{'water%':>8}{'IoU':>9}{'F1':>9}{'prec':>9}{'recall':>9}")
for d in rows:
    print(f"{d['region']:<12}{d['chips']:>6}{d['water_pct']:>8.2f}{d['iou']:>9.4f}"
          f"{d['f1']:>9.4f}{d['precision']:>9.4f}{d['recall']:>9.4f}")

(HERE / "outputs" / "per_region_test.json").write_text(json.dumps(rows, indent=2))
print("\n-> outputs/per_region_test.json")
