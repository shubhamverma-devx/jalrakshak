# Model Card — JalRakshak SAR Flood Segmentation (B1)

| | |
|---|---|
| **Model** | `sar_unet.pth` — U-Net, ResNet34 encoder (ImageNet pretrained) |
| **Version** | 1.0 |
| **Trained** | 22 Aug 2026 |
| **Task** | Binary semantic segmentation — surface water in Sentinel-1 SAR |
| **Input** | 2-band GeoTIFF: Sentinel-1 VV + VH, decibel, any size (32 ka multiple pad ho jaata hai) |
| **Output** | Per-pixel water probability → threshold → binary mask + area (sq km) |
| **Params** | 24.43 M |
| **Framework** | PyTorch 2.8 + segmentation-models-pytorch 0.5 |
| **Trained on** | Apple M4 (MPS), 28 min, 40 epochs |
| **Licence (data)** | Sen1Floods11, CC-BY 4.0 |
| **Team** | Blackbox · SIH26071 (MoES/IMD) |

---

## Intended use

**Kis liye banaya:** Ek Sentinel-1 SAR image mein **abhi paani kahan hai** — ye map karna,
aur doobe hue area ka mota-mota hisaab dena. JalRakshak officer dashboard ke "Satellite"
tab mein iska output flooded-polygon overlay ban ke dikhta hai.

**Kis liye NAHI:**
- ❌ Forecasting — "kal kahan paani hoga" ye model nahi bata sakta (wo B2 LSTM ka kaam hai)
- ❌ Hydrodynamic/flow simulation — ye ek image ka snapshot classify karta hai, bas
- ❌ Legal/insurance damage assessment — accuracy us kaam layak nahi
- ❌ Akela decision lena — ye ek **decision support** signal hai, aakhri faisla nahi
- ❌ Paani ki gehrai — ye model gehrai kuch nahi jaanta, sirf "paani hai / nahi hai"

**Users:** district flood control officers, JalRakshak dashboard ka backend.

---

## Training data

Sen1Floods11 **hand-labeled subset**, official splits: **252 train / 89 valid / 90 test**.
11 flood events: Bolivia, Ghana, India, Mekong, Nigeria, Pakistan, Paraguay, Somalia,
Spain, Sri-Lanka, USA. (Bolivia official splits se bahar hai — generalisation set.)

Labels: `1` water · `0` not water · `-1` no data.
**`-1` pixels (train ka 13.3%) loss aur metrics dono se hata diye gaye.**

Class balance (train, valid pixels): **~9.5% water** — imbalanced, isliye Dice loss.

Known data issues (humne khud check kiye): 5 chips poore no-data hain (train 1, valid 3,
test 1 — sab Ghana); 47 chips mein paani bilkul nahi.

---

## Evaluation

Held-out **test split (90 chips)** — na training mein, na model selection mein
(wo validation pe hua). Sirf valid pixels. Water = positive class.

<!-- METRICS:START -->
**Test set — 90 held-out chips, threshold 0.5**

| Metric | Value |
|---|---|
| **IoU (water)** | **0.6489** |
| **F1 (water)** | **0.7871** |
| Precision | 0.8038 |
| Recall | 0.7710 |
| Pixel accuracy | 0.9478 |

Confusion matrix (valid pixels only, water = positive):

| | pred: not water | pred: water |
|---|---|---|
| **true: not water** | 17,468,287 | 482,979 |
| **true: water** | 587,579 | 1,978,522 |

Threshold sweep (test set):

| thr | IoU | F1 | Precision | Recall |
|---|---|---|---|---|
| 0.2 | 0.6337 | 0.7758 | 0.7189 | 0.8425 |
| 0.3 | 0.6452 | 0.7843 | 0.7554 | 0.8155 |
| 0.4 | 0.6489 | 0.7871 | 0.7814 | 0.7928 |
| 0.5 | 0.6489 | 0.7871 | 0.8038 | 0.7710 |
| 0.6 | 0.6457 | 0.7847 | 0.8260 | 0.7474 |
| 0.7 | 0.6369 | 0.7782 | 0.8511 | 0.7168 |
| 0.8 | 0.6197 | 0.7652 | 0.8831 | 0.6750 |

Best IoU **0.6489** at threshold **0.5**.

Training: best val IoU **0.6423** at epoch 30 of 40 run · mps · 24.43M params.
<!-- METRICS:END -->

### Region-wise

