"""
forecast_data.py — B2 ka dataset. Notebook aur forecast.py DONO yahi use karte hain.

KYUN ek shared module: agar training apne tareeke se features banaye aur inference apne,
to model ko wo cheez milegi jispe wo train hi nahi hua — aur ye bug chup-chaap galat
forecast deta rehta hai, kabhi crash nahi karta. Feature banane ki jagah EK honi chahiye.

SHAPE:
  rain[V, T]     har gaon ka roz ka rainfall (mm)
  labels[V, T]   ussi din ka RiskEngine level (0=green 1=yellow 2=red)
  X_seq[N, L, F] pichhle L din ka sequence
  X_static[N, S] gaon ki na-badalne wali baatein
  y24[N], y48[N] +1 din aur +2 din ka level
"""

import csv
import json
import math
from pathlib import Path

import numpy as np

import risk_rules as rr

HERE = Path(__file__).resolve().parent
DATA = HERE / "data" / "forecast"

SEQ_LEN = 30            # ek mahina history — monsoon ka build-up isme aa jaata hai

# ---- Sequence features ----
# Pehle 5 the (sirf barish se bane). Ab NWP-family atmospheric variables bhi hain.
#
# ============ LEAKAGE KA NIYAM ============
# Ye SAARE features sirf din `t` TAK ke hote hain — kabhi t+1 ya t+2 ke nahi.
# ERA5 reanalysis mein t+1 ka matlab "kal ka ASLI mausam" hota hai; usko feature banana
# model ko jawaab dikha dena hai. Score shaandaar aata aur deployment mein bekaar hota.
# build_sequences() ka slice `t` par khatam hota hai — yahi is niyam ko lagoo karta hai.
N_RAIN_FEATURES = 5          # rain, cum3, river-fraction, doy sin/cos
N_NWP_FEATURES = 11          # neeche NWP_FEATURE_NAMES dekho
N_SEQ_FEATURES = N_RAIN_FEATURES + N_NWP_FEATURES
N_STATIC_FEATURES = 11

# NWP CSV ke columns (fetch_nwp_data.py ne jis order mein likhe)
# ---- Source columns ----
# TRAINING ka data NASA POWER (MERRA-2 reanalysis) se aata hai. INFERENCE ka Open-Meteo
# forecast endpoint se. Dono ke naam alag hain, isliye dono ko ek CANONICAL shape mein
# badla jaata hai aur features usi se bante hain (neeche nwp_features_from_canonical).
NWP_COLUMNS = ["PS", "RH2M", "T2MDEW", "T2M_MAX", "T2M_MIN", "WS10M", "WD10M", "CLOUD_AMT"]

# Canonical units — dono source inhi mein badalte hain:
#   pressure_hpa, rh_pct, dewpoint_c, tmax_c, tmin_c, wind_ms, wind_deg, cloud_pct
CANONICAL_KEYS = ["pressure_hpa", "rh_pct", "dewpoint_c", "tmax_c", "tmin_c",
                  "wind_ms", "wind_deg", "cloud_pct"]

# Model ko jaane wale derived features (scaling ~[-3,3] mein rakhi hai)
NWP_FEATURE_NAMES = [
    # Pressure ka ABSOLUTE level jaan-bujh ke nahi hai — sirf TENDENCY.
    # Do wajah: (a) surface pressure gaon ki elevation pe nirbhar hai, to absolute level
    # model ko "kaunsa gaon hai" sikha deta, mausam nahi; (b) uske liye per-village
    # normalization constant chahiye hota, jo training aur inference ke beech ek aur
    # cheez hai jo bigad sakti hai. Tendency dono jhanjhat se bachati hai — aur barish ke
    # liye girta dabaav hi asli ishaara hai, uska level nahi.
    "pressure_delta_1d",   # 1-din ka badlav (hPa)
    "pressure_delta_3d",   # 3-din ka badlav — dheere aa rahe system ke liye
    "humidity",            # relative humidity
    "dewpoint_depression", # T - Td: kitni hawa sookhi hai (chhota = sanpृkt = barish ke haalat)
    "temp_mean",
    "temp_range",          # max-min: bada range = saaf aasman, chhota = baadal
    "wind_speed",
    "wind_sin", "wind_cos",# direction circular hai — degrees seedha dena galat hoga
    "cloud_cover",
    "cloud_delta",         # baadal badh rahe hain ya chhat rahe hain
]
assert len(NWP_FEATURE_NAMES) == N_NWP_FEATURES

