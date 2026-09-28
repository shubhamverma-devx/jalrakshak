#!/usr/bin/env bash
# =============================================================================
#  push-dashboard.sh — LAPTOP pe chalao: dashboard build karke droplet pe bhejo
#  USAGE: deploy/push-dashboard.sh <domain> <ssh-host>
#         deploy/push-dashboard.sh jalrakshak.example.com <ssh-user>@1.2.3.4
# =============================================================================
#  KYUN laptop pe build (droplet pe nahi): npm install + vite build 1GB box pe 400-600 MB
#  leta hai — wahi RAM jisse doosri do sites chal rahi hain. dist/ sirf static files hai,
#  rsync se chala jaata hai.
#
#  Build se pehle fallback JSON taaza karta hai (replay + predict.py x 4 scenes) — yahi
#  wo copy hai jo server gire tab dikhti hai.
# =============================================================================
set -euo pipefail
DOMAIN="${1:?domain do}"; HOST="${2:?ssh host do (user@ip)}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "== fallback JSON (replay + SAR predict.py)"
(cd "$ROOT/backend" && php artisan risk:compute --mode=replay --all >/dev/null && php artisan dashboard:export-fallback)

echo "== build (API = https://$DOMAIN/api)"
(cd "$ROOT/dashboard" && VITE_API_URL="https://$DOMAIN/api" npm run build)

echo "== rsync -> $HOST"
rsync -az --delete "$ROOT/dashboard/dist/" "$HOST:/var/www/jalrakshak/dashboard/dist/"
echo "done: https://$DOMAIN"
