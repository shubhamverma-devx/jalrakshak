# B2 — Village-level Flood Forecast (+24h / +48h)

**JalRakshak** · SIH26071 (MoES/IMD) · Team Blackbox

Har gaon ka apna risk level, 24 aur 48 ghante aage. Ye **forecast** hai — A2 RiskEngine
(jo abhi ka haal batata hai) se alag cheez.

---

## Sabse pehle: seedha natija

> **NWP features se koi asli sudhaar NAHI hua.**
>
> Sochne wali baat ye thi ki model sirf pichhli barish dekhta hai, isliye persistence
> se aage nahi nikal pa raha. To atmospheric variables (dabaav, nami, dew point, taapman,
> hawa, baadal) daale gaye. Natija:
>
> | | +24h | +48h |
> |---|---|---|
> | rainfall-only | 0.6259 | 0.5028 |
> | rainfall + NWP | 0.6091 | 0.4827 |
> | **NWP se farq** | **-0.0168** | **-0.0201** |
>
> Validation par NWP ne **thoda achha** kiya (macro-F1 0.5602/0.4688 vs 0.5509/0.4415),
> par held-out test par **thoda bura**. Dono taraf ka farq itna chhota hai ki use shor
> (noise) kehna zyada theek hai — khaas kar jab test mein sirf **45 RED din** hain.
>
> **Persistence ke against:** ship hua model +24h par usse **+0.0087** aage hai
> (0.6091 vs 0.6004) — itna kam ki jeet kehna theek nahi. **+48h par wo peeche hai**
> (0.4827 vs 0.5113).
>
> RED recall: **+24h par 11/45**, **+48h par 4/45**. Zyadatar RED din abhi bhi chhoot
> jaate hain.

**To seedha jawaab: NWP ne persistence ko nahi haraya.** Wajah neeche
"NWP se fayda kyun nahi hua" mein hai — aur wo wajah reanalysis ki nature mein hai,
feature engineering ki kami mein nahi.

### Model kaunsa ship hua, aur kyun

Ship **rainfall+NWP** wala hua, jabki test par rainfall-only behtar dikha.

Ye jaan-bujh ke hai: **model selection VALIDATION par hoti hai, test par nahi.**
Validation ne rainfall+NWP chuna (0.5602/0.4688 vs 0.5509/0.4415). Agar hum test dekh kar
rainfall-only ship karte, to test "held-out" reh hi nahi jaata aur uska number bhi
bharose ke laayak na hota. Isliye validation ka faisla maana gaya aur test ko chhua nahi
gaya — chaahe wo hamare haq mein na gaya ho.

## Ye kya karta hai (aur kya NAHI karta)

| | |
|---|---|
| ✅ **Karta hai** | Aage 2 din ki barish ka anumaan, phir uspe **wahi RiskEngine** jo dashboard chalata hai |
| ❌ **Nahi karta** | Asli flood ki bhavishyavani — target "flood hua" nahi, target "hamara rule kya bolta" hai |
| ❌ **Nahi karta** | Hydrodynamic / flow simulation |
| ❌ **Nahi karta** | IMD ya CWC se behtar hone ka koi daava — unke paas radar, gauge network aur NWP hai, hamare paas ek reanalysis rainfall series |

---

## Target kya hai — ye samajhna zaroori hai

Gaon-level flood ka labelled historical record hamare paas **hai hi nahi**. To label ye hai:

> *"us aane wale din hamara apna RiskEngine kya bolta"*

Matlab model hamari risk-definition ko **aage ke waqt mein le jaana** seekh raha hai.
Ye jaan-bujh ke chuna gaya: isse forecast aur dashboard ek hi bhasha bolte hain.
Par iska matlab bhi saaf hona chahiye — **ye "sach" ke against nahi napa gaya, hamare apne
rule ke against napa gaya hai.**

---

## Data

| | |
|---|---|
| **Rainfall** | **Open-Meteo** historical archive (**ERA5** reanalysis), free, no key |
| **Atmospheric (NWP)** | **NASA POWER** (**MERRA-2** reanalysis), free, no key |
| Gaon | 30 (wahi jo poore system mein hain) |
| Range | **2000-01-01 → 2025-12-31**, roz ka data |
| Samples | 283,980 gaon-din |

### "NWP" ka matlab yahan kya hai — aur kya NAHI

