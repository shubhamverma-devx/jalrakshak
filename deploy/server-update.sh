#!/usr/bin/env bash
# =============================================================================
#  server-update.sh — DROPLET pe: backend code update (git pull + composer + cache)
#  USAGE (<ssh-user> se, sudo nahi): bash /var/www/jalrakshak/deploy/server-update.sh [--first]
#         --first = pehli baar: migrate --seed bhi
#
#  KYUN artisan `sudo -u www-data` se: Laravel ka daily log file jo pehle banaye wahi
#  malik. Agar <ssh-user> ne aaj ka log banaya to php-fpm (www-data) usme likh nahi paata
#  aur HAR request 500 deti hai — sabse common "kal tak chal raha tha" bug. Isliye
#  storage/ mein sirf www-data likhta hai. Code (git/composer) <ssh-user> ka.
# =============================================================================
set -euo pipefail
cd /var/www/jalrakshak
git pull --ff-only
cd backend
art() { sudo -u www-data php artisan "$@"; }
# COMPOSER_MEMORY_LIMIT: composer 1GB pe apni RAM ke liye swap pe girta hai — theek hai,
# ek baar ka kaam hai. --no-dev: tests/tinker ke package server pe nahi chahiye.
COMPOSER_MEMORY_LIMIT=-1 composer install --no-dev --optimize-autoloader --no-interaction
if [ "${1:-}" = "--first" ]; then art migrate --force --seed; else art migrate --force; fi
art config:cache
art route:cache
# Replay cache garam + live ek baar — pehla visitor kabhi thanda cache na dekhe.
art risk:compute --mode=replay --all
art risk:compute || echo "live compute fail (Open-Meteo?) — scheduler 30 min mein phir koshish karega"
echo "ok — health: curl -s https://\$(cat /etc/jr-watchdog.domain)/api/health"