# Time-based split. RANDOM SPLIT YAHAN GHATAK HOTA:
# lagatar din ek doosre se milte-julte hain (aaj ka cum3 kal ke cum3 mein bhi hai), to
# random split se test ka data train mein ghus jaata aur score jhootha achha aata.
# Isliye waqt se kaata hai — model ko sirf ATEET dikhta hai, BHAVISHYA pe test hota hai.
TRAIN_END = "2017-12-31"
VAL_END = "2021-12-31"      # test = 2022-01-01 se aage (2022 ka asli Assam flood isme hai)


def load_raw():
    """
    CSV se rainfall + villages.json.
    OUTPUT: (villages list, dates list, rain float32[V, T])
    """
    villages = json.load(open(DATA / "villages.json"))
    by_id = {v["id"]: i for i, v in enumerate(villages)}

    rows = list(csv.DictReader(open(DATA / "rainfall_daily.csv")))
    dates = sorted({r["date"] for r in rows})
    date_idx = {d: i for i, d in enumerate(dates)}

    rain = np.zeros((len(villages), len(dates)), dtype=np.float32)
    for r in rows:
        rain[by_id[int(r["village_id"])], date_idx[r["date"]]] = float(r["rain_mm"])

    return villages, dates, rain


def fill_gaps(a):
    """NaN bharo — pehle aage se, phir peeche se. ERA5 mein gaps kam hain par hote hain."""
    if np.all(np.isnan(a)):
        a[:] = 0.0
        return a
    idx = np.where(~np.isnan(a))[0]
    a[:idx[0]] = a[idx[0]]
    for k in range(1, len(a)):
        if np.isnan(a[k]):
            a[k] = a[k - 1]
    return a


def power_to_canonical(raw):
    """NASA POWER (MERRA-2) columns -> canonical units. PS kPa hai, hPa chahiye."""
    return {
        "pressure_hpa": np.asarray(raw["PS"], dtype=np.float32) * 10.0,
        "rh_pct": np.asarray(raw["RH2M"], dtype=np.float32),
        "dewpoint_c": np.asarray(raw["T2MDEW"], dtype=np.float32),
        "tmax_c": np.asarray(raw["T2M_MAX"], dtype=np.float32),
        "tmin_c": np.asarray(raw["T2M_MIN"], dtype=np.float32),
        "wind_ms": np.asarray(raw["WS10M"], dtype=np.float32),
        "wind_deg": np.asarray(raw["WD10M"], dtype=np.float32),
        "cloud_pct": np.asarray(raw["CLOUD_AMT"], dtype=np.float32),
    }