Dono source **reanalysis** hain: ek numerical weather prediction model purane observations
(satellite, station, radiosonde) ke saath dobara chalaya gaya, taaki ateet ke mausam ki
sabse achhi tasveer bane.

To ye **NWP model ka output** hai — par **operational forecast NAHI**. Farq bada hai:

| | |
|---|---|
| **Reanalysis** (jo hamare paas hai) | "us din mausam kaisa **THA**" — observations ke saath fit kiya hua |
| **Operational forecast** (asli deployment mein) | "kal kaisa **HOGA**" — bina kal ke observations ke, hamesha kam sateek |

Isliye is README mein kahin bhi "NWP forecast" nahi likha — sirf **"NWP-based reanalysis"**.
PS ka "numerical weather prediction model data" wala hissa is data se cover hota hai, par
usko operational forecast bol dena jhooth hoga.

### Atmospheric variables (8 raw → 11 features)

`PS` (surface pressure) · `RH2M` (relative humidity) · `T2MDEW` (dew point) ·
`T2M_MAX` / `T2M_MIN` · `WS10M` (wind speed) · `WD10M` (wind direction) · `CLOUD_AMT`

Inse bane features: pressure ka **1-din aur 3-din tendency**, humidity, dew-point
depression, mean temperature, temperature range, wind speed, wind direction ka **sin/cos**,
cloud cover, cloud tendency.

Pressure ka **absolute level jaan-bujh ke nahi** liya — sirf tendency. Do wajah: surface
pressure gaon ki elevation pe nirbhar karta hai (to level model ko "kaunsa gaon" sikha
deta, mausam nahi), aur uske liye per-village normalization constant chahiye hota jo
training aur inference ke beech ek aur bigadne wali cheez hoti. Barish ke liye **girta
dabaav** hi ishaara hai, uska level nahi.

### Source Open-Meteo se NASA POWER kyun badla

Pehle atmospheric variables bhi Open-Meteo archive se hi liye ja rahe the (ERA5). Uska
free tier request ko **data volume** (variables x days) se tolta hai, aur 30 gaon x
8 variables x 26 saal us quota se kai guna zyada hai. Maapa gaya: **ghante mein sirf 2-3
gaon**, aur quota har ghante reset hota hai — poore data ke liye **6-7 ghante**.
NASA POWER wahi cheez **2 minute** mein deta hai, bina API key ke. Isliye source badla.

### LEAKAGE ka niyam

Har feature sirf din **`t` tak** ka hai — kabhi `t+1` ya `t+2` ka nahi. Reanalysis mein
`t+1` ka matlab hai "kal ka **ASLI** mausam"; usse feature banana model ko jawaab dikha
dena hota — score shaandaar aata aur deployment mein bilkul bekaar hota.
`build_sequences()` ka slice `t` par khatam hota hai; yahi is niyam ko lagoo karta hai.

### Ek train/serve mismatch jo jaan-bujh ke liya gaya hai

Training ka atmospheric data **NASA POWER (MERRA-2)** se hai, par inference par
**Open-Meteo forecast API** se aata hai (NASA POWER ka forecast endpoint hai hi nahi).
Units barabar kar di gayi hain (`PS` kPa x10 → hPa, `wind_speed_unit=ms`), par ye do alag
system hain aur values bilkul same nahi hongi. **Iska matlab: asli deployment mein accuracy
test-set wali accuracy se thodi KAM hogi.** Ye chhupaya nahi ja raha.

Split **waqt se** kata hai, random se nahi — lagatar din ek doosre se milte-julte hain,
to random split test ka data train mein ghusa deta aur score jhootha achha aata:

| split | range | samples |
|---|---|---|
| train | 2000-01-30 → 2017-12-31 | 196,380 |
| val | 2018-01-01 → 2021-12-31 | 43,830 |
| test | 2022-01-01 → 2025-12-29 | 43,770 |

## Class balance — is problem ki sabse badi sachai

26 saal ke 284,850 gaon-din mein:

| level | din | % |
|---|---|---|
| green | 280,870 | **98.603%** |
| yellow | 3,776 | 1.326% |
| red | **204** | **0.072%** |

Test set mein sirf **45 RED** aur **730 YELLOW** samples hain (43,770 mein se).

