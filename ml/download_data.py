"""
download_data.py — Sen1Floods11 ka hand-labeled subset laata hai.

KYA: 446 hand-labeled chips (S1Hand = Sentinel-1 SAR, LabelHand = paani ka mask)
     + official train/valid/test splits.

KYUN SIRF HAND-LABELED (poora 4,831 nahi):
  Poore dataset mein baaki chips ke labels ALGORITHM se bane hain (Otsu thresholding,
  JRC permanent water). Wo weakly-labeled hain — unpe train karke jo metric aayega wo
  "humne algorithm ki nakal kar li" batayega, "humne paani dhoondhna seekha" nahi.
  Hand-labeled 446 pe insaan ne khud paani mark kiya hai. Kam data, par sach.
  (BUILD_PLAN B1 bhi yahi kehta hai: pehle 446, time bache to scale karo.)

KYUN gsutil NAHI: bucket publicly readable hai HTTPS pe, to gcloud SDK install
karne ki zaroorat hi nahi. Sirf requests/urllib se kaam ho jaata hai.

Licence: CC-BY 4.0 (Cloud to Street / Google). Citation README.md mein.
"""

import csv
import io
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

BUCKET = "https://storage.googleapis.com/sen1floods11"
BASE = "v1.1/data/flood_events/HandLabeled"
SPLITS = "v1.1/splits/flood_handlabeled"

HERE = Path(__file__).parent
DATA = HERE / "data"


def fetch(url: str) -> bytes:
    """Ek file download karo. Retry ke saath — GCS kabhi-kabhi 503 deta hai."""
    last = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
    raise RuntimeError(f"download fail: {url} ({last})")


def read_split(name: str):
    """
    Split CSV padho.
    INPUT : 'train' | 'valid' | 'test'
    OUTPUT: [(s1_filename, label_filename), ...]
    """
    raw = fetch(f"{BUCKET}/{SPLITS}/flood_{name}_data.csv").decode()
    rows = list(csv.reader(io.StringIO(raw)))
    return [(r[0].strip(), r[1].strip()) for r in rows if len(r) >= 2 and r[0].strip()]


def download_one(args):
    """Ek chip (S1 + label) download karo. Pehle se hai to skip."""
    split, s1_name, label_name = args
    out_s1 = DATA / split / "S1" / s1_name
    out_lb = DATA / split / "Label" / label_name

    for out, remote in ((out_s1, f"S1Hand/{s1_name}"), (out_lb, f"LabelHand/{label_name}")):
        if out.exists() and out.stat().st_size > 0:
            continue
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(fetch(f"{BUCKET}/{BASE}/{remote}"))
    return s1_name


def main():
    total = 0
    for split in ("train", "valid", "test"):
        pairs = read_split(split)
        print(f"{split}: {len(pairs)} chips")

        jobs = [(split, s1, lb) for s1, lb in pairs]
        # 8 parallel — GCS ise aaram se handle karta hai, aur 700MB jaldi aa jaata hai.
        with ThreadPoolExecutor(max_workers=8) as ex:
            for i, _ in enumerate(ex.map(download_one, jobs), 1):
                if i % 25 == 0 or i == len(jobs):
                    print(f"  {i}/{len(jobs)}", flush=True)
        total += len(pairs)

    size = sum(f.stat().st_size for f in DATA.rglob("*.tif"))
    print(f"\nDone: {total} chips, {size / 1e6:.0f} MB in {DATA}")


if __name__ == "__main__":
    sys.exit(main())
