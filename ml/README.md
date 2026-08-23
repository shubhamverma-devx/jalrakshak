# B1 — SAR Flood Segmentation

**JalRakshak** · SIH26071 (MoES/IMD) · Team Blackbox

Sentinel-1 **SAR** (radar) satellite image se paani ka mask nikalne wala trained model,
aur doobe hue area ka hisaab (sq km).

Radar isliye — optical satellite (Sentinel-2) baadal ke paar nahi dekh sakta, aur baadh
ke waqt aasman hamesha bhara rehta hai. SAR baadal ke aar-paar dekh leta hai, din ho ya raat.

---

## Ye kya karta hai (aur kya NAHI karta)

| | |
|---|---|
| ✅ **Karta hai** | Ek SAR image dekh ke batata hai **is waqt paani KAHAN hai**, aur kitne sq km |
| ❌ **Nahi karta** | **Kal kahan paani hoga** — ye forecast nahi hai. Wo B2 (LSTM) ka kaam hai |
| ❌ **Nahi karta** | Hydrodynamic simulation — hum koi water-flow model nahi chala rahe |

Ye **segmentation** hai: ek image, ek mask. Isse zyada claim mat karna.

---

## Results — REAL numbers

Ye sab `train_sar_unet.ipynb` ke ek asli run se hain. Raw values `outputs/metrics.json` mein.

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

### Iska matlab seedhe shabdon mein

- **Jitna paani asli mein tha, uska ~77% model ne dhoonda** (recall 0.771).
  Yaani **har 4 mein se ~1 paani wala pixel chhoot jaata hai.**
- **Jo paani model ne dikhaya, uska ~80% sach mein paani tha** (precision 0.804).
  Baaki ~20% false alarm hai.
- **Pixel accuracy 0.948 ko mat dekho — wo dhoka hai.** Test set mein paani sirf ~12%
  pixels hai. "Sab kuch not-water" bol dene wala bekaar model bhi ~88% accuracy le aata.
  **IoU aur F1 hi asli metric hain.**

### Region-wise (yahi sabse kaam ki table hai)

Overall IoU ek average hai aur average kamzori chhupa deta hai. Hum ye **Assam** ke liye
bana rahe hain, isliye India ka number alag se dekhna zaroori hai:

<!-- REGIONS:START -->
| Region | chips | water % | IoU | F1 | Precision | Recall |
|---|---|---|---|---|---|---|
| Nigeria | 4 | 16.2 | 0.8864 | 0.9398 | 0.9254 | 0.9547 |
| Mekong | 6 | 14.5 | 0.8507 | 0.9193 | 0.9093 | 0.9295 |
| Sri-Lanka | 9 | 18.5 | 0.7828 | 0.8782 | 0.8909 | 0.8658 |
| Spain | 6 | 23.8 | 0.7226 | 0.8389 | 0.8016 | 0.8799 |
| **India** | 14 | 21.3 | 0.7044 | 0.8265 | 0.8471 | 0.8069 |
| USA | 14 | 3.1 | 0.5710 | 0.7269 | 0.6747 | 0.7880 |
| Ghana | 11 | 8.0 | 0.5474 | 0.7075 | 0.8819 | 0.5907 |
| Paraguay | 14 | 7.3 | 0.5074 | 0.6732 | 0.6776 | 0.6688 |
| Somalia | 6 | 6.5 | 0.4877 | 0.6557 | 0.7937 | 0.5585 |
| **Pakistan** | 6 | 17.9 | 0.2432 | 0.3913 | 0.4379 | 0.3536 |
<!-- REGIONS:END -->

Do cheezein saaf dikh rahi hain:

1. **India pe IoU 0.704 — overall se behtar, aur ye chips ASSAM ke hi hain.**
   Test ke 14 India chips **lat 25.73-27.21, lon 92.75-93.80** pe hain — yani seedha
   **Brahmaputra valley** (Sonitpur, Nagaon, Golaghat, Karbi Anglong ka ilaaka).
   Hamare seeded gaon isi belt mein hain. Yaani ye "kisi aur jagah ka India" nahi,
   theek wahi terrain hai jahan JalRakshak deploy hoga.
   *(Ye baat pehle README mein galat likhi thi — "zaroori nahi ki Assam ke hon".
   Chips ke bounds check karne pe pata chala ki wo Assam ke hi hain.)*