**Isliye accuracy is problem mein bekaar metric hai.** "Hamesha green bolo" se test pe
**0.9823 accuracy** aa jaati hai. Neeche har jagah macro-F1 aur per-class recall hai;
accuracy sirf reference ke liye likhi hai.

Aur ye bhi jaan lo: **15 gaon 26 saal mein kabhi RED nahi hote** aur **Charaideo hamesha
green rehta hai** — uska koi river threshold nahi hai (D10) aur elevation 110 m hai, to
rule ke hisaab se wo kabhi kuch aur ho hi nahi sakta.

---

## Natije — asli numbers

Test set = 2022-2025, jo training aur quantile-chunav dono se bahar hai.

| Tareeka | +24h macro-F1 | +48h macro-F1 |
|---|---|---|
| Majority — hamesha `green` | 0.3304 | 0.3304 |
| Persistence — "aaj wala hi level" | 0.6004 | 0.5113 |
| Persistence — "aaj jitni barish" → rule | 0.5952 | 0.5106 |
| Seedha LSTM classifier (sqrt weights) | 0.4832 | 0.4335 |
| Seedha LSTM classifier (full weights) | 0.4637 | 0.3737 |
| LSTM **rainfall-only** → RiskEngine | 0.6259 | 0.5028 |
| **LSTM rainfall+NWP → RiskEngine (ship hua)** | 0.6091 | 0.4827 |

### Ship hue model ka per-class detail

**+24h** — accuracy 0.9818

| class | precision | recall | F1 | n |
|---|---|---|---|---|
| green | 0.991 | 0.991 | 0.991 | 42,995 |
| yellow | 0.458 | 0.468 | 0.463 | 730 |
| **red** | **0.786** | **0.244** | 0.373 | 45 |

Figures: `outputs/forecast_confusion.png` · `outputs/forecast_baselines.png` ·
`outputs/forecast_training_curves.png` · `outputs/forecast_quantile_selection.png`
Raw numbers: `outputs/forecast_metrics.json`

> 45 samples pe nikli precision/recall **shor bhari** hai — ek-do din idhar-udhar hone se
> 0.05 ka farq aa jaata hai. Isliye har table mein `n` likha hai.

---

## NWP se fayda kyun nahi hua

Ummeed ye thi: model sirf pichhli barish dekh raha hai, isliye kal ki barish ka anumaan
nahi laga pa raha. Atmospheric state (girta dabaav, badhti nami, baadal) de do to wo
"system aa raha hai" pehchan lega.

Features mein **signal hai** — ye check kiya gaya. Barish wale dinon aur sookhe dinon par
saaf farq dikhta hai: cloud cover 0.93 vs 0.33, dew-point depression 0.40 vs 0.69,
temperature range 0.65 vs 1.02. Yaani variables bekaar nahi hain.

Dikkat ye hai ki **ye signal USI din ka hai jis din barish hoti hai, uske EK DIN PEHLE ka
nahi**. Reanalysis batata hai ki aaj ka mausam kaisa tha — aur aaj ka mausam aaj ki barish
ke saath bahut juda hua hai, par KAL ki barish ke saath utna nahi. Kal ki barish jaanne ke
liye chahiye ki model aaj ki hawa ko **aage chalaye** — aur wo kaam ek NWP model karta hai,
jismein poora atmosphere ka physics hota hai. 30 gaon ke daily averages se ek LSTM wo
physics nahi seekh sakta.

**Jo cheez asal mein chahiye wo reanalysis nahi, operational FORECAST hai** — yaani wo
data jo already keh raha ho "kal 80 mm barish hogi". Open-Meteo ka forecast API wahi deta
hai, par sirf **~3 mahine peeche tak** — 26 saal ka training data usse nahi banta.

## Ye itna kamzor kyun hai (asli wajah)

River level **asli gauge se nahi aata** — wo 3-din ke cumulative rainfall se DERIVE hota
hai (decision D9). Rule ki algebra kholo to poori risk definition do numbers pe simat jaati hai:

```
cum3 >= 192 mm  ->  river warning mark par
cum3 >= 352 mm  ->  river danger mark paar
```

Yaani:

```
label = f(barish)        <- deterministic, isme seekhne ko kuch NAHI hai
```

