"""
forecast.py — B2 inference. Ek gaon ka +24h / +48h risk forecast.

CHALAO:
    python forecast.py 10                 # village id
    python forecast.py 10 --json          # Laravel/API ke liye
    python forecast.py --list             # kaunse id hain

YE KYA DETA HAI: har horizon pe predicted risk level, predicted barish (mm), aur ek
confidence jo BANAYI HUI nahi hai — wo model ke apne quantiles se aati hai (neeche dekho).

============ IMANDAARI (FORECAST_README.md padho) ============
Ye model apne test set pe ek TRIVIAL baseline se mushkil se behtar hai:
  +24h macro-F1 0.5953  vs  "aaj wala hi level kal bhi" ka 0.6004  (yaani THODA BURA)
  +48h macro-F1 0.5219  vs  0.5113                                 (yaani THODA BEHTAR)
RED recall: +24h par 0.378, +48h par 0.178 — yaani teen mein se ek se bhi kam asli
RED din pakde jaate hain. Isko "AI flood predict karta hai" bol dena JHOOTH hoga.
UI mein ye numbers dikhte hain, chhupe nahi hain.
"""

import argparse
import json
import math
import sys
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen

import numpy as np
import torch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import forecast_data as fd          # noqa: E402
import forecast_model as fm         # noqa: E402
import risk_rules as rr             # noqa: E402

CKPT = HERE / "models" / "forecast_lstm.pt"
VILLAGES = HERE / "data" / "forecast" / "villages.json"
FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

# Model ko SEQ_LEN din chahiye, aur cum3 ke liye 2 din aur. Thoda extra maang lete hain.
PAST_DAYS = fd.SEQ_LEN + 5


def load_villages():
    """
    Gaon ki list. `data/forecast/villages.json` gitignored hai (wo poore dataset ke saath
    banti hai), isliye taaza clone pe wo nahi hoti.

    Us soorat mein use repo ke apne CSV se dobara bana lete hain — wahi 6 KB ka file hai,
    aur wahi code use hota hai jo fetch_forecast_data.py use karta hai. Iske bina inference
    ek missing derived file ki wajah se fail hoti, jabki uska poora source repo mein hai.
    """
    if VILLAGES.exists():
        return json.load(open(VILLAGES))

    try:
        from fetch_forecast_data import load_villages as build
    except ImportError:
        sys.exit(f"villages.json nahi mila: {VILLAGES}\nChalao: python fetch_forecast_data.py")

    villages = build()
    VILLAGES.parent.mkdir(parents=True, exist_ok=True)
    with open(VILLAGES, "w") as f:
        json.dump(villages, f, indent=2)
    return villages


def load_model():
    if not CKPT.exists():
        sys.exit(
            f"Model nahi mila: {CKPT}\n\n"
            "B2 ke weights repo mein nahi hain. Do raste:\n"
            "  1. khud train karo:  jupyter notebook train_forecast_lstm.ipynb\n"
            "  2. Release se lo (agar attach kiya ho): FORECAST_README.md dekho"
        )
    ck = torch.load(CKPT, map_location="cpu", weights_only=False)
    model = fm.RainfallQuantileLSTM(
        n_seq=ck["n_seq_features"],
        n_static=ck["n_static_features"],
        quantiles=ck["quantiles"],
    )
    model.load_state_dict(ck["state_dict"])
    model.eval()
    return model, ck


# Open-Meteo forecast endpoint ke naam -> canonical key.
# Units jaan-bujh ke match karaye gaye hain NASA POWER (training) ke saath:
#   surface_pressure_mean hPa   <-> PS kPa x 10
#   wind_speed_unit=ms          <-> WS10M m/s
# Agar ye units na milte to model ko dus-guna bada pressure-delta milta aur wo chup-chaap
# bakwaas forecast deta — crash kabhi nahi hota.
OM_TO_CANONICAL = {
    "surface_pressure_mean": "pressure_hpa",
    "relative_humidity_2m_mean": "rh_pct",
    "dew_point_2m_mean": "dewpoint_c",
    "temperature_2m_max": "tmax_c",
    "temperature_2m_min": "tmin_c",
    "wind_speed_10m_mean": "wind_ms",
    "wind_direction_10m_dominant": "wind_deg",
    "cloud_cover_mean": "cloud_pct",
}


