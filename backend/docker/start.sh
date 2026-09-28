#!/usr/bin/env bash
# =============================================================================
#  start.sh — Railway container ka entrypoint (har deploy/restart pe chalta hai)
# =============================================================================
#  ORDER:
#   1. Firebase credential env se file mein (repo mein kabhi nahi)
#   2. DB ka intezaar -> migrate -> seed (seeders idempotent: updateOrCreate/upsert)
#   3. config:cache + route:cache (env yahin set hai, build time pe nahi)
#   4. Replay cache garam + live ek baar
#   5. Scheduler loop BACKGROUND mein (Railway free pe cron nahi — isi container mein)
#   6. FrankenPHP foreground mein ($PORT)
# =============================================================================
set -uo pipefail
cd /app/backend

log() { echo "[start] $*"; }

if [ -z "${APP_KEY:-}" ]; then
    log "APP_KEY set nahi hai — Railway Variables mein daalo (deploy/README.md). Ruk rahe hain."
    exit 1
fi

# --- 1. Firebase --------------------------------------------------------------
# Service-account JSON ko base64 karke ek env var mein rakhte hain (file commit nahi ho
# sakti). Na ho to bhi app chalti hai: alert save hota hai, push.error saaf batata hai.
if [ -n "${FIREBASE_CREDENTIALS_BASE64:-}" ]; then
    echo "$FIREBASE_CREDENTIALS_BASE64" | base64 -d > storage/app/firebase/firebase-admin.json
    export FIREBASE_CREDENTIALS=/app/backend/storage/app/firebase/firebase-admin.json
    log "firebase credentials written"
else
    log "FIREBASE_CREDENTIALS_BASE64 nahi — push notifications off (alerts phir bhi save honge)"
fi

# --- 2. DB: intezaar + migrate + seed ------------------------------------------
# MySQL service kabhi-kabhi app se der se uthti hai (restart ke baad). 20 x 3s.
for i in $(seq 1 20); do
    if php artisan migrate --force --no-interaction; then break; fi
    log "migrate fail (DB abhi nahi mila?) — retry $i/20"
    sleep 3
done
# Idempotent: dobara chalne pe koi duplicate row nahi (verify kiya: 28/30/360/10 wahi rehte).
php artisan db:seed --force --no-interaction || log "seed fail — upar ka error dekho"

# --- 3. Production caches -------------------------------------------------------
php artisan config:cache
php artisan route:cache

# --- 4. Cache garam -------------------------------------------------------------
# --no-store: replay ke liye risk_scores mein har deploy pe 360 nayi row nahi likhni.
php artisan risk:compute --mode=replay --all --no-store || log "replay warm fail"
php artisan risk:compute || log "live compute fail (Open-Meteo?) — scheduler 30 min mein phir"

# --- 5. Scheduler ---------------------------------------------------------------
# Railway free pe cron service nahi, isliye isi container mein har minute schedule:run.
# while-loop (schedule:work nahi) — ek run crash ho to loop zinda rehta hai.
# Sequential loop = dher nahi lagta; `timeout 25m` + withoutOverlapping(25) = ek atka run
# bhi 25 min mein khatam. Container restart = loop bhi naya.
(
    while true; do
        timeout 25m php artisan schedule:run --no-interaction 2>&1 | grep -v "No scheduled commands are ready" || true
        sleep 60
    done
) &
log "scheduler loop started"

# --- 6. Web server --------------------------------------------------------------
log "serving on :${PORT:-8080}"
exec frankenphp php-server --root public/ --listen ":${PORT:-8080}"