To B2 ka asli kaam sirf ek hai: **aage ki barish ka anumaan lagao**. Aur roz ki barish
apne hi ateet se lagbhag anumaan-yogya nahi hai — asli rainfall forecast ke liye NWP model
chahiye (dabaav, hawa, nami, samundra ka taapmaan), sirf pichhli barish ki series nahi.

Ek aur cheez isi se nikalti hai — **+24h aasan aur +48h mushkil kyun hai:**

```
cum3(t+1) = rain[t-1] + rain[t] + rain[t+1]    <- 3 mein se 2 PEHLE SE PATA
cum3(t+2) = rain[t]   + rain[t+1] + rain[t+2]  <- 3 mein se sirf 1 pata
```

Test ke 45 RED dinon mein se +24h par sirf **3** aise the jo pehle se pakke the
(known part hi >= 352). Baaki sabke liye aage ki barish ka sahi anumaan zaroori tha.

---

## Model

```
rainfall sequence (30 din x 5 features)
        |
     LSTM (2 layers x 64 hidden)          57,100 parameters
        |
  + static features (11)
        |
   quantile head  ->  aage 2 din ki barish, 6 quantiles par
        |
   ASLI RiskEngine (risk_rules.py)  ->  green / yellow / red
```

**Seedha classifier kyun nahi:** pehle wahi try kiya tha — sequence se seedha 3 class.
Bina class weight ke model hamesha `green` bolta tha (98.6% accuracy, RED recall **0.000**).
Poore inverse-frequency weight ke saath RED recall to 0.867 pahunch gayi par precision
**0.066** rah gayi — 42,995 green dinon mein 403 jhoothe RED. Flood system mein false alarm
apne aap mein khatra hai: do-teen jhoothe alert ke baad log agla ASLI alert bhi ignore
karte hain. Dono setting baseline se buri thi, isliye ye raasta chhoda.

**Quantile kyun, mean kyun nahi:** mean wala model extremes daba deta hai (predicted p99 =
15 mm jabki asli p99 = 83 mm). Flood mein extreme din hi sab kuch hai. Model 6 quantiles
seekhta hai; kaunsa use karna hai wo **validation set** pe chuna gaya (`q=0.95` dono
horizon ke liye) — **test pe nahi**.

**Confidence kahan se aati hai:** banayi nahi gayi hai. Har quantile ko RiskEngine se chala
ke dekha jaata hai ki level kya banta hai; jitne quantiles ek hi level pe rukein, utna
bharosa. `confidence 0.33` ka matlab hai 6 mein se sirf 2 scenario us level pe pahunche —
yaani model khud confused hai, aur UI wahi dikhata hai.

---

## Labels PHP se verify hue hain

Saare labels `risk_rules.py` se bante hain, jo `backend/app/Services/RiskEngine.php` ka
Python port hai. Port ko asli PHP engine ke against **4,320 combinations** pe check kiya
gaya hai:

```bash
python verify_against_php.py      # PHP + backend/vendor chahiye
```

Ye verification optional nahi thi — usne **do asli bug** pakde:

1. **PHP ka `round()` aadhe ko zero se DOOR le jaata hai, Python ka nazdeeki EVEN pe.**
   `35.925` → PHP `35.93`, Python `35.92`. River level warning/danger se compare hota hai,
   to ek paisa bhar ka farq poora label palat deta tha.
2. **PHP mein low elevation `<= 80` hai, `< 80` nahi.**

Dono se 4,320 mein se 253 label galat ban rahe the. Ab **dono bilkul match karte hain**.

---

## Chalane ka tareeka

```bash
cd ml
source .venv/bin/activate
pip install -r requirements.txt

python fetch_forecast_data.py                  # rainfall, ~285k rows (Open-Meteo rate-limits)
python fetch_nwp_data.py                       # atmospheric, 30 gaon ~2 min (NASA POWER)
python verify_against_php.py                   # labels PHP se match karte hain?
jupyter notebook train_forecast_lstm.ipynb     # ~5 min (MacBook M4 / MPS)
```

Inference:

```bash
python forecast.py --list          # village ids
python forecast.py 10              # Barpeta
python forecast.py 10 --json       # Laravel/API ke liye
```

```
  Barpeta (Barpeta)   as of 2026-08-22
  abhi : GREEN   rain 4.1 mm  ·  3-day 32.4 mm
  +24h : GREEN   rain  44.3 mm (q0.95)  3-day   66.1 mm  ·  confidence 0.83
  +48h : GREEN   rain  57.2 mm (q0.95)  3-day  105.6 mm  ·  confidence 0.67
```

