# Deploy — 1 GB droplet par hafton unattended

SIH portal ka link judge kabhi bhi (hafton baad) kholega. Ye folder usi ke liye hai:
site gire nahi, aur gire bhi to **pehli screen kabhi blank na ho**.

Target box: 1 vCPU / 1 GB RAM, Ubuntu 22.04, nginx + php8.3-fpm + MySQL, do aur Laravel
sites saath mein, Cloudflare aage.

| file | kahan jaati hai | kaam |
|---|---|---|
| `harden.sh` | droplet pe `sudo` se | swap, MySQL, php-fpm, systemd, watchdog, cron, nginx, certbot — sab ek baar |
| `server-update.sh` | droplet pe | git pull + composer + migrate + cache garam |
| `push-dashboard.sh` | **laptop** pe | fallback JSON bake + build + rsync |
| `mysql/zz-lowmem.cnf` | `/etc/mysql/mysql.conf.d/` | MySQL ~400 MB -> ~180 MB |
| `php/jalrakshak-pool.conf` | `/etc/php/8.3/fpm/pool.d/` | alag pool, RAM ki chhat |
| `nginx/jalrakshak.conf` | `/etc/nginx/sites-available/` | dashboard static, `/api` -> PHP |
| `systemd/*` | `/etc/systemd/system/` | Restart=on-failure + watchdog timer |
| `cron.d/jalrakshak` | `/etc/cron.d/` | scheduler, flock ke saath |

---

## Numbers (aur kyun)

### RAM budget (1 GB = ~960 MB usable)

| cheez | MB | note |
|---|---|---|
| kernel + systemd + sshd + journald + cron | ~130 | fixed |
| nginx (master + 1 worker) | ~15 | |
| **MySQL (tuned)** | **~170-200** | pehle ~350-450 |
| php-fpm masters | ~20 | |
| **PHP workers — sab sites** | **~500 max** | ~12 worker x ~40-45 MB |
| page cache / headroom | ~100 | isse kam hua to swap shuru |

### Swap — 2 GB, `swappiness=10`, `vfs_cache_pressure=50`
- **2 GB:** 1 GB RAM box pe 2x standard hai. Ye performance ke liye nahi, **OOM-killer se
  bachne** ke liye hai — bina swap ke RAM khatam hote hi kernel sabse bade process (MySQL)
  ko maar deta hai aur site chup-chaap 500 deti hai. Swap ke saath wo bas dheemi hoti hai.
- **swappiness 10** (default 60): kernel tabhi swap kare jab sach mein zaroorat ho. 60 pe
  wo idle MySQL pages ko jaldi disk pe phenk deta hai, aur hafton baad judge ki pehli
  query disk se aati hai.
- **vfs_cache_pressure 50**: file metadata cache zyada der rakho — nginx static files.

### MySQL — `performance_schema=OFF`, `innodb_buffer_pool_size=64M`, `max_connections=30`
- **performance_schema OFF:** ek line mein ~150-250 MB. Sirf diagnostics hai, yahan koi
  use nahi karta.
- **buffer pool 64M** (default 128M): JalRakshak ka poora DB <5 MB. `harden.sh` teeno
  sites ka asli InnoDB size print karta hai — 64 se bada nikla to badhana.
- **max_connections 30** (default 151): PHP workers sab mila ke ~12 + cron + shell. Har
  khula connection apne buffers leta hai; 151 ka matlab tha ki burst mein MySQL khud RAM
  kha jaata.
- **disable_log_bin:** koi replica nahi, aur binlog 30 din tak disk bharta rehta.

### php-fpm — JalRakshak pool: `ondemand`, `pm.max_children=4`, `memory_limit=96M`
- **Alag pool** taaki JalRakshak ki RAM ki pakki chhat ho: worst case 4 x ~45 = ~180 MB.
  Traffic aaye to bhi doosri sites ki memory nahi khaata, aur wo isko nahi gira sakti.
- **ondemand:** koi request nahi to 0 worker. Hafton idle rahega — tab 0 MB.
- **4:** dashboard nginx se static aata hai. PHP tak sirf API aati hai, aur wo cached
  hai (<50 ms). 4 worker = ~80 req/sec, demo ke liye kaafi se zyada.
- **memory_limit 96M** (Ubuntu default 128M): ek bigdi request poori RAM na khaaye.
- **pm.max_requests 300:** dheere badhti PHP memory hafton jama na ho.
- **Doosri sites ke pools `harden.sh` NAHI chhoota** — sirf report karta hai ki sab pools
  mila ke worst case kitna hai. Agar wo 550 MB se upar aaye to un sites mein bhi
  `pm = ondemand` + chhota `max_children` lagana padega. Ye aapka faisla hai kyunki wo
  live sites hain.

---

