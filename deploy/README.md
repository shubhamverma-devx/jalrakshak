# Deploy — Railway (API + MySQL) + Vercel (dashboard)

```
 judge ka browser ──► Vercel: React dashboard (static) ──► Railway: Laravel API ──► Railway: MySQL
                         │
                         └─ public/fallback/*.json  (API gira ho to bhi replay + satellite chalte hain)
```

| file | kaam |
|---|---|
| `railway.json` (repo root) | Railway ko batata hai: `backend/Dockerfile` se build, `/api/health` healthcheck, crash pe restart |
| `backend/Dockerfile` | PHP 8.4 + FrankenPHP; context = repo root (seeders ko `data/` chahiye) |
| `backend/docker/start.sh` | har deploy pe: migrate -> seed (idempotent) -> config/route cache -> scheduler loop -> server |
| `.dockerignore` | secrets, weights, node_modules image se bahar |
| `dashboard/vercel.json` | build, `dist`, SPA rewrite, cache headers |
| `deploy/verify.sh` | deploy ke baad public URLs ki poori jaanch |

---

## Pehle ye padho — free tier pe kya NAHI chalega (seedha sach)

1. **Railway free tier hafton tak 24x7 nahi chalega.** Naya account = **30 din ka trial,
   $5 credit**. Uske baad **Free plan = $1/mahina credit, 0.5 GB RAM per service**.
   Railway RAM ka $10/GB/mahina leta hai. Hamara kharcha lagbhag:
   - Laravel container ~120-150 MB => ~$1.2-1.5/mahina
   - MySQL ~300-400 MB => ~$3-4/mahina
   - **Kul ~$4.5-5.5/mahina.** Trial ka $5 ~3-4 hafte chalega. Uske baad $1 credit mein
     ye ek hafta bhi nahi chalega — Railway services rok dega.
   - **Agar judges ko hafton baad bhi live API chahiye: Hobby plan ($5/mahina, usme $5
     usage included)** — ye ek hi raasta hai jo Railway pe pakka kaam karta hai.
2. **Railway band ho jaaye to kya bachta hai:** Vercel free hamesha chalega. Dashboard 8 sec
   mein API chhod ke bundled copy pe aa jaata hai — **replay ke 12 din, map, charts,
   Satellite tab ke polygons sab chalte hain**, upar patti likhti hai "Server unreachable:
   showing the bundled copy". **Jo band ho jaata hai:** Live mode, relief/SOS list, alert
   bhejna, citizen app ka data. Yani judge ko blank screen kabhi nahi dikhegi, par "live"
   hissa nahi dikhega.
3. **Cron:** Railway free pe alag cron service nahi hai, isliye scheduler **isi container
   mein** chalta hai (`start.sh` ka background loop, har minute `schedule:run`). Ye free
   tier pe kaam karta hai. Seema: container restart/redeploy ke waqt loop bhi restart
   hota hai (koi nuksaan nahi — start.sh deploy pe ek live compute khud karta hai).
   **"App Sleeping" (serverless) ON mat karna** — so gaya to scheduler bhi so jaayega aur
   live risk purana ho jaayega.
4. **B2 forecast strip (drawer)** — PyTorch chahiye, image mein nahi hai. Endpoint turant
   503 deta hai aur drawer use chup-chaap chhupa leta hai. SAR pe koi asar nahi (baked hai).
5. **Push notifications** tabhi jab `FIREBASE_CREDENTIALS_BASE64` set ho. Na ho to alert
   phir bhi save hota hai aur response mein `push.error` saaf wajah deta hai.

---

## Step 1 — Railway (browser)

**A. Project + repo**
1. https://railway.com -> **Login** -> **Login with GitHub**.
2. Dashboard pe **New Project** -> **Deploy from GitHub repo**.
3. Pehli baar: **Configure GitHub App** -> `shubhamverma-devx/jalrakshak` ko access do -> wapas aao.
4. List mein **jalrakshak** chuno. Railway ek service banata hai aur build shuru karta hai.
   **Pehla deploy FAIL hoga** ("APP_KEY set nahi hai") — ye expected hai, variables abhi daale nahi.
5. Service pe click -> **Settings** tab:
   - **Source -> Root Directory:** khaali chhodo (`/`). `railway.json` root pe hai, wahi Dockerfile batata hai.
   - **Config-as-code** section mein `railway.json` dikhna chahiye.
   - **Branch:** `main` (auto-deploy on push by default ON).

**B. MySQL**
6. Project canvas pe **+ Create** (upar-daayein, ya canvas pe right-click) -> **Database** -> **MySQL**.
   Service ka naam **`MySQL`** hi rehne do — neeche ke variables isi naam se refer karte hain.
7. (Optional, RAM/kharcha ~40% kam) MySQL service -> **Settings -> Deploy -> Custom Start Command**.
   Pehle dekho wahan kya likha hai; agar khaali hai to ye daalo:
   ```
   docker-entrypoint.sh mysqld --performance-schema=OFF --innodb-buffer-pool-size=32M --max-connections=20 --disable-log-bin
   ```
   Deploy ke baad MySQL ke logs mein "ready for connections" dikhna chahiye. Na dikhe to field khaali karke redeploy.

