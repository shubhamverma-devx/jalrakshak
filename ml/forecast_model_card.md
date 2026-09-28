# Model Card — B2 Village Flood Forecast (+24h / +48h)

**Project:** JalRakshak · SIH26071 (MoES/IMD) · Team Blackbox
**Version:** 1.1 (NWP features added) · trained 2026-08-23
**Full detail:** `FORECAST_README.md`

---

## Ek line mein

30 Assam gaon ke 26 saal ke rainfall + atmospheric reanalysis par train kiya gaya chhota
LSTM, jo aage 2 din ki barish ka quantile anumaan lagata hai; us anumaan par project ka
apna rule-based RiskEngine chalta hai aur +24h / +48h ka risk level nikalta hai.

**Ye model trivial baseline se meaningfully behtar nahi hai, aur NWP features se koi
sudhaar nahi hua.** Numbers neeche.

---

## v1.0 se kya badla

v1.0 sirf rainfall dekhta tha. Sochne wali baat thi ki isi wajah se wo persistence se
aage nahi nikal pa raha, to v1.1 mein **NWP-family atmospheric variables** daale gaye
(dabaav ki tendency, nami, dew point, taapman, hawa, baadal).

**Natija: fayda nahi hua.**

| | +24h macro-F1 | +48h macro-F1 |
|---|---|---|
| rainfall-only | 0.6259 | 0.5028 |
| rainfall + NWP | 0.6091 | 0.4827 |
| **farq** | **-0.0168** | **-0.0201** |

Validation par NWP thoda **aage** tha (0.5602/0.4688 vs 0.5509/0.4415), test par thoda
**peeche**. Dono farq itne chhote hain ki unhe shor kehna zyada theek hai.

**Ship rainfall+NWP wala hua** — kyunki model selection **validation** par hoti hai,
test par nahi. Test dekh kar doosra model chunte to test "held-out" reh hi nahi jaata.

---

## Intended use

| | |
|---|---|
| **Kiske liye** | District flood officer — dashboard ke village drawer mein "next 48 hours" strip |
| **Kis liye** | Ek **ishaara** ki kisi gaon ka haal bigad sakta hai, taaki us par nazar rakhi jaaye |
| **Kis liye NAHI** | Evacuation ka faisla · official warning · IMD/CWC ka replacement |
| **Kis liye NAHI** | Assam ke bahar — sirf inhi 30 gaon par train aur test hua hai |

---

## Model

| | |
|---|---|
| Architecture | LSTM 2 layers x 64 hidden → quantile head (6 quantiles x 2 horizons) |
| Parameters | 59,916 |
| Input | 30 din x **16 features** (5 rainfall-derived + 11 atmospheric) + 11 static |
| Output | Aage 2 din ki barish (mm) 6 quantiles par → RiskEngine → green/yellow/red |
| Loss | Pinball (quantile) loss |
| Training | 20 epochs, Adam 1e-3, Apple M4 (MPS), ~3 min per variant |
| Chosen quantile | q=0.97 dono horizon (validation par chuna, test par nahi) |

---

## Data

| | |
|---|---|
| Rainfall | **Open-Meteo** archive (**ERA5** reanalysis), CC-BY 4.0, no key |
| Atmospheric | **NASA POWER** (**MERRA-2** reanalysis), public domain, no key |
| Coverage | 30 villages x 9,497 din (2000-01-01 → 2025-12-31), 283,980 samples |
| Split | **Waqt se** — train 2000-2017 · val 2018-2021 · test 2022-2025 |
| Labels | Project ka apna `RiskEngine` aane wale din par lagaya hua |

**Label balance (26 saal):** green 98.603% · yellow 1.326% · red **0.072%** (204 RED din)

**"NWP" yahan reanalysis hai, operational forecast NAHI.** Reanalysis batata hai "us din
mausam kaisa THA"; forecast batata hai "kal kaisa HOGA" aur hamesha kam sateek hota hai.