2. **Pakistan pe model lagbhag fail hai (IoU 0.243).** Chhupa nahi rahe. Wahan ke chips
   mein irrigated farmland aur wet soil hai jinki radar signature paani jaisi hai — model
   confuse ho jaata hai. Aisa hi terrain Assam mein bhi mil sakta hai (dhaan ke khet).

Aam taur pe: jahan paani bada aur ek jagah hai (Nigeria, Mekong) wahan achha; jahan paani
patla aur bikhra hai (Paraguay, Ghana, USA) wahan kamzor.

### Threshold

Default **0.5** hai (best IoU bhi wahi deta hai). Par flood warning mein **recall zyada
matter karta hai** — paani chhoot jaana, false alarm se zyada khatarnak hai.
Threshold **0.3** pe recall 0.771 → **0.816** jaata hai aur IoU sirf 0.649 → 0.645 girta hai.
Operational deployment mein 0.3 behtar trade-off hai.

### Figures (`outputs/`)

| file | kya |
|---|---|
| `training_curves.png` | loss, val IoU/F1, LR schedule |
| `confusion_matrix.png` | pixel counts + normalised |
| `threshold_sweep.png` | IoU/F1/precision/recall vs threshold |
| `sample_predictions_by_region.png` | **deck ke liye yehi use karo** — India, Sri-Lanka, Ghana, Pakistan |
| `sample_predictions.png` | notebook ka apna figure (sirf Ghana chips) |
| `metrics.json`, `per_region_test.json` | raw numbers |

---

## Dataset

**Sen1Floods11** — Cloud to Street / Google, **CC-BY 4.0**.
Bonafilia et al., *Sen1Floods11: a georeferenced dataset to train and test deep learning
flood algorithms for Sentinel-1*, CVPR Workshops 2020.

Hum **sirf hand-labeled subset** use kar rahe hain — 431 chips (official split):

| split | chips |
|---|---|
| train | 252 |
| valid | 89 |
| test | 90 |

**Kyun poora 4,831 nahi:** baaki chips ke labels **algorithm se** bane hain (Otsu
thresholding / JRC permanent water). Unpe train karke jo score aata wo batata ki "humne
ek algorithm ki nakal kar li", ye nahi ki "humne paani pehchanna seekha". Hand-labeled
chips mein insaan ne khud paani mark kiya hai. Kam data, par sach.

**Data ki shakal:**
- `S1Hand`: 2-band GeoTIFF (Sentinel-1 **VV + VH**), float32, **decibel**, 512x512, ~10 m/px
- `LabelHand`: int16 — `1` paani, `0` paani nahi, **`-1` no data**

**Data quality jo humne khud check ki:**
- **5 chips (431 mein se) poore ke poore no-data hain** — train 1, valid 3, test 1
  (sab Ghana). Ye loss/metrics mein kuch contribute nahi karte (masking sahi hai), par
  training steps zaya karte hain.
- 47 chips mein paani bilkul nahi hai (pure negative) — ye valid aur kaam ke hain.
- Baaki chips mein median ~99% pixels valid hain.

### Data laane ke liye

```bash
python download_data.py       # ~708 MB, 3-4 min
```
gsutil/gcloud ki zaroorat nahi — bucket HTTPS pe publicly readable hai.

---

## Model

- **U-Net**, **ResNet34** encoder (ImageNet pretrained), 24.4M params
- Input 2 channels (VV, VH) — `smp` pretrained 3-channel conv ko 2-channel mein adapt kar deta hai
- Output 1 channel logit → sigmoid → threshold
- **Loss: masked BCE + masked Dice**
- Optimizer AdamW (lr 1e-3, wd 1e-4), cosine schedule, 40 epochs, batch 8

### Do cheezein jo is training ko imaandaar banati hain

**1. `-1` (no-data) pixels loss aur metrics DONO se hataye gaye hain.**
Train set ke **13.3%** pixels no-data hain. Inhe "not water" maan lena sabse aam galti hai —
usse IoU/accuracy jhoothi badh jaati hai. Har jagah `valid = (label >= 0)` mask lagta hai.

