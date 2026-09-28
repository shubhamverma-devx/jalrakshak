#!/usr/bin/env bash
# =============================================================================
#  harden.sh — 1GB droplet ko hafton unattended chalne layak banao
#  USAGE (droplet pe, repo /var/www/jalrakshak mein):
#      sudo bash /var/www/jalrakshak/deploy/harden.sh jalrakshak.example.com
# =============================================================================
#  KYA KARTA HAI (har step idempotent — dobara chalao to kuch nahi bigadta):
#    1. 2 GB swap + swappiness 10              -> MySQL OOM-kill se bachao
#    2. journald 100 MB cap                    -> hafton mein disk na bhare
#    3. MySQL low-memory config                -> ~400 MB se ~180 MB
#    4. php-fpm: JalRakshak ka alag pool        -> RAM ki pakki chhat
#    5. systemd Restart=on-failure (3 services) -> crash/OOM ke 5 sec baad khud uthe
#    6. jr-watchdog.timer (har 2 min)           -> is-active + asli HTTP check
#    7. Laravel scheduler cron (flock)          -> risk:compute dher na lage
#    8. nginx site (sirf agar pehle se nahi)    -> dashboard static, /api -> PHP
#    9. certbot timer check + renew --dry-run
#
#  SAFETY: har badli file ka backup /root/jr-backup-<time>/ mein. Har config restart se
#  PEHLE validate hoti hai (nginx -t, php-fpm -t, mysqld --validate-config). MySQL naye
#  config se na uthe to turant purana wapas.
#  DOOSRI SITES: unke nginx/php pool ko ye script CHHOOTI NAHI — sirf padh kar report
#  karti hai (step 4).
# =============================================================================
set -euo pipefail

DOMAIN="${1:?usage: sudo bash harden.sh <domain>}"
REPO=/var/www/jalrakshak
HERE="$REPO/deploy"
BACKUP="/root/jr-backup-$(date +%Y%m%d-%H%M%S)"
PHPV=8.3

[ "$(id -u)" -eq 0 ] || { echo "sudo se chalao"; exit 1; }
[ -d "$HERE" ] || { echo "$HERE nahi mila — pehle repo clone karo"; exit 1; }
mkdir -p "$BACKUP"

say()  { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }
ok()   { printf '   \033[32mok\033[0m %s\n' "$*"; }
warn() { printf '   \033[33m!!\033[0m %s\n' "$*"; }
backup() { [ -e "$1" ] && cp -a "$1" "$BACKUP/" || true; }

say "0. Memory BEFORE"
free -m | tee "$BACKUP/free-before.txt"
[ -f /root/jr-free-first-run.txt ] || cp "$BACKUP/free-before.txt" /root/jr-free-first-run.txt
ps -eo rss,comm --sort=-rss | awk 'NR==1{print "   RSS(MB) PROCESS"; next} NR<=12{printf "   %7.0f %s\n",$1/1024,$2}'

# ---------------------------------------------------------------------------------
say "1. Swap 2 GB + swappiness"
if swapon --show | grep -q .; then
    ok "swap already on: $(swapon --show --noheadings | awk '{print $1, $3}')"
else
    avail_gb=$(df --output=avail -BG / | tail -1 | tr -dc 0-9)
    [ "$avail_gb" -ge 5 ] || { warn "disk pe sirf ${avail_gb}G khaali — swap skip. Pehle disk saaf karo."; exit 1; }
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
    swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || { backup /etc/fstab; echo '/swapfile none swap sw 0 0' >> /etc/fstab; }
    ok "2G /swapfile on + fstab"
fi
# swappiness 10: swap sirf aakhri raasta (RAM sach mein khatam ho tab). 60 (default) pe
# kernel idle MySQL pages ko jaldi swap mein phenk deta — har query disk se, site dheemi.
# vfs_cache_pressure 50: file metadata cache thoda zyada rakho (nginx static files).
cat > /etc/sysctl.d/99-jalrakshak.conf <<'EOF'
vm.swappiness = 10
vm.vfs_cache_pressure = 50
EOF
sysctl -q --system
ok "swappiness=$(cat /proc/sys/vm/swappiness) vfs_cache_pressure=$(cat /proc/sys/vm/vfs_cache_pressure)"

