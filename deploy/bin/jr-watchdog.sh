#!/usr/bin/env bash
# =============================================================================
#  jr-watchdog.sh — har 2 min: nginx, php-fpm, MySQL zinda hain? Nahi to uthao.
#  Install: /usr/local/bin/jr-watchdog.sh   (jr-watchdog.timer chalata hai, root se)
# =============================================================================
#  KYUN systemd Restart= ke upar ek aur layer:
#    1. StartLimit ke baad systemd haar maan leta hai ("failed" state) — ye reset karta hai.
#    2. Process zinda par ATKA ho sakta hai (php-fpm ke saare worker hang). systemd ko
#       lagta hai sab theek hai. Isliye ek asli HTTP check bhi: /api/health localhost se.
#       502/504 ya timeout do baar lagatar => php-fpm restart.
#  Har action `journalctl -t jr-watchdog` mein dikhta hai.
# =============================================================================
set -u

SERVICES=(nginx php8.3-fpm mysql)
STATE=/run/jr-watchdog.fails
DOMAIN_FILE=/etc/jr-watchdog.domain   # harden.sh isme domain likhta hai

log() { logger -t jr-watchdog "$*"; }

for svc in "${SERVICES[@]}"; do
    if ! systemctl is-active --quiet "$svc"; then
        log "$svc not active — restarting"
        systemctl reset-failed "$svc" 2>/dev/null
        systemctl restart "$svc" && log "$svc restarted" || log "$svc restart FAILED"
    fi
done

# --- HTTP check: php-fpm atka to nahi? ------------------------------------------
# Host header ke saath localhost pe — Cloudflare/DNS ki dikkat ko server ki dikkat na samjhe.
# 503 (DB down) ko yahan fail nahi maante: MySQL upar wale loop mein sambhal liya.
[ -r "$DOMAIN_FILE" ] || exit 0
domain=$(cat "$DOMAIN_FILE")
# certbot ke baad port 80 sirf 301 deta hai, isliye pehle HTTPS (--resolve = localhost
# pe hi jao, DNS/Cloudflare bypass). Cert abhi na bana ho to HTTP.
code=$(curl -sk -o /dev/null -m 15 -w '%{http_code}' --resolve "$domain:443:127.0.0.1" "https://$domain/api/health")
if [ "$code" = "000" ]; then
    code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' -H "Host: $domain" http://127.0.0.1/api/health)
fi

case "$code" in
    200|503)
        rm -f "$STATE" ;;
    *)
        fails=$(( $(cat "$STATE" 2>/dev/null || echo 0) + 1 ))
        echo "$fails" > "$STATE"
        log "health check got HTTP $code (fail $fails)"
        if [ "$fails" -ge 2 ]; then
            log "restarting php8.3-fpm after $fails failed health checks"
            systemctl restart php8.3-fpm
            rm -f "$STATE"
        fi ;;
esac