<!-- REGIONS:START -->
| Region | IoU | Recall |
|---|---|---|
| Nigeria | 0.8864 | 0.9547 |
| Mekong | 0.8507 | 0.9295 |
| Sri-Lanka | 0.7828 | 0.8658 |
| Spain | 0.7226 | 0.8799 |
| **India** | 0.7044 | 0.8069 |
| USA | 0.5710 | 0.7880 |
| Ghana | 0.5474 | 0.5907 |
| Paraguay | 0.5074 | 0.6688 |
| Somalia | 0.4877 | 0.5585 |
| **Pakistan** | 0.2432 | 0.3536 |
<!-- REGIONS:END -->

Padhne ka tareeka: jahan paani **bada aur ek jagah** hai wahan model achha hai
(Nigeria, Mekong); jahan **patla aur bikhra** hai wahan girta hai (USA 3% water,
Paraguay, Ghana). **India hamare use-case ke sabse kareeb hai.** **Pakistan lagbhag
fail hai** — wajah neeche "Limitations" point 2 mein.

---

## Limitations aur risks

**Ye model galtiyan karta hai. Kaunsi, ye pata hona chahiye:**

1. **~23% paani chhoot jaata hai** (recall 0.771 @ 0.5). Flood mapping mein ye kaafi hai.
   Mitigation: operational threshold **0.3** (recall 0.816) — thoda zyada false alarm ke saath.
2. **Pakistan-type terrain pe fail (IoU 0.243).** Irrigated farmland aur geeli mitti ki
   radar backscatter paani jaisi hoti hai. **Assam ke dhaan ke khet bhi aisa hi kar sakte hain** —
   ye hamare deployment ke liye sabse bada risk hai.
3. **Assam pe validate kiya — par sirf 14 chips pe.** India test chips
   lat 25.73-27.21, lon 92.75-93.80 pe hain = Brahmaputra valley, wahi belt jahan
   JalRakshak deploy hoga. IoU 0.704. Par 14 chips ~350 sq km hain, aur poora Assam
   78,000 sq km. Bade deployment se pehle zyada scenes chahiye — alag mausam,
   alag fasal cycle.
4. **Sirf 252 chips pe train hua** — bahut kam. Full 4,831 pe weak-label pre-training se
   sudhar milna chahiye; abhi kiya nahi.
5. **Patla/bikhra paani kamzori hai** — chhoti nadiyan, gaon ke beech bhara paani.
6. **10 m resolution** — 100 sq m se chhoti cheez dikhegi hi nahi.
7. **SAR ki physics ki seema:** smooth surfaces (geeli sadak, dhaatu chhat) paani jaise
   dikh sakte hain (false positive); ghane ped ke neeche ka paani dikhta hi nahi (false negative).
8. **Sirf VV+VH.** Elevation/DEM, slope, permanent-water prior — kuch use nahi kiya.
   Ye sab jodne se sudhar hona chahiye.
9. **Published baselines se like-for-like tulna nahi ki** — unka exact protocol reproduce
   nahi kiya. Isliye koi comparative claim nahi.

### Kis pe asar padega agar model galat hua

False negative (paani chhoot gaya) → gaon ko warning nahi mili → **jaan ka khatra**.
False positive (galat paani) → faltu evacuation → bharosa tootta hai, agli baar log alert
ignore karte hain.

**Isi liye ye system mein akela faisla lene wala nahi hai** — rule-based RiskEngine
(rainfall + river level + elevation) apna alag signal deta hai, aur officer dono dekh ke
faisla leta hai.

---

## Ethical / honesty notes

Ye JalRakshak ke honesty rules ke andar aata hai (CLAUDE.md, BUILD_PLAN Part E):

- Ye **trained model hai** (U-Net, Sen1Floods11) — ise ML bolna sahi hai
- **RiskEngine (A2) ML NAHI hai** — wo IMD thresholds pe rule-based if-else hai.
  Dono ko ek saath "AI" bol dena jhooth hoga
- Ye **"hydrodynamic simulation" nahi hai**
- Metrics jo hain wahi bolne hain — IoU **0.649**, badha ke nahi

---

## Reproduce

```bash
cd ml
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python download_data.py
jupyter nbconvert --to notebook --execute --inplace train_sar_unet.ipynb
```
Seed 42 fix hai (`random`, `numpy`, `torch`). Ek-do decimal ka farq GPU non-determinism
se aa sakta hai.

**Weights:** `models/sar_unet.pth` 98 MB hai — gitignored. GitHub Release ya Drive se share karo.
Repo mein notebook + metrics + figures hain, wahi saboot hain.