# ---------------------------------------------------------------------------------
say "2. journald cap"
mkdir -p /etc/systemd/journald.conf.d
printf '[Journal]\nSystemMaxUse=100M\n' > /etc/systemd/journald.conf.d/jalrakshak.conf
systemctl restart systemd-journald
ok "journal max 100M"

# ---------------------------------------------------------------------------------
say "3. MySQL low-memory"
if mysqld --version 2>/dev/null | grep -qi mariadb; then
    CNF_DIR=/etc/mysql/mariadb.conf.d; IS_MARIA=1
else
    CNF_DIR=/etc/mysql/mysql.conf.d; IS_MARIA=0
fi
data_mb=$(mysql -N -e "SELECT ROUND(SUM(data_length+index_length)/1048576) FROM information_schema.tables WHERE engine='InnoDB'" 2>/dev/null || echo "?")
echo "   InnoDB data (sab sites mila ke): ${data_mb} MB  (buffer pool 64M hoga)"
if [ "$data_mb" != "?" ] && [ "$data_mb" -gt 64 ]; then
    warn "data 64 MB se bada — buffer pool ${data_mb}M tak badhana behtar. Abhi 64M hi laga rahe hain."
fi
CNF="$CNF_DIR/zz-lowmem.cnf"
had_cnf=0; [ -f "$CNF" ] && { had_cnf=1; backup "$CNF"; }
cp "$HERE/mysql/zz-lowmem.cnf" "$CNF"
[ "$IS_MARIA" -eq 1 ] && sed -i '/^disable_log_bin/d' "$CNF"
if [ "$IS_MARIA" -eq 0 ] && ! mysqld --validate-config 2>"$BACKUP/mysqld-validate.txt"; then
    warn "MySQL config invalid — wapas le rahe hain:"; cat "$BACKUP/mysqld-validate.txt"
    if [ "$had_cnf" -eq 1 ]; then cp -a "$BACKUP/zz-lowmem.cnf" "$CNF"; else rm -f "$CNF"; fi
else
    systemctl restart mysql
    sleep 3
    if systemctl is-active --quiet mysql; then
        ok "mysql restarted: pool=$(mysql -N -e 'SELECT @@innodb_buffer_pool_size/1048576')M max_conn=$(mysql -N -e 'SELECT @@max_connections') perf_schema=$(mysql -N -e 'SELECT @@performance_schema')"
    else
        warn "MySQL naye config se nahi utha — ROLLBACK"
        if [ "$had_cnf" -eq 1 ]; then cp -a "$BACKUP/zz-lowmem.cnf" "$CNF"; else rm -f "$CNF"; fi
        systemctl restart mysql
        journalctl -u mysql -n 20 --no-pager
        exit 1
    fi
fi

# ---------------------------------------------------------------------------------
say "4. php-fpm pool (JalRakshak alag)"
POOL=/etc/php/$PHPV/fpm/pool.d/jalrakshak.conf
had_pool=0; [ -f "$POOL" ] && { had_pool=1; backup "$POOL"; }
cp "$HERE/php/jalrakshak-pool.conf" "$POOL"
if ! php-fpm$PHPV -t 2>"$BACKUP/fpm-test.txt"; then
    warn "php-fpm config invalid — wapas le rahe hain:"; cat "$BACKUP/fpm-test.txt"
    if [ "$had_pool" -eq 1 ]; then cp -a "$BACKUP/jalrakshak.conf" "$POOL"; else rm -f "$POOL"; fi
    exit 1