def fetch_recent_weather(v):
    """
    Pichhle ~35 din ka rainfall + atmospheric variables, Open-Meteo se.
    OUTPUT: (dates list, rain float32[T], nwp float32[T, 11])

    ============ EK TRAIN/SERVE MISMATCH JO JAAN-BUJH KE LIYA GAYA HAI ============
    Training ka atmospheric data **NASA POWER (MERRA-2 reanalysis)** se aata hai — ateet
    ka mausam, observations ke saath fit kiya hua. Yahan inference par **Open-Meteo** ke
    operational model se aata hai.

    Ye do alag reanalysis/forecast systems hain. Units barabar kar di gayi hain (upar
    dekho), par values bilkul same nahi hongi. Iska seedha matlab: asli deployment mein
    accuracy test-set wali accuracy se THODI KAM hogi. Ye FORECAST_README.md mein likha hai.

    Isse bachne ka tareeka hota — training aur inference dono ek hi source se — par
    Open-Meteo ka archive free tier is volume par rate-limit kar deta hai (measure kiya:
    30 gaon x 26 saal ke liye 6-7 ghante), aur NASA POWER ka forecast endpoint hai hi nahi.
    """
    q = urlencode({
        "latitude": v["lat"], "longitude": v["lng"],
        "daily": "precipitation_sum," + ",".join(OM_TO_CANONICAL),
        "past_days": PAST_DAYS,
        "forecast_days": 1,
        "timezone": "Asia/Kolkata",
        "wind_speed_unit": "ms",        # NASA WS10M m/s mein hai — barabar rakhna zaroori
    })
    with urlopen(f"{FORECAST_URL}?{q}", timeout=60) as r:
        data = json.load(r)

    daily = data["daily"]
    dates = daily["time"]

    def col(name):
        return np.array([np.nan if x is None else float(x) for x in daily[name]], dtype=np.float32)

    rain = np.nan_to_num(col("precipitation_sum"), nan=0.0)
    canon = {ck: col(om) for om, ck in OM_TO_CANONICAL.items()}

    # Wahi shared function jo training mein chalta hai — do jagah likhne se drift hota.
    nwp = fd.nwp_features_from_canonical(canon)
    return dates, rain, nwp


def build_input(v, dates, rain, nwp, t):
    """
    Ek sample ke features — BILKUL wahi tareeka jo training mein tha (forecast_data.py).
    Ye function jaan-bujh ke waisa hi hai; feature banane ka doosra raasta banane se
    training aur inference chup-chaap alag ho jaate hain.
    """
    L = fd.SEQ_LEN
    cum3 = np.convolve(rain, np.ones(3, dtype=np.float32), mode="full")[:len(rain)]

    doy = np.array([(int(dd[5:7]) - 1) * 31 + int(dd[8:10]) for dd in dates], dtype=np.float32)
    doy_sin = np.sin(2 * math.pi * doy / 372.0)
    doy_cos = np.cos(2 * math.pi * doy / 372.0)

    sl = slice(t - L + 1, t + 1)
    x_seq = np.zeros((L, fd.N_SEQ_FEATURES), dtype=np.float32)
    x_seq[:, 0] = np.log1p(rain[sl])
    x_seq[:, 1] = np.log1p(cum3[sl])
    x_seq[:, 2] = [fd.river_fraction(v, float(c)) for c in cum3[sl]]
    x_seq[:, 3] = doy_sin[sl]
    x_seq[:, 4] = doy_cos[sl]
    x_seq[:, fd.N_RAIN_FEATURES:] = nwp[sl, :]     # NWP — slice `t` par khatam, bhavishya nahi

    x_static = np.concatenate([
        fd.village_features(v, rain),
        fd.known_future_features(rain, t),
    ]).astype(np.float32)

    return x_seq, x_static, cum3