**C. Variables**
8. App service (jalrakshak) -> **Variables** tab -> **Raw Editor** -> neeche ka block paste
   -> `<...>` wali cheezein bharo -> **Update Variables**.

```
APP_NAME=JalRakshak
APP_ENV=production
APP_DEBUG=false
APP_KEY=<laptop pe: cd backend && php artisan key:generate --show   — poori line "base64:..." copy>
APP_URL=https://${{RAILWAY_PUBLIC_DOMAIN}}
LOG_CHANNEL=stderr
LOG_LEVEL=warning
DB_CONNECTION=mysql
DB_HOST=${{MySQL.MYSQLHOST}}
DB_PORT=${{MySQL.MYSQLPORT}}
DB_DATABASE=${{MySQL.MYSQLDATABASE}}
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
CACHE_STORE=file
SESSION_DRIVER=file
QUEUE_CONNECTION=sync
CORS_ALLOWED_ORIGINS=<Step 2 ke baad: https://<project>.vercel.app  — abhi http://localhost:5173 daal do>
CORS_ALLOWED_ORIGIN_PATTERN=#^https://jalrakshak-[a-z0-9-]+\.vercel\.app$#
FIREBASE_CREDENTIALS_BASE64=<optional — laptop pe: base64 -i _secrets/firebase-admin.json | tr -d '\n'>
```

   | variable | kahan se |
   |---|---|
   | `APP_KEY` | laptop pe command (upar). Ek baar banao, kabhi mat badlo. Kisi ko mat bhejo. |
   | `DB_*` | `${{MySQL.…}}` Railway khud MySQL service se bharta hai — jaisa likha hai waisa paste |
   | `APP_URL` | `${{RAILWAY_PUBLIC_DOMAIN}}` Railway khud bharta hai (step 9 ke baad) |
   | `CORS_ALLOWED_ORIGINS` | Vercel ka production URL, **bina trailing slash** |
   | `CORS_ALLOWED_ORIGIN_PATTERN` | Vercel preview URLs ke liye; project ka naam `jalrakshak` na ho to regex mein badlo |
   | `FIREBASE_CREDENTIALS_BASE64` | `_secrets/firebase-admin.json` ka base64. Railway variables encrypted rehte hain; repo mein kabhi nahi |

   `APP_ENV`/`APP_DEBUG`/`config:cache`/`route:cache` — Dockerfile + `start.sh` pehle se karte hain.

**D. Public URL**
9. App service -> **Settings -> Networking -> Public Networking -> Generate Domain**.
   Port poochhe to **8080** (container `$PORT` pe sunta hai, Railway khud set karta hai).
   Mila URL jaise `https://jalrakshak-production-xxxx.up.railway.app` — **ise note karo**.
10. **Deployments** tab -> latest deploy -> **View logs**. Ye lines dikhni chahiye:
    `[start] scheduler loop started` aur `[start] serving on :8080`.
    Browser mein `https://<railway-url>/api/health` -> `"status":"ok"`.

## Step 2 — Vercel (browser)

1. https://vercel.com -> **Sign Up / Log in** -> **Continue with GitHub**.
2. **Add New… -> Project** -> `jalrakshak` repo ke saamne **Import**
   (na dikhe to **Adjust GitHub App Permissions** -> repo ko access do).
3. **Configure Project** screen:
   - **Project Name:** `jalrakshak` (URL `jalrakshak.vercel.app` banega; naam le liya gaya ho to jo mile)
   - **Framework Preset:** Vite
   - **Root Directory:** **Edit** -> `dashboard` chuno -> Continue
   - **Build & Output Settings:** kuch mat chhedo — `dashboard/vercel.json` batata hai
     (`npm run build`, output `dist`)
   - **Environment Variables:** Key `VITE_API_URL`, Value `https://<railway-url>/api` -> **Add**
4. **Deploy**. ~1 min. Mila URL (jaise `https://jalrakshak.vercel.app`) note karo.
5. **Wapas Railway** -> app service -> Variables -> `CORS_ALLOWED_ORIGINS` = wahi Vercel URL
   (bina `/` ke) -> Update. Railway khud redeploy karega (~2-3 min).

> `VITE_API_URL` build ke waqt bundle mein jaata hai. Baad mein badlo to Vercel ->
> **Deployments** -> latest -> **⋯** -> **Redeploy** zaroori hai.

## Step 3 — Jaanch

```bash
deploy/verify.sh https://<project>.vercel.app https://<railway-url>
```
API health, replay ke 12 din (30 gaon + risk), relief/alerts, CORS, SPA rewrite, bundled SAR
polygons — sab ek saath. `ALL PASS` aana chahiye.

**Free uptime monitor:** uptimerobot.com -> Add New Monitor -> HTTP(s) ->
`https://<railway-url>/api/health` -> 5 min. 200 = theek, 503 = MySQL gira hua,
koi jawaab nahi = Railway service band (credit khatam?). Doosra monitor Vercel URL pe.

## Kuch badla to

| kya badla | kya karna |
|---|---|
| backend code / data / seeders | `git push` — Railway khud deploy (migrate + seed idempotent hai) |
| dashboard code | `git push` — Vercel khud deploy |
| replay data, RiskEngine rules, SAR model | laptop: `php artisan risk:compute --mode=replay --all && php artisan dashboard:export-fallback`, phir commit + push (fallback JSON taaza) |