def nwp_features_from_canonical(canon):
    """
    Canonical atmospheric variables -> model ke 11 derived features.

    INPUT : dict of CANONICAL_KEYS -> float array [T]  (units upar likhe hain)
    OUTPUT: float32[T, N_NWP_FEATURES]

    ============ YE FUNCTION SHARED KYUN HAI ============
    Training (NASA POWER) aur inference (Open-Meteo) DONO isi ko call karte hain, apne-apne
    data ko canonical units mein badal kar. Agar do jagah alag likha hota to ek jagah
    scaling badalne pe model ko wo cheez milti jispe wo train hi nahi hua — aur ye bug
    kabhi crash nahi karta, bas chup-chaap galat forecast deta rehta.

    KYUN derived, kachche nahi: pressure 1007 hPa aur humidity 85 seedha daalne se scaling
    bekaar ho jaati; aur wind direction degrees mein 359 aur 1 bahut door dikhte hain
    jabki wo paas hain — isliye sin/cos.
    """
    p = fill_gaps(np.asarray(canon["pressure_hpa"], dtype=np.float32).copy())
    rh = fill_gaps(np.asarray(canon["rh_pct"], dtype=np.float32).copy())
    td = fill_gaps(np.asarray(canon["dewpoint_c"], dtype=np.float32).copy())
    tmax = fill_gaps(np.asarray(canon["tmax_c"], dtype=np.float32).copy())
    tmin = fill_gaps(np.asarray(canon["tmin_c"], dtype=np.float32).copy())
    ws = fill_gaps(np.asarray(canon["wind_ms"], dtype=np.float32).copy())
    wd = fill_gaps(np.asarray(canon["wind_deg"], dtype=np.float32).copy())
    cloud = fill_gaps(np.asarray(canon["cloud_pct"], dtype=np.float32).copy())

    t_mean = (tmax + tmin) / 2.0
    wdir = np.deg2rad(wd)

    def delta(a, k):
        d_ = np.zeros_like(a)
        if len(a) > k:
            d_[k:] = a[k:] - a[:-k]
        return d_

    out = np.zeros((len(p), N_NWP_FEATURES), dtype=np.float32)
    out[:, 0] = delta(p, 1) / 5.0
    out[:, 1] = delta(p, 3) / 10.0
    out[:, 2] = rh / 100.0
    out[:, 3] = (t_mean - td) / 10.0
    out[:, 4] = (t_mean - 20.0) / 10.0
    out[:, 5] = (tmax - tmin) / 10.0
    out[:, 6] = ws / 10.0
    out[:, 7] = np.sin(wdir)
    out[:, 8] = np.cos(wdir)
    out[:, 9] = cloud / 100.0
    out[:, 10] = delta(cloud, 1) / 50.0

    return np.nan_to_num(out, nan=0.0, posinf=0.0, neginf=0.0)


def load_nwp(villages, dates):
    """
    NWP (ERA5 reanalysis) variables -> derived features.

    OUTPUT: float32[V, T, N_NWP_FEATURES], aur ek bool ki data mila ya nahi.
            Data na mile to zeros (model rainfall-only mode mein chalta rahega).

    KYUN derived (kachche columns nahi): pressure 1007 hPa aur humidity 85 ko seedha
    daalne se scaling bekaar ho jaati, aur direction (degrees) mein 359 aur 1 ek doosre
    se bahut door dikhte jabki wo paas hain. Isliye anomaly/delta/sin-cos.
    """
    nwp_dir = DATA / "nwp"
    out = np.zeros((len(villages), len(dates), N_NWP_FEATURES), dtype=np.float32)

    if not nwp_dir.exists():
        return out, False

    date_idx = {d: i for i, d in enumerate(dates)}
    found = 0

    for vi_, v in enumerate(villages):
        path = nwp_dir / f"{v['id']}.csv"
        if not path.exists():
            continue
        found += 1

        raw = {c: np.full(len(dates), np.nan, dtype=np.float32) for c in NWP_COLUMNS}
        with open(path) as f:
            for row in csv.DictReader(f):
                j = date_idx.get(row["date"])
                if j is None:
                    continue
                for c in NWP_COLUMNS:
                    val = row.get(c, "")
                    if val != "":
                        raw[c][j] = float(val)

        out[vi_] = nwp_features_from_canonical(power_to_canonical(raw))

    return np.nan_to_num(out, nan=0.0, posinf=0.0, neginf=0.0), found == len(villages)