fi
systemctl reload php$PHPV-fpm
ok "pool 'jalrakshak' (ondemand, max_children 4, memory_limit 96M)"
# Doosri sites ke pools — sirf report, badlaav NAHI (wo aapki doosri live sites hain).
echo "   Baaki pools (unchanged):"
total=4
for f in /etc/php/$PHPV/fpm/pool.d/*.conf; do
    [ "$f" = "$POOL" ] && continue
    name=$(grep -m1 -oP '^\[\K[^\]]+' "$f" || echo "?")
    pm=$(grep -m1 -oP '^\s*pm\s*=\s*\K\S+' "$f" || echo "dynamic")
    mc=$(grep -m1 -oP '^\s*pm\.max_children\s*=\s*\K\d+' "$f" || echo "5")
    total=$((total + mc))
    printf '     %-12s pm=%-9s max_children=%s  (%s)\n' "$name" "$pm" "$mc" "$f"
done
worker_mb=$(ps -C php-fpm$PHPV -o rss= | sort -n | tail -n +2 | awk '{s+=$1;n++} END{ if(n) printf "%d", s/n/1024; else print 45}')
echo "   Sab pools ka max_children jod: $total  x ~${worker_mb} MB/worker = ~$((total * worker_mb)) MB worst case"
if [ $((total * worker_mb)) -gt 550 ]; then
    warn "Worst case 550 MB se zyada — doosri sites ke pool mein pm=ondemand + max_children kam karna padega (deploy/README.md). Ye script unhe khud nahi chhooti."
fi

# ---------------------------------------------------------------------------------
say "5. systemd Restart=on-failure (nginx, php-fpm, mysql)"
for svc in nginx php$PHPV-fpm mysql; do
    mkdir -p "/etc/systemd/system/$svc.service.d"
    cp "$HERE/systemd/restart-on-failure.conf" "/etc/systemd/system/$svc.service.d/restart.conf"
done
systemctl daemon-reload
for svc in nginx php$PHPV-fpm mysql; do
    ok "$svc: Restart=$(systemctl show -p Restart --value $svc) enabled=$(systemctl is-enabled $svc)"
done

# ---------------------------------------------------------------------------------
say "6. Watchdog timer (har 2 min)"
install -m 755 "$HERE/bin/jr-watchdog.sh" /usr/local/bin/jr-watchdog.sh
cp "$HERE/systemd/jr-watchdog.service" "$HERE/systemd/jr-watchdog.timer" /etc/systemd/system/
echo "$DOMAIN" > /etc/jr-watchdog.domain
systemctl daemon-reload
systemctl enable --now jr-watchdog.timer >/dev/null
ok "$(systemctl list-timers jr-watchdog.timer --no-pager | sed -n 2p)"

# ---------------------------------------------------------------------------------
say "7. Scheduler cron"
install -m 644 "$HERE/cron.d/jalrakshak" /etc/cron.d/jalrakshak
# Purani crontab entry (SETUP.md wali, bina flock) ho to double chalega — sirf batao.
if crontab -l -u www-data 2>/dev/null | grep -q 'jalrakshak.*schedule:run'; then
    warn "www-data ki crontab mein bhi schedule:run hai — use hatao: sudo crontab -e -u www-data"
fi
ok "/etc/cron.d/jalrakshak"

# ---------------------------------------------------------------------------------
say "8. nginx site"
SITE=/etc/nginx/sites-available/jalrakshak
if [ -f "$SITE" ]; then
    warn "$SITE pehle se hai — chhua nahi (certbot ke badlaav na mitein). Farq dekhna ho to:"
    echo "     diff <(sed 's/__DOMAIN__/$DOMAIN/' $HERE/nginx/jalrakshak.conf) $SITE"
else
    sed "s/__DOMAIN__/$DOMAIN/g" "$HERE/nginx/jalrakshak.conf" > "$SITE"
    ln -sf "$SITE" /etc/nginx/sites-enabled/jalrakshak
    ok "installed $SITE — HTTPS ke liye: sudo certbot --nginx -d $DOMAIN"
fi
if ! nginx -t 2>"$BACKUP/nginx-test.txt"; then
    warn "nginx config invalid — reload NAHI kiya (chalti site safe hai):"; cat "$BACKUP/nginx-test.txt"
    exit 1
fi
systemctl reload nginx

# ---------------------------------------------------------------------------------
say "9. certbot auto-renewal"
if command -v certbot >/dev/null; then
    systemctl list-timers --all --no-pager | grep -E 'certbot|snap.certbot' || warn "certbot ka koi timer nahi mila!"
    certbot certificates 2>/dev/null | grep -E 'Certificate Name|Domains|Expiry' || true
    certbot renew --dry-run 2>&1 | tail -4
else
    warn "certbot installed nahi"
fi

# ---------------------------------------------------------------------------------
say "10. Memory AFTER"
free -m | tee "$BACKUP/free-after.txt"
ps -eo rss,comm --sort=-rss | awk 'NR==1{print "   RSS(MB) PROCESS"; next} NR<=12{printf "   %7.0f %s\n",$1/1024,$2}'
echo
echo "Backups: $BACKUP"