## Server gire to bhi dashboard kyun chalta hai

1. Dashboard **replay 2022 mode** mein khulta hai — Open-Meteo ki zaroorat nahi.
2. `dashboard/public/fallback/*.json` build ka hissa hai; nginx seedha disk se deta hai.
   API 8 sec mein jawaab na de (ya 500 de) to replay isi se chalta hai, aur upar patti
   saaf likhti hai: *"Server unreachable: showing the bundled copy…"*.
3. **Satellite tab kabhi API nahi bulata.** `ml/predict.py` laptop pe chaaron scenes pe
   chalta hai (`php artisan dashboard:export-fallback`), output JSON mein baked. Droplet
   PyTorch load nahi kar sakta (~400 MB+). UI pe likha hai ki result pre-computed hai.
4. Live mode fail ho (server ya Open-Meteo) to saaf message + "Open Replay 2022" button.
   Live ka koi fake fallback NAHI — purani barish ko "live" dikhana jhooth hota.
5. Relief/alerts live data hain — unka koi baked copy nahi; panel likhta hai "needs the server".

**Na chalne wali cheezein (1 GB pe):** B2 forecast strip (drawer) PyTorch maangti hai —
droplet pe wo chup-chaap chhup jaati hai (503). SAR "live inference" bhi nahi. Dono ka
code aur notebooks repo mein hain.

---

## Pehli baar

```bash
# --- droplet (<ssh-user>) ---
sudo git clone https://github.com/shubhamverma-devx/jalrakshak.git /var/www/jalrakshak
sudo chown -R <ssh-user>:www-data /var/www/jalrakshak
sudo chown -R www-data:www-data /var/www/jalrakshak/backend/storage /var/www/jalrakshak/backend/bootstrap/cache
cp /var/www/jalrakshak/backend/.env.example /var/www/jalrakshak/backend/.env   # neeche wale keys bharo
sudo mysql -e "CREATE DATABASE jalrakshak; CREATE USER 'jalrakshak'@'localhost' IDENTIFIED BY '<pw>'; GRANT ALL ON jalrakshak.* TO 'jalrakshak'@'localhost';"
cd /var/www/jalrakshak/backend && sudo -u www-data php artisan key:generate
bash /var/www/jalrakshak/deploy/server-update.sh --first
sudo bash /var/www/jalrakshak/deploy/harden.sh <domain>
sudo certbot --nginx -d <domain>

# --- laptop ---
deploy/push-dashboard.sh <domain> <ssh-user>@<droplet-ip>
```

### `backend/.env` — production keys jo badalne hain
```
APP_ENV=production
APP_DEBUG=false                 # true = error pe stack trace + .env values judge ko dikhte
APP_URL=https://<domain>
LOG_STACK=daily                 # single = ek file hafton badhti rehti
LOG_DAILY_DAYS=7
LOG_LEVEL=warning
CACHE_STORE=file                # database NAHI — MySQL gire to cache bhi gire
DB_DATABASE=jalrakshak
DB_USERNAME=jalrakshak
DB_PASSWORD=<pw>
ML_PYTHON=/nonexistent          # droplet pe torch nahi; SAR/forecast turant saaf 503 dein
FIREBASE_CREDENTIALS=...        # _secrets/firebase-admin.json scp karo, commit kabhi nahi
```

**Cloudflare:** SSL/TLS mode **Full (strict)**. Bot Fight Mode ON ho to uptime monitor
ko challenge mil sakta hai — Security > WAF mein monitor ke user-agent ko skip rule do.

---

## Uptime monitor (free) — UptimeRobot

1. uptimerobot.com pe free account (50 monitors, 5-min interval, email alert).
2. **Add New Monitor** -> type **HTTP(s)** -> URL `https://<domain>/api/health` -> 5 min.
   `/api/health` 200 deta hai jab PHP + MySQL theek hain, **503 jab MySQL gira** ho —
   dashboard tab bhi fallback se chalta hai, par aapko email aa jaayegi.
3. Doosra monitor: **Keyword** type, URL `https://<domain>/`, keyword `JalRakshak` —
   dashboard ka static page (nginx) zinda hai ya nahi.
4. Alert contact mein apna email. (Better Stack ka free tier bhi yahi karta hai.)

Health response:
```json
{"status":"ok","checks":{"database":"ok","scheduler":"ok","live_risk_generated_at":"..."}}
```
`scheduler: stale` = 40 min se `risk:compute` ne live map nahi likha (cron ya Open-Meteo).
Ye 503 nahi deta — Open-Meteo ka ghanta bhar down rehna site down nahi hai.

---

## Roz ki jaanch (ek line)
```bash
free -m; systemctl is-active nginx php8.3-fpm mysql jr-watchdog.timer; journalctl -t jr-watchdog --since today --no-pager | tail
```