def build_labels(villages, rain):
    """
    Har gaon-din ka RiskEngine level. OUTPUT: (labels int8[V, T], cum3 float32[V, T])

    cum3 = aaj + pichhle 2 din (OpenMeteoService::rain_3day_mm ki wahi definition).
    Pehle 2 din ka cum3 adhoora hai — unhe baad mein drop kar dete hain.
    """
    V, T = rain.shape
    labels = np.zeros((V, T), dtype=np.int8)
    cum3 = np.zeros((V, T), dtype=np.float32)

    for i, v in enumerate(villages):
        series = rain[i]
        c = np.convolve(series, np.ones(3, dtype=np.float32), mode="full")[:T]
        cum3[i] = c
        for t in range(T):
            lvl, _ = rr.assess(float(series[t]), float(c[t]), v["elevation_m"],
                               v["warning_m"], v["danger_m"])
            labels[i, t] = rr.LEVEL_IDX[lvl]

    return labels, cum3


def river_fraction(v, cum3_value):
    """
    River proxy ko ek scale-free number mein badlo:
        0.0 = warning mark, 1.0 = danger mark
    KYUN: har station ke absolute metres alag hain (Dhubri 29 m, Diphu 185 m). Kachche
    metres model ko station pehchanna sikha denge, paani ka behaviour nahi.
    River data nahi to 0 — `has_river` flag se model ko pata rehta hai ki ye asli 0 nahi.
    """
    if v["warning_m"] is None or v["danger_m"] is None:
        return 0.0
    span = v["danger_m"] - v["warning_m"]
    if span <= 0:
        span = 1.0
    level = rr.estimate_river_level(v["warning_m"], v["danger_m"], cum3_value)
    return float(np.clip((level - v["warning_m"]) / span, -3.0, 3.0))


def village_features(v, rain_row):
    """Gaon ki na-badalne wali 5 baatein."""
    has_river = 1.0 if (v["warning_m"] is not None and v["danger_m"] is not None) else 0.0
    span = (v["danger_m"] - v["warning_m"]) if has_river else 0.0
    return np.array([
        v["elevation_m"] / 200.0,
        has_river,
        1.0 if v["elevation_m"] <= rr.LOW_ELEVATION_M else 0.0,
        min(span, 5.0) / 5.0,
        float(rain_row.mean()) / 20.0,     # gaon ki apni climatology (औसत barish)
    ], dtype=np.float32)


def known_future_features(rain_row, t):
    """
    ============ YE B2 KA SABSE ZAROORI HISSA HAI ============

    Label cum3 (3-din ka total) se banta hai. Forecast ke waqt (din t) cum3 ka ek hissa
    HUME PEHLE SE PATA HAI:

        cum3(t+1) = rain[t-1] + rain[t] + rain[t+1]   <- 3 mein se 2 pata hai
        cum3(t+2) = rain[t]   + rain[t+1] + rain[t+2] <- 3 mein se 1 pata hai

    Yehi wajah hai ki +24h aasan hai aur +48h mushkil.

    Ye "known part" aur threshold (192 / 352) se uski DOORI hi wo statistic hai jispe
    poora faisla tika hai. Pehle model ko sirf kachcha rainfall sequence deta tha aur
    usse ummeed thi ki wo ye ghatana khud seekh lega — nahi seekha (RED recall 0.000,
    ek trivial baseline se bhi bura). Ye numbers seedha dene se model ka kaam sirf itna
    bachta hai: "bachi hui barish kitni hogi?"

    YE LEAKAGE NAHI HAI: teeno number sirf din t tak ke rainfall se bante hain, jo
    forecast ke waqt system ke paas already hai.
    """
    known24 = float(rain_row[t] + rain_row[t - 1])   # cum3(t+1) ka pata hissa
    known48 = float(rain_row[t])                     # cum3(t+2) ka pata hissa
    return np.array([
        np.log1p(known24) / 5.0,
        np.log1p(known48) / 5.0,
        np.clip((rr.CUM3_WARNING_MM - known24) / 100.0, -3, 5),   # warning tak aur kitni barish chahiye
        np.clip((rr.CUM3_DANGER_MM - known24) / 100.0, -3, 5),    # danger tak
        np.clip((rr.CUM3_WARNING_MM - known48) / 100.0, -3, 5),
        np.clip((rr.CUM3_DANGER_MM - known48) / 100.0, -3, 5),
    ], dtype=np.float32)