**Leakage rule:** har feature sirf din `t` tak ka hai, kabhi `t+1` / `t+2` ka nahi.

---

## Results — test set (2022-2025, 43,770 samples)

| Tareeka | +24h macro-F1 | +48h macro-F1 |
|---|---|---|
| Majority (hamesha green) | 0.3304 | 0.3304 |
| Persistence ("aaj wala level") | **0.6004** | **0.5113** |
| Persistence ("aaj jitni barish" → rule) | 0.5952 | 0.5106 |
| rainfall-only LSTM → rule | 0.6259 | 0.5028 |
| **Ye model (rainfall+NWP)** | 0.6091 | 0.4827 |

**+24h** — accuracy 0.9818

| class | precision | recall | F1 | n |
|---|---|---|---|---|
| green | 0.991 | 0.991 | 0.991 | 42,995 |
| yellow | 0.458 | 0.468 | 0.463 | 730 |
| red | 0.786 | **0.244** | 0.373 | 45 |

RED din pakde: **11 / 45**

**+48h** — accuracy 0.9624

| class | precision | recall | F1 | n |
|---|---|---|---|---|
| green | 0.991 | 0.971 | 0.981 | 42,995 |
| yellow | 0.227 | 0.518 | 0.316 | 730 |
| red | 0.500 | **0.089** | 0.151 | 45 |

RED din pakde: **4 / 45**

> **Accuracy ko headline mat banao.** "Hamesha green" bolne se 0.9823 accuracy aati hai.
> Matlab wali sankhya macro-F1 aur RED recall hai.

---

## Limitations

1. **Trivial baseline ke barabar.** +24h par persistence se +0.0087, +48h par -0.0287.
2. **NWP features ne madad nahi ki** (-0.017 / -0.020 test par). Reanalysis "aaj ka mausam"
   batata hai, "kal ka" nahi — uske liye operational forecast chahiye.
3. **RED recall kam** — 0.244 / 0.089. Zyadatar RED din chhoot jaate hain.
4. **Target asli flood nahi hai** — target project ka apna rule hai, aage ke din par lagaya hua.
5. **River level derived proxy hai** (decision D9), asli CWC gauge feed nahi.
6. **Train/serve mismatch** — training NASA POWER (MERRA-2) par, inference Open-Meteo
   (operational) par. Deployment accuracy test wali se thodi kam hogi.
7. **45 RED test samples** — in par nikle metrics shor bhare hain.
8. **15 gaon kabhi RED nahi hote** aur Charaideo hamesha green rehta hai (rule ki wajah se).

---

## Ethical / safety notes

- UI mein hamesha **"forecast"** likha jaata hai, aur uske saath model ki apni confidence
  bhi dikhti hai (quantile agreement se, banayi hui nahi).
- Dashboard mein accuracy ki line **hamesha dikhti hai** — macro-F1 vs baseline aur
  red-days-caught %. Numbers `forecast_metrics.json` se aate hain, hardcode nahi.
- Is model ke bharose **koi automatic alert nahi jaata** — alert hamesha officer bhejta hai.
- Deck/demo mein ise "AI flood prediction" **nahi** bola jaayega. Jo hai wahi bola jaayega:
  village-level forecast ka poora pipeline khada hai, asli data par train hua hai, NWP-family
  features bhi try kiye — aur abhi bhi ye baseline ke barabar hai, aur hum jaante hain kyun.

---

## Reproduce

```bash
cd ml
python fetch_forecast_data.py                  # rainfall (Open-Meteo / ERA5)
python fetch_nwp_data.py                       # atmospheric (NASA POWER / MERRA-2)
python verify_against_php.py                   # labels PHP RiskEngine se match karte hain
jupyter nbconvert --to notebook --execute --inplace train_forecast_lstm.ipynb
```

Seed 42. Notebook apne saare outputs ke saath repo mein hai — dono variants (rainfall-only
aur rainfall+NWP) ek hi run mein train hote hain, isliye comparison apples-to-apples hai.