**2. Augmentation sirf geometric hai** (flips, 90° rotations). Brightness/contrast jitter
jaan-bujh ke nahi — dB value ka **physical matlab** hota hai (radar backscatter), usse
badalna data ko jhootha kar deta hai.

---

## Chalane ka tareeka

### Local (MacBook M-series) — **yahi use kiya gaya**

M4 pe PyTorch **MPS** (Metal) se Apple ka integrated GPU use karta hai. Colab ki zaroorat
nahi padi — **poori training 28 minute mein ho gayi** (40 epochs, 252 chips).

```bash
cd ml
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python download_data.py
jupyter notebook train_sar_unet.ipynb      # ya: jupyter nbconvert --to notebook --execute --inplace train_sar_unet.ipynb
```

### Google Colab — free GPU (tab chahiye jab **poore 4,831 chips** pe scale karo)

446 chips ke liye local kaafi hai. Par full dataset (~14 GB, 10x data) pe local training
ghanton lagegi — tab Colab.

1. https://colab.research.google.com → **Upload notebook** → `train_sar_unet.ipynb`
2. **Runtime → Change runtime type → T4 GPU** → Save
3. Sabse upar ek naya cell banao aur ye chipkao:
   ```python
   !pip -q install segmentation-models-pytorch rasterio
   !git clone --depth 1 https://github.com/shubhamverma-devx/jalrakshak.git /content/jr
   %cd /content/jr/ml
   !python download_data.py
   ```
4. **Runtime → Run all**. Notebook `torch.cuda.is_available()` dekh ke apne aap CUDA le lega —
   code mein kuch badalna nahi hai.
5. Khatam hone pe left panel se ye download kar lo:
   `models/sar_unet.pth`, `outputs/metrics.json`, `outputs/*.png`
6. Unhe local `ml/models/` aur `ml/outputs/` mein rakh do.

> Colab free session ~12 ghante mein kat jaata hai aur idle pe pehle. Full-dataset run
> lamba hai — checkpoint har epoch save hota hai, to disconnect pe sab nahi jaata.

---

## Inference

```bash
python predict.py data/test/S1/India_1018327_S1Hand.tif
python predict.py chip.tif --out-mask mask.tif --out-geojson water.geojson
python predict.py chip.tif --json          # dashboard/API ke liye
```

Output:
```
  file            : India_1018327_S1Hand.tif
  flooded area    : 2.2526 sq km
  water coverage  : 9.61% of chip
  water pixels    : 25,194 / 262,144
  confidence      : 0.841 (mean over water px)
  threshold       : 0.5   device: mps
```
(ye asli output hai — upar wali command chalane se yehi aayega)

**Area ka hisaab:** Sen1Floods11 chips **EPSG:4326 (degrees)** mein hain, meters mein nahi.
`predict.py` image ke centre latitude pe degree→meter conversion karta hai
(1° lat ≈ 111,320 m; longitude `cos(lat)` se sikudta hai). Projected CRS (UTM) aaye to
resolution seedha meters mein le leta hai. **"10m x 10m" hardcode karna galat area deta hai.**

---

## Dashboard integration (ho gaya)

Officer dashboard mein **Satellite** tab hai jo isi model ko chalata hai:

```
React (Satellite tab)  ->  POST /api/sar/detect  ->  SarDetectionService (PHP)
                                                  ->  subprocess: ml/predict.py
                                                  ->  GeoJSON polygons + sq km
                                                  ->  Leaflet map pe overlay
```

- 4 sample chips `ml/samples/` mein bundled hain (**test split se — model ne inhe train
  mein kabhi nahi dekha**), taaki demo bina 708 MB dataset ke chale
- Threshold **0.3** use hota hai (recall ke liye, upar wali wajah)
- UI mein hamesha dikhta hai: dataset, chips count, IoU @ 0.3, aur
  **"Detection, not forecast"** — provenance chhupa hua nahi hai
- Backend detection cache karta hai (deterministic hai): pehli baar ~4 s, phir ~25 ms
- Nazdeeki gaon **doori ke saath** dikhte hain, aur saaf likha hai ki ye
  "affected villages" ka daava nahi hai — chip 5x5 km ka hai, gaon 7-33 km door

