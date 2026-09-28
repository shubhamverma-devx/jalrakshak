"""
fetch_forecast_data.py — B2 ka training data laata hai.

KYA: Open-Meteo ke HISTORICAL ARCHIVE se hamare 30 Assam gaon ka roz ka rainfall,
2000 se 2025 tak. Free, koi API key nahi (decision D5 — wahi source jo live mode
use karta hai, bas archive endpoint).

KYUN ITNE SAAL: flood ek DURLABH ghatna hai. 2-3 saal mein RED wale din itne kam
milenge ki koi bhi model "hamesha GREEN bolo" seekh lega aur 97% accuracy dikha dega.
26 saal se monsoon ke kaafi extreme din mil jaate hain.

OUTPUT: data/forecast/rainfall_daily.csv  (village_id, date, rain_mm)

NOTE: archive ERA5 reanalysis pe based hai — ye SATELLITE/STATION observation ka
gridded product hai, gaon ka apna raingauge nahi. Grid cell ~11 km ka hai, to ek hi
cell mein do gaon aa sakte hain. Ye limitation FORECAST_README.md mein likhi hai.
"""

import csv
import json
import sys
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import urlopen

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
OUT_DIR = HERE / "data" / "forecast"

ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"
START = "2000-01-01"
END = "2025-12-31"

# Ek request mein kitne gaon.
# KYUN sirf 4: Open-Meteo free tier request ko DATA VOLUME se tolta hai, count se nahi.
# 26 saal x 10 gaon ek call mein = bahut bada, seedha HTTP 429. 4 gaon x 26 saal theek
# se nikal jaata hai. Ye speed ka nahi, "server ko na chidhane" ka number hai.
BATCH = 4

# 429 ke baad kitni der rukna (seconds). Har retry pe agla number.
BACKOFF = [20, 45, 90, 180]


def load_villages():
    """assam_villages.csv + river_stations.csv ko jod ke har gaon ka poora record."""
    stations = {}
    with open(REPO / "data" / "river_stations.csv") as f:
        for row in csv.DictReader(f):
            def num(x):
                x = (x or "").strip()
                return float(x) if x not in ("", "NULL", "null") else None
            stations[row["name"]] = (num(row["warning_level_m"]), num(row["danger_level_m"]))

    villages = []
    with open(REPO / "data" / "assam_villages.csv") as f:
        for i, row in enumerate(csv.DictReader(f), start=1):
            warn, danger = stations.get(row["river_station_name"], (None, None))
            villages.append({
                "id": i,                       # seeder bhi isi order mein id deta hai
                "name": row["name"],
                "district": row["district"],
                "lat": float(row["lat"]),
                "lng": float(row["lng"]),
                "elevation_m": int(row["elevation_m"]),
                "population": int(row["population"]),
                "station": row["river_station_name"],
                "warning_m": warn,
                "danger_m": danger,
            })
    return villages


def fetch_batch(batch):
    """Ek batch ka daily precipitation. OUTPUT: [{village_id, dates[], rain[]}]"""
    q = urlencode({
        "latitude": ",".join(str(v["lat"]) for v in batch),
        "longitude": ",".join(str(v["lng"]) for v in batch),
        "start_date": START,
        "end_date": END,
        "daily": "precipitation_sum",
        "timezone": "Asia/Kolkata",
    })
    # Open-Meteo free tier pe 429 aana normal hai — ruk ke dobara maango, girna nahi.
    url = f"{ARCHIVE_URL}?{q}"
    data = None
    for attempt, wait in enumerate([0] + BACKOFF):
        if wait:
            print(f"      rate limited — waiting {wait}s (retry {attempt}/{len(BACKOFF)})", flush=True)
            time.sleep(wait)
        try:
            with urlopen(url, timeout=300) as r:
                data = json.load(r)
            break
        except HTTPError as e:
            if e.code != 429 or attempt == len(BACKOFF):
                raise
    if data is None:
        raise RuntimeError("Open-Meteo se data nahi mila")

    # Ek coordinate pe API dict deta hai, kai pe list. Dono sambhalo.
    if isinstance(data, dict):
        data = [data]

    out = []
    for v, block in zip(batch, data):
        daily = block["daily"]
        out.append({
            "village_id": v["id"],
            "dates": daily["time"],
            "rain": [0.0 if x is None else float(x) for x in daily["precipitation_sum"]],
        })
    return out


def main():
    villages = load_villages()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # Gaon ki poori list bhi likh dete hain — training aur forecast.py dono ko chahiye,
    # aur isse ml/ backend ke CSV pe seedha depend nahi karta.
    with open(OUT_DIR / "villages.json", "w") as f:
        json.dump(villages, f, indent=2)

    rows = []
    for i in range(0, len(villages), BATCH):
        batch = villages[i:i + BATCH]
        names = ", ".join(v["name"] for v in batch[:3])
        print(f"  fetching {i+1}-{i+len(batch)} of {len(villages)}  ({names}, ...)", flush=True)
        for res in fetch_batch(batch):
            for d, mm in zip(res["dates"], res["rain"]):
                rows.append((res["village_id"], d, round(mm, 2)))
        time.sleep(8)  # API pe zyada zor nahi — free tier hai, jaldi ki koi zaroorat nahi

    out = OUT_DIR / "rainfall_daily.csv"
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["village_id", "date", "rain_mm"])
        w.writerows(rows)

    days = len({r[1] for r in rows})
    print(f"\n  {len(rows):,} rows  ·  {len(villages)} villages  ·  {days:,} days")
    print(f"  {START} -> {END}")
    print(f"  saved: {out.relative_to(REPO)}")


if __name__ == "__main__":
    sys.exit(main())