def build_sequences(villages, dates, rain, labels, cum3, nwp=None):
    """
    Sabhi (village, day) samples banao.

    Ek sample t pe: features = din t-L+1 .. t (aaj tak ka SAB PATA hai),
                    target   = label[t+1] aur label[t+2] (BHAVISHYA — ye nahi pata)

    OUTPUT: dict of arrays + split masks
    """
    V, T = rain.shape
    L = SEQ_LEN

    # t ki range: shuru mein L-1 din history chahiye (aur cum3 ke liye 2), aakhir mein 2 din target
    t_start = max(L - 1, 2)
    t_end = T - 3            # t+2 <= T-1

    if nwp is None:
        nwp = np.zeros((V, T, N_NWP_FEATURES), dtype=np.float32)

    doy = np.array([
        (int(d[5:7]) - 1) * 31 + int(d[8:10]) for d in dates
    ], dtype=np.float32)
    doy_sin = np.sin(2 * math.pi * doy / 372.0)
    doy_cos = np.cos(2 * math.pi * doy / 372.0)

    n_per_v = t_end - t_start + 1
    N = V * n_per_v

    X_seq = np.zeros((N, L, N_SEQ_FEATURES), dtype=np.float32)
    X_static = np.zeros((N, N_STATIC_FEATURES), dtype=np.float32)
    y24 = np.zeros(N, dtype=np.int64)
    y48 = np.zeros(N, dtype=np.int64)
    v_idx = np.zeros(N, dtype=np.int32)
    t_idx = np.zeros(N, dtype=np.int32)
    y_now = np.zeros(N, dtype=np.int64)      # aaj ka level — persistence baseline ke liye

    k = 0
    for i, v in enumerate(villages):
        vfeat = village_features(v, rain[i])
        # river fraction poori series ke liye ek baar
        rfrac = np.array([river_fraction(v, float(c)) for c in cum3[i]], dtype=np.float32)

        for t in range(t_start, t_end + 1):
            sl = slice(t - L + 1, t + 1)
            X_seq[k, :, 0] = np.log1p(rain[i, sl])
            X_seq[k, :, 1] = np.log1p(cum3[i, sl])
            X_seq[k, :, 2] = rfrac[sl]
            X_seq[k, :, 3] = doy_sin[sl]
            X_seq[k, :, 4] = doy_cos[sl]
            # NWP features — slice `t` par khatam hota hai, isliye kabhi bhavishya nahi dekhta
            X_seq[k, :, N_RAIN_FEATURES:] = nwp[i, sl, :]
            X_static[k] = np.concatenate([vfeat, known_future_features(rain[i], t)])
            y24[k] = labels[i, t + 1]
            y48[k] = labels[i, t + 2]
            y_now[k] = labels[i, t]
            v_idx[k] = i
            t_idx[k] = t
            k += 1

    assert k == N

    sample_dates = np.array([dates[t] for t in t_idx])
    train = sample_dates <= TRAIN_END
    val = (sample_dates > TRAIN_END) & (sample_dates <= VAL_END)
    test = sample_dates > VAL_END

    return {
        "X_seq": X_seq, "X_static": X_static,
        "y24": y24, "y48": y48, "y_now": y_now,
        "v_idx": v_idx, "t_idx": t_idx, "dates": sample_dates,
        "train": train, "val": val, "test": test,
        "villages": villages,
    }


def load_all(use_nwp=True):
    """
    Sab kuch ek call mein — notebook isi se shuru karta hai.

    use_nwp=False se NWP features zero ho jaate hain — yahi purana rainfall-only model hai,
    aur wahi apples-to-apples comparison deta hai (bilkul same split, same code).
    """
    villages, dates, rain = load_raw()
    labels, cum3 = build_labels(villages, rain)

    nwp, complete = (load_nwp(villages, dates) if use_nwp else (None, False))
    d = build_sequences(villages, dates, rain, labels, cum3, nwp)
    d["nwp_used"] = bool(use_nwp and complete)
    return d