## Honest limitations

Ye padhe bina model ko kahin plug mat karna.

1. **~23% paani chhoot jaata hai** (recall 0.771 @ thr 0.5). Flood mapping mein ye kam nahi hai.
   Threshold 0.3 pe ~18% pe aa jaata hai, par false alarm badhta hai.
2. **Sirf 252 chips pe train hua.** Ye bahut kam hai. Full 4,831 (weak labels ke saath
   pre-train + hand-labeled pe fine-tune) se sudhar milna chahiye — abhi kiya nahi.
3. **Assam pe test kiya — par sirf 14 chips pe.** Test ke India chips Brahmaputra valley
   ke hi hain (bounds upar diye hain), aur wahan IoU 0.704 aaya. Ye achha signal hai,
   par 14 chips ~350 sq km cover karte hain — poora Assam 78,000 sq km hai.
   **Aur ye ek-do flood events ke chips hain, har season ka nahi.** Bade deployment se pehle
   zyada Assam scenes chahiye, khaas kar alag mausam aur alag fasal cycle ke waqt ke.

   > ⚠️ **Acquisition dates verify NAHI kiye.** Chips ke GeoTIFF tags mein sirf band naam
   > (VV/VH) hain, koi date nahi. Kis saal ka flood event hai — wo Sen1Floods11 paper/
   > metadata se confirm karna baaki hai. Deck ya judge ke saamne koi saal mat bolna
   > jab tak check na kar lo.
4. **Pakistan-jaisa terrain fail karta hai** (IoU 0.243) — irrigated farmland aur geeli mitti
   ki radar signature paani jaisi hoti hai. Assam ke dhaan ke khet bhi aisa hi behave kar sakte hain.
5. **Patla/bikhra paani kamzori hai.** Chhoti nadiyan, gaon ke beech ka bhara paani — jahan
   paani kam % mein hai wahan IoU girta hai (Paraguay 0.507, Ghana 0.547).
6. **10 m resolution ki seema** — 100 sq m se chhoti cheezein dikhengi hi nahi. Ek ghar ke
   aangan ka paani ye model kabhi nahi pakdega.
7. **SAR ki apni seema:** smooth surface (geeli sadak, dhaatu ki chhat) paani jaisa dikh sakta
   hai; aur ghane ped ke neeche ka paani radar ko dikhta hi nahi.
8. **Published baselines se seedhi tulna nahi ki.** Sen1Floods11 pe doosre logon ke numbers
   maujood hain, par unka exact protocol (splits, preprocessing, epochs) hum reproduce nahi
   kiye. Isliye "hum X se better hain" **mat bolna** — apna number bolo, bas.
9. **Ye ek image ka snapshot hai.** Time series nahi, forecast nahi.

---

## Files

```
ml/
├── train_sar_unet.ipynb              # training (executed, outputs ke saath) — YEHI SABOOT HAI
├── predict.py                        # inference: mask + sq km + geojson
├── download_data.py                  # Sen1Floods11 hand-labeled subset
├── requirements.txt
├── README.md                         # ye file
├── model_card.md
├── models/sar_unet.pth               # 98 MB — gitignored (Release/Drive se share karo)
├── outputs/                          # metrics + figures (tracked — yehi proof hai)
└── scripts/
    ├── build_notebook.py             # notebook generator (regenerable rakha)
    ├── per_region_eval.py            # region-wise breakdown
    ├── qualitative_figure.py         # deck wala figure
    └── render_docs.py                # metrics.json se docs bharta hai
```

`data/` aur `models/*.pth` gitignored hain (708 MB + 98 MB). `outputs/` jaan-bujh ke
tracked hai — training curves, confusion matrix aur metrics.json hi wo saboot hain ki
model humne khud train kiya.

## Licence / citation

Dataset **CC-BY 4.0** — Bonafilia, D., Tellman, B., Anderson, T., Issenberg, E.
*Sen1Floods11: A georeferenced dataset to train and test deep learning flood algorithms
for Sentinel-1.* CVPR Workshops, 2020.
