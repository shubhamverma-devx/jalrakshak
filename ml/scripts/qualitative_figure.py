"""
qualitative_figure.py — deck ke liye behtar sample-predictions figure.

KYUN ALAG (notebook mein already ek figure hai):
  Notebook ka figure test_ds ke sorted order se pehle 4 chips uthata hai — aur wo
  saare "Ghana" nikle, jo hamare weakest regions mein se ek hai. Us figure se
  na model ki asli range dikhti hai, na India (jahan hum deploy karenge).

  Ye script har region se ek representative chip uthati hai, aur India ko sabse
  upar rakhti hai — kyunki judge ka pehla sawaal wahi hoga.

  Notebook ka figure jaan-bujh ke waisa hi chhoda hai (wo executed run ka
  imaandaar record hai). Ye uske alawa hai, uski jagah nahi.
"""
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import rasterio
import segmentation_models_pytorch as smp
import torch

HERE = Path(__file__).parent.parent
DB_MIN, DB_MAX, THR = -50.0, 1.0, 0.5
# India pehle — baaki IoU ke hisaab se achhe se kharaab tak, taaki range dikhe
REGIONS = ["India", "Mekong", "Sri-Lanka", "Ghana", "Pakistan"]

dev = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
ck = torch.load(HERE / "models" / "sar_unet.pth", map_location=dev, weights_only=False)
model = smp.Unet("resnet34", encoder_weights=None, in_channels=2, classes=1)
model.load_state_dict(ck["model"]); model.to(dev).eval()

S1, LB = HERE / "data/test/S1", HERE / "data/test/Label"


def load(name):
    with rasterio.open(S1 / name) as s:
        img = s.read().astype(np.float32)
    with rasterio.open(LB / name.replace("S1Hand", "LabelHand")) as s:
        lab = s.read(1).astype(np.float32)
    img = np.nan_to_num(img, nan=DB_MIN, posinf=DB_MAX, neginf=DB_MIN)
    img = (np.clip(img, DB_MIN, DB_MAX) - DB_MIN) / (DB_MAX - DB_MIN)
    return img, lab


picks = []
for reg in REGIONS:
    best = None
    for p in sorted(S1.glob(f"{reg}_*.tif")):
        _, lab = load(p.name)
        valid = lab != -1
        # NaN bug se bachao: pehle check karo ki koi valid pixel hai bhi ya nahi.
        # (Notebook wale figure mein yahi chhoot gaya tha — Ghana_83483 poora no-data
        #  hai, uska mean NaN aaya, aur "NaN < 0.05" False hone se wo figure mein ghus gaya.)
        if valid.sum() == 0:
            continue
        frac = float((lab[valid] == 1).mean())
        if 0.08 < frac < 0.6 and (best is None or abs(frac - 0.25) < best[1]):
            best = (p.name, abs(frac - 0.25))
    if best:
        picks.append((reg, best[0]))

fig, axes = plt.subplots(len(picks), 4, figsize=(15, 3.5 * len(picks)))
for r, (reg, name) in enumerate(picks):
    img, lab = load(name)
    with torch.no_grad():
        pr = torch.sigmoid(model(torch.from_numpy(img).unsqueeze(0).to(dev)))[0, 0].cpu().numpy()
    pred = (pr > THR)
    gt = lab == 1
    valid = lab != -1
    tp = int((pred & gt & valid).sum()); fp = int((pred & ~gt & valid).sum())
    fn = int((~pred & gt & valid).sum())
    iou = tp / max(tp + fp + fn, 1)

    ov = np.zeros((*gt.shape, 3))
    ov[..., 0] = (pred & ~gt & valid); ov[..., 1] = (pred & gt & valid); ov[..., 2] = (~pred & gt & valid)

    axes[r, 0].imshow(img[0], cmap="gray")
    axes[r, 1].imshow(gt, cmap="Blues", vmin=0, vmax=1)
    axes[r, 2].imshow(pred, cmap="Blues", vmin=0, vmax=1)
    axes[r, 3].imshow(ov)
    axes[r, 0].set_ylabel(f"{reg}\nIoU {iou:.3f}", fontsize=10)
    if r == 0:
        for a, t in zip(axes[r], ["Sentinel-1 VV (SAR)", "ground truth", "model prediction",
                                  "TP green · FP red · FN blue"]):
            a.set_title(t, fontsize=11)
    for a in axes[r]:
        a.set_xticks([]); a.set_yticks([])

plt.suptitle("SAR flood segmentation — test-set examples by region", fontsize=13, y=1.001)
plt.tight_layout()
out = HERE / "outputs" / "sample_predictions_by_region.png"
plt.savefig(out, dpi=120, bbox_inches="tight")
print("wrote", out)
for reg, name in picks:
    print(f"  {reg:<12} {name}")