def forecast_village(v, model, ck):
    """
    Ek gaon ka poora forecast.

    CONFIDENCE KAHAN SE AATI HAI: model ek nahi, KAI quantiles predict karta hai —
    yaani "kal 5 mm ho sakti hai, 20 bhi, 60 bhi". Har quantile ko RiskEngine se
    chala ke dekha jaata hai ki level kya banta hai. Jitne zyada quantiles ek hi level
    pe rukein, utna bharosa. Ye ek asli agreement-count hai, koi banaya hua number nahi.
    """
    dates, rain, nwp = fetch_recent_weather(v)
    t = len(rain) - 2                      # aakhri poora din (aakhri entry aaj ka adhoora forecast)
    x_seq, x_static, cum3 = build_input(v, dates, rain, nwp, t)

    with torch.no_grad():
        out = model(torch.from_numpy(x_seq)[None], torch.from_numpy(x_static)[None])
    pred = np.expm1(out[0].numpy()).clip(0)          # [2, n_quantiles] mm

    quantiles = ck["quantiles"]
    qi = {24: ck["q_index_24"], 48: ck["q_index_48"]}

    result = {
        "village_id": v["id"], "village": v["name"], "district": v["district"],
        "as_of": dates[t],
        "recent": {
            "rain_today_mm": round(float(rain[t]), 1),
            "rain_3day_mm": round(float(cum3[t]), 1),
            "level_now": rr.assess(float(rain[t]), float(cum3[t]), v["elevation_m"],
                                   v["warning_m"], v["danger_m"])[0],
        },
        "horizons": [],
    }

    for h in (24, 48):
        j = qi[h]
        # chune hue quantile ka faisla
        if h == 24:
            r_fut = float(pred[0, j])
            c3 = float(rain[t - 1] + rain[t]) + r_fut
        else:
            r1 = float(pred[0, j])
            r_fut = float(pred[1, j])
            c3 = float(rain[t]) + r1 + r_fut

        level, level_m = rr.assess(r_fut, c3, v["elevation_m"], v["warning_m"], v["danger_m"])

        # saare quantiles pe level nikaal ke agreement gino
        votes = []
        for k in range(len(quantiles)):
            if h == 24:
                rf = float(pred[0, k]); cc = float(rain[t-1] + rain[t]) + rf
            else:
                rf = float(pred[1, k]); cc = float(rain[t]) + float(pred[0, k]) + rf
            votes.append(rr.assess(rf, cc, v["elevation_m"], v["warning_m"], v["danger_m"])[0])

        agree = sum(1 for x in votes if x == level)
        result["horizons"].append({
            "hours": h,
            "level": level,
            "rain_mm": round(r_fut, 1),
            "rain_3day_mm": round(c3, 1),
            "river_level_m": level_m,
            "quantile_used": quantiles[j],
            "confidence": round(agree / len(quantiles), 2),
            "scenario_levels": votes,
        })

    return result


def main():
    ap = argparse.ArgumentParser(description="B2 — village flood risk forecast (+24h / +48h)")
    ap.add_argument("village_id", nargs="?", type=int)
    ap.add_argument("--json", action="store_true", help="machine-readable output")
    ap.add_argument("--list", action="store_true", help="village ids dikhao")
    args = ap.parse_args()

    villages = load_villages()

    if args.list:
        for v in villages:
            print(f"  {v['id']:3}  {v['name']:16} {v['district']}")
        return 0

    if args.village_id is None:
        ap.error("village_id chahiye (ya --list)")

    v = next((x for x in villages if x["id"] == args.village_id), None)
    if v is None:
        sys.exit(f"village id {args.village_id} nahi mila. `--list` se dekho.")

    model, ck = load_model()
    res = forecast_village(v, model, ck)

    if args.json:
        print(json.dumps(res))
        return 0

    print(f"\n  {res['village']} ({res['district']})   as of {res['as_of']}")
    print(f"  abhi : {res['recent']['level_now'].upper():6}  "
          f"rain {res['recent']['rain_today_mm']} mm  ·  3-day {res['recent']['rain_3day_mm']} mm")
    for h in res["horizons"]:
        print(f"  +{h['hours']:2}h : {h['level'].upper():6}  "
              f"rain {h['rain_mm']:5.1f} mm (q{h['quantile_used']})  "
              f"3-day {h['rain_3day_mm']:6.1f} mm  ·  confidence {h['confidence']:.2f}")
    print("\n  NOTE: ye forecast hamare apne RiskEngine ki definition ko aage le jaata hai —")
    print("        asli flood observation nahi. Accuracy limits FORECAST_README.md mein.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