(ye asli output hai — inference ke waqt Open-Meteo se pichhle 35 din ki barish aati hai)

---

## Honest limitations

1. **Trivial baseline se behtar nahi.** +24h par persistence se +0.0087 aage (itna kam ki
   jeet nahi kehte), +48h par -0.0287 peeche. Ye component abhi **research spike hai,
   production forecast nahi**.
2. **NWP features ne madad nahi ki** — test par -0.017 / -0.020. Wajah upar likhi hai:
   reanalysis "aaj ka mausam" batata hai, "kal ka" nahi.
3. **RED recall kam hai** — +24h par 0.244 (11/45), +48h par 0.089 (4/45). Isi bharose
   gaon khaali karwane ka faisla nahi liya ja sakta.
3. **Target hamara apna rule hai**, asli flood observation nahi. "Ground truth" nahi hai.
4. **River level derived proxy hai** (D9), asli gauge feed nahi. Asli CWC gauge lag jaaye to
   label bhi badlega aur ye poora model dobara train karna padega.
5. **Reanalysis use hua, operational forecast nahi.** Ye ab test ho chuka hai aur isse
   fayda nahi hua — asli sudhaar tab hoga jab NWP ka **forecast** (analysis nahi) mile.
6. **Train/serve mismatch:** training NASA POWER (MERRA-2) par, inference Open-Meteo
   (operational) par. Deployment accuracy test wali se thodi kam hogi.
7. **Reanalysis grid mota hai** (MERRA-2 ~50 km, ERA5 ~11 km) — gaon ka apna raingauge
   nahi. Do paas ke gaon ek hi cell se data le sakte hain.
8. **Sirf 30 gaon, sirf Assam.** Kisi aur zile pe iske number kya honge, pata nahi.
9. **204 RED din 26 saal mein** — koi bhi model itni kam misalon se robust nahi ban sakta.

---

## Aage kya karna chahiye (agar ye behtar karna ho)

1. **Operational NWP FORECAST chahiye, reanalysis nahi.** Reanalysis try ho chuka —
   usse fayda nahi hua (upar wajah). Asli sudhaar tab hoga jab training data mein wo
   forecast ho jo *us waqt* jaari hua tha ("kal 80 mm hogi"). Open-Meteo ka forecast API
   ye deta hai par sirf ~3 mahine peeche tak, to iske liye **aaj se roz forecast archive
   karna shuru karna padega** — 1-2 saal baad us par train kiya ja sakta hai.
2. **Asli CWC/India-WRIS gauge data** lao — tab river level derived nahi rahega aur target
   mein asli hydrology aa jaayegi.
3. **Zyada gaon** — 30 se 300 karne se RED misalein 10x ho jaayengi.
4. **Alert-level metric pe train karo** (green vs not-green) — teen class ke bajaye do.
   Operationally wahi faisla hota hai, aur imbalance bhi kam ho jaata hai.

---

## Files

```
ml/
├── fetch_forecast_data.py       # Open-Meteo archive (ERA5) -> rainfall
├── fetch_nwp_data.py            # NASA POWER (MERRA-2) -> atmospheric variables
├── risk_rules.py                # RiskEngine ka Python port (PHP se verified)
├── verify_against_php.py        # 4,320 cases pe port check
├── forecast_data.py             # dataset + features (training aur inference dono yahi)
├── forecast_model.py            # RainfallQuantileLSTM + (fail hua) DirectClassifierLSTM
├── train_forecast_lstm.ipynb    # training (executed, outputs ke saath) — YEHI SABOOT HAI
├── forecast.py                  # inference: village id -> +24h / +48h
├── forecast_model_card.md
├── models/forecast_lstm.pt      # 0.7 MB — gitignored
├── outputs/forecast_metrics.json
└── outputs/forecast_*.png
```

---

## Licence / citation

Rainfall: **Open-Meteo** historical archive (ERA5 reanalysis), CC-BY 4.0 — https://open-meteo.com/
Atmospheric: **NASA POWER** (MERRA-2 reanalysis), public domain — https://power.larc.nasa.gov/
Dono free hain aur kisi API key ki zaroorat nahi.
