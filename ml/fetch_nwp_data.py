"""
fetch_nwp_data.py — B2 ke liye atmospheric (NWP-family) variables.

============ SOURCE: NASA POWER (MERRA-2 reanalysis) ============
Pehle Open-Meteo ka archive (ERA5) try kiya tha. Wo scientifically theek tha par uska
free tier request ko DATA VOLUME se tolta hai: 30 gaon x 8 variables x 26 saal us quota
se kai guna zyada hai. Measure kiya — ghante mein sirf ~2-3 gaon nikalte the aur quota
har ghante reset hota tha, yaani poore data ke liye 6-7 ghante lagte.

NASA POWER wahi cheez deta hai — daily reanalysis fields, free, bina API key ke — aur
poore 30 gaon 2 minute mein aa jaate hain. Isliye source badla gaya.

============ YE VARIABLES ASAL MEIN HAIN KYA — YE PADHNA ZAROORI HAI ============
NASA POWER ke peeche **MERRA-2 reanalysis** hai. Reanalysis matlab: ek numerical weather
prediction model ko purane observations (satellite, station, radiosonde) ke saath dobara
chalaya gaya, taaki ateet ke mausam ki sabse achhi tasveer bane.

To technically ye **NWP model ka output** hai — par **operational forecast NAHI**:

  - Reanalysis (jo hamare paas hai): "us din mausam kaisa THA", observations ke saath fit.
  - Operational forecast (asli deployment mein): "kal kaisa HOGA", bina kal ke observations.
    Ye hamesha kam sateek hota hai.

Isliye README mein ise "NWP forecast" nahi, "**NWP-based reanalysis**" likha gaya hai.

============ LEAKAGE KA NIYAM (sabse zaroori) ============
Feature banate waqt SIRF din `t` tak ki value use hoti hai — kabhi `t+1` ya `t+2` ki nahi.
Reanalysis mein t+1 ka data "kal ka ASLI mausam" hota hai; use feature banana matlab model
ko jawaab dikha dena. Score shaandaar aata aur deployment mein bilkul bekaar hota.
Ye niyam `forecast_data.py` ke `build_sequences()` mein enforce hai (slice `t` par khatam).

OUTPUT: data/forecast/nwp/{village_id}.csv   (resumable)
"""

import csv
import json
import sys
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen

HERE = Path(__file__).resolve().parent
OUT_DIR = HERE / "data" / "forecast" / "nwp"

POWER_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"
START = "20000101"
END = "20251231"

# NASA POWER ke naam. Comments mein wahi cheez Open-Meteo ki bhasha mein bhi likhi hai,
# kyunki inference ke waqt Open-Meteo se aati hai (neeche unit note dekho).
VARIABLES = [
    "PS",          # surface pressure, kPa   -> hPa mein badla jaata hai
    "RH2M",        # relative humidity 2m, %
    "T2MDEW",      # dew point 2m, degC
    "T2M_MAX",     # max temp, degC
    "T2M_MIN",     # min temp, degC
    "WS10M",       # wind speed 10m, m/s
    "WD10M",       # wind direction 10m, degrees
    "CLOUD_AMT",   # cloud amount, %
]

MISSING = -999.0     # POWER ka missing marker


def fetch_village(v):
    """Ek gaon ke saare variables, poore date range ke liye."""
    q = urlencode({
        "parameters": ",".join(VARIABLES),
        "community": "AG",
        "latitude": v["lat"], "longitude": v["lng"],
        "start": START, "end": END,
        "format": "JSON",
    })

    last = None
    for attempt in range(5):
        try:
            with urlopen(f"{POWER_URL}?{q}", timeout=300) as r:
                return json.load(r)["properties"]["parameter"]
        except Exception as e:       # noqa: BLE001 — network flake, 5 tries
            last = e
            print(f"      {type(e).__name__} — retry {attempt+1}/5 in 15s", flush=True)
            time.sleep(15)
    raise RuntimeError(f"NASA POWER se data nahi mila: {last}")


def main():
    villages_path = HERE / "data" / "forecast" / "villages.json"
    if not villages_path.exists():
        sys.exit("villages.json nahi mila — pehle chalao: python fetch_forecast_data.py")

    villages = json.load(open(villages_path))
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    done = skipped = 0
    for i, v in enumerate(villages, 1):
        out = OUT_DIR / f"{v['id']}.csv"
        if out.exists():
            skipped += 1
            continue

        print(f"  [{i}/{len(villages)}] {v['name']}", flush=True)
        param = fetch_village(v)
        dates = sorted(param[VARIABLES[0]].keys())

        with open(out, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["date"] + VARIABLES)
            for d in dates:
                # POWER YYYYMMDD deta hai; baaki pipeline YYYY-MM-DD par chalti hai
                iso = f"{d[:4]}-{d[4:6]}-{d[6:]}"
                row = [iso]
                for var in VARIABLES:
                    x = param[var].get(d)
                    row.append("" if x is None or x == MISSING else x)
                w.writerow(row)

        done += 1
        time.sleep(1)

    print(f"\n  fetched {done}, already had {skipped}  ->  {OUT_DIR.relative_to(HERE.parent)}")
    files = sorted(OUT_DIR.glob("*.csv"))
    if files:
        rows = sum(1 for _ in open(files[0])) - 1
        print(f"  {len(files)} files, {rows:,} days each, {len(VARIABLES)} variables")


if __name__ == "__main__":
    sys.exit(main())
