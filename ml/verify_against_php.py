"""
verify_against_php.py — risk_rules.py (Python) == RiskEngine.php (PHP)?

KYUN YE ZAROORI HAI: B2 ke saare training labels risk_rules.py se bante hain. Agar wo
PHP se zara bhi alag hua, to model ek definition seekhega aur dashboard doosri dikhayega —
aur ye farq kisi test mein pakda nahi jaayega, bas forecast chupchaap galat hota rahega.

Ye script asli PHP engine ko 4,320 combinations pe chalati hai (rainfall x cum3 x elevation
x threshold-pairs, boundary values ke aas-paas ghani) aur dono ko compare karti hai.

Do asli bug isi ne pakde the:
  1. PHP ka round() aadhe ko zero se DOOR le jaata hai, Python ka nazdeeki EVEN pe —
     35.925 -> PHP 35.93, Python 35.92. River level warning/danger se compare hota hai,
     to ek paisa bhar ka farq poora label palat deta tha. (253/4320 galat)
  2. PHP mein low elevation `<= 80` hai, `< 80` nahi. (wahi 253 cases)

Chalao: python verify_against_php.py       (PHP chahiye + backend/vendor installed)
"""

import json
import subprocess
import sys
import tempfile
from pathlib import Path

import risk_rules as rr

HERE = Path(__file__).resolve().parent


def main():
    grid_php = HERE / "scripts" / "risk_grid.php"
    if not grid_php.exists():
        sys.exit(f"nahi mila: {grid_php}")

    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tmp:
        out_path = tmp.name

    proc = subprocess.run(["php", str(grid_php), out_path], capture_output=True, text=True)
    if proc.returncode != 0:
        sys.exit(f"PHP chala nahi:\n{proc.stderr[:800]}")

    cases = json.loads(Path(out_path).read_text())

    bad_river = bad_level = 0
    examples = []

    for c in cases:
        py_lvl_m = rr.estimate_river_level(c["warn"], c["danger"], c["cum3"])
        php_lvl_m = c["level_m"]
        if (py_lvl_m is None) != (php_lvl_m is None) or (
            py_lvl_m is not None and abs(py_lvl_m - php_lvl_m) > 1e-9
        ):
            bad_river += 1
            if len(examples) < 5:
                examples.append(f"  river_level: {c} -> php={php_lvl_m} py={py_lvl_m}")

        py_level, _ = rr.assess(c["rain"], c["cum3"], c["elev"], c["warn"], c["danger"])
        if py_level != c["level"]:
            bad_level += 1
            if len(examples) < 5:
                examples.append(f"  risk_level: {c} -> php={c['level']} py={py_level}")

    print(f"  cases compared      : {len(cases):,}")
    print(f"  river-level mismatch: {bad_river}")
    print(f"  risk-level mismatch : {bad_level}")
    for e in examples:
        print(e)

    if bad_river or bad_level:
        print("\n  MISMATCH — labels bharose ke laayak nahi. Training se pehle theek karo.")
        return 1

    print("\n  OK — Python port asli RiskEngine se bilkul milta hai.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
