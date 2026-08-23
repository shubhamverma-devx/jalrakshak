"""
render_docs.py — README.md aur model_card.md ke andar ke METRIC blocks bharta hai.

KYUN SCRIPT SE, HAATH SE NAHI: metrics haath se copy karne mein ek digit galat likhna
bahut aasan hai — aur phir deck mein wahi galat number chala jaata hai. Ye script
seedha outputs/metrics.json padhti hai, to docs aur asli run kabhi alag nahi ho sakte.

Docs mein ye markers hone chahiye:
    <!-- METRICS:START --> ... <!-- METRICS:END -->
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).parent.parent
M = json.loads((HERE / "outputs" / "metrics.json").read_text())
R = json.loads((HERE / "outputs" / "per_region_test.json").read_text())


def table() -> str:
    t = M["test"]
    sweep = M["test_threshold_sweep"]
    best = M["best_threshold_by_iou"]
    cm = M["test_confusion"]
    rows = "\n".join(
        f"| {r['threshold']:.1f} | {r['iou']:.4f} | {r['f1']:.4f} | {r['precision']:.4f} | {r['recall']:.4f} |"
        for r in sweep
    )
    return f"""**Test set — {M['splits']['test']} held-out chips, threshold {M['threshold']}**

| Metric | Value |
|---|---|
| **IoU (water)** | **{t['iou']:.4f}** |
| **F1 (water)** | **{t['f1']:.4f}** |
| Precision | {t['precision']:.4f} |
| Recall | {t['recall']:.4f} |
| Pixel accuracy | {t['accuracy']:.4f} |

Confusion matrix (valid pixels only, water = positive):

| | pred: not water | pred: water |
|---|---|---|
| **true: not water** | {cm['tn']:,} | {cm['fp']:,} |
| **true: water** | {cm['fn']:,} | {cm['tp']:,} |

Threshold sweep (test set):

| thr | IoU | F1 | Precision | Recall |
|---|---|---|---|---|
{rows}

Best IoU **{best['iou']:.4f}** at threshold **{best['threshold']}**.

Training: best val IoU **{M['best_val_iou']:.4f}** at epoch {M['best_epoch']} of {M['epochs_run']} run · {M['device']} · {M['params_millions']}M params."""


def region_table(style: str) -> str:
    """
    Region-wise table. style='full' README ke liye, style='short' model card ke liye.
    KYUN render karte hain: pehle ye haath se likhi thi. Model dobara train hone pe
    wo numbers chup-chaap purane reh jaate — aur wahi deck mein chale jaate.
    """
    if style == "short":
        head = "| Region | IoU | Recall |\n|---|---|---|"
        rows = [f"| {'**' + d['region'] + '**' if d['region'] in ('India', 'Pakistan') else d['region']} "
                f"| {d['iou']:.4f} | {d['recall']:.4f} |" for d in R]
    else:
        head = ("| Region | chips | water % | IoU | F1 | Precision | Recall |\n"
                "|---|---|---|---|---|---|---|")
        rows = [f"| {'**' + d['region'] + '**' if d['region'] in ('India', 'Pakistan') else d['region']} "
                f"| {d['chips']} | {d['water_pct']:.1f} | {d['iou']:.4f} | {d['f1']:.4f} "
                f"| {d['precision']:.4f} | {d['recall']:.4f} |" for d in R]
    return head + "\n" + "\n".join(rows)


def patch_block(s: str, marker: str, body: str) -> str:
    a, b = f"<!-- {marker}:START -->", f"<!-- {marker}:END -->"
    if a not in s:
        return s
    pre, rest = s.split(a, 1)
    _, post = rest.split(b, 1)
    return f"{pre}{a}\n{body}\n{b}{post}"


def patch(path: Path) -> None:
    s = path.read_text()
    if "<!-- METRICS:START -->" not in s:
        print(f"  skip {path.name} (no marker)")
        return
    s = patch_block(s, "METRICS", table())
    s = patch_block(s, "REGIONS", region_table("short" if "model_card" in path.name else "full"))
    path.write_text(s)
    print(f"  patched {path.name}")


if __name__ == "__main__":
    for f in ("README.md", "model_card.md"):
        p = HERE / f
        if p.exists():
            patch(p)
    sys.exit(0)
