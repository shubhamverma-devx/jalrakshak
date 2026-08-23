#!/usr/bin/env bash
# finalize.sh — training ke BAAD sab kuch dobara sync karo.
#
# KYUN: model dobara train hone pe metrics badal sakte hain. Agar region table,
# qualitative figure aur docs alag-alag haath se update karein to koi ek chhoot
# jaata hai — aur wahi purana number deck mein chala jaata hai. Ek command, sab sync.
set -e
cd "$(dirname "$0")/.."
PY=./.venv/bin/python

echo "1/3  region-wise eval (naye weights pe)"
$PY scripts/per_region_eval.py | tail -12

echo
echo "2/3  qualitative figure"
$PY scripts/qualitative_figure.py

echo
echo "3/3  docs (metrics + region table) render"
$PY scripts/render_docs.py

echo
echo "--- notebook mein figures embed hue? ---"
$PY - <<'PY'
import nbformat
nb = nbformat.read("train_sar_unet.ipynb", as_version=4)
code = [c for c in nb.cells if c.cell_type == "code"]
imgs = sum(1 for c in code for o in c.get("outputs", []) if "image/png" in o.get("data", {}))
errs = sum(1 for c in code for o in c.get("outputs", []) if o.get("output_type") == "error")
print(f"  inline images: {imgs}   errors: {errs}")
PY
