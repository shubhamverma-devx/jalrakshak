#!/usr/bin/env bash
# =============================================================================
#  verify.sh — deploy ke baad public URLs pe end-to-end jaanch (laptop se)
#  USAGE: deploy/verify.sh https://<project>.vercel.app https://<service>.up.railway.app
# =============================================================================
#  Kya dekhta hai:
#    API    : /api/health 200 · replay ke 12 din (har din 30 gaon + risk level) ·
#             /api/relief + /api/alerts 200 · CORS header Vercel origin ke liye
#    Vercel : dashboard page · SPA rewrite · bundled fallback + SAR polygons
#  Kuch bhi fail = exit 1, aur kya fail hua wo saaf likha.
# =============================================================================
set -uo pipefail
WEB="${1:?vercel URL do}"; WEB="${WEB%/}"
API="${2:?railway URL do}"; API="${API%/}"; API="${API%/api}/api"
fail=0
pass() { printf '  \033[32mPASS\033[0m %s\n' "$*"; }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$*"; fail=1; }
code() { curl -s -o /dev/null -m 20 -w '%{http_code}' "$@"; }

echo "== API ($API)"
c=$(code "$API/health"); [ "$c" = 200 ] && pass "/health 200 — $(curl -s -m 20 "$API/health")" || bad "/health HTTP $c"

for d in $(seq 0 11); do
    out=$(curl -s -m 20 "$API/villages?mode=replay&day=$d" | python3 -c '
import json,sys
j=json.load(sys.stdin); v=j.get("villages",[]); b=j["summary"]["by_level"]
ok=len(v)==30 and all("level" in x["risk"] for x in v) and len(j.get("replay_days",[]))==12
print(("OK" if ok else "BAD"), j.get("date"), "villages=%d red=%d yellow=%d green=%d" % (len(v),b["red"],b["yellow"],b["green"]))
' 2>/dev/null || echo "BAD parse-error")
    [[ $out == OK* ]] && pass "replay day $d: ${out#OK }" || bad "replay day $d: $out"
done

for p in relief alerts; do
    c=$(code "$API/$p"); [ "$c" = 200 ] && pass "/$p 200" || bad "/$p HTTP $c"
done

acao=$(curl -s -m 20 -D - -o /dev/null -H "Origin: $WEB" "$API/villages?mode=replay&day=0" | tr -d '\r' | awk -F': ' 'tolower($1)=="access-control-allow-origin"{print $2}')
[ "$acao" = "$WEB" ] && pass "CORS allows $WEB" || bad "CORS allow-origin='$acao' (Railway ka CORS_ALLOWED_ORIGINS = $WEB hona chahiye)"

echo "== Dashboard ($WEB)"
page=$(curl -s -m 20 "$WEB/"); [[ $page == *JalRakshak* ]] && pass "/ serves dashboard" || bad "/ dashboard page nahi mila"
c=$(code "$WEB/some/client/route"); [ "$c" = 200 ] && pass "SPA rewrite (deep link 200)" || bad "SPA rewrite HTTP $c"
c=$(code "$WEB/fallback/replay.json"); [ "$c" = 200 ] && pass "bundled replay fallback" || bad "fallback/replay.json HTTP $c"
n=$(curl -s -m 20 "$WEB/fallback/sar/India_591317.json" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["geojson"]["features"]))' 2>/dev/null || echo 0)
[ "$n" -gt 0 ] && pass "SAR polygons baked ($n features, India_591317)" || bad "SAR fallback polygons missing"
# (Variable mein pakadte hain: `curl | grep -q` pipefail ke saath SIGPIPE se jhootha fail deta.)
js=$(curl -s -m 20 "$WEB/" | grep -o 'assets/index-[^"]*\.js' | head -1)
bundle=$(curl -s -m 20 "$WEB/$js")
[[ $bundle == *"${API%/api}"* ]] \
    && pass "bundle points at $API" || bad "bundle mein Railway URL nahi — Vercel ka VITE_API_URL set karke Redeploy karo"

echo; [ $fail = 0 ] && echo "ALL PASS" || { echo "KUCH FAIL HUA (upar dekho)"; exit 1; }
