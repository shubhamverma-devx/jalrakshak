# RECOVERY — API band ho gayi (Railway credit khatam). Ise naye account pe le jao.

> Ye tab padhna jab GitHub se **"uptime" workflow failed** ki email aaye, ya
> https://api-production-6f20.up.railway.app/api/health khulna band ho jaaye
> (Railway tab `{"message":"Application not found"}` 404 deta hai).
>
> **Ghabrao mat.** Dashboard https://jalrakshak-beta.vercel.app ab bhi chal raha hai —
> judge ko replay 2022 (12 din), map, charts aur Satellite tab bundled data se dikh rahe
> hain, upar patti ke saath "Server unreachable: showing the bundled copy". Sirf Live mode,
> relief list, alert bhejna aur Android app ka data ruka hai. Ye 20-30 minute ka kaam hai.

**Poora kaam:** naya Railway project -> `railway up` -> Vercel pe 1 variable + redeploy ->
GitHub pe 1 variable. **Vercel URL (SIH portal wala) kabhi nahi badalta.**

Ye sab 28 Sept 2026 ko asli mein chala ke likha hai — neeche ke commands wahi hain jo chale.

---

## 0. Faisla (2 min)

- **(a) Paisa do — sabse aasan:** purane Railway account pe **Hobby plan ($5/mahina)**.
  railway.com -> avatar -> **Account Settings -> Plans -> Hobby**. Phir laptop se
  `railway up --service api --detach` (step 3). Variables, database, URL — sab wahi rehte
  hain; Vercel/GitHub/APK kuch nahi badalna. **Step 3 ke baad seedha step 6 pe jao.**
- **(b) Naya free account:** Railway GitHub login use karta hai, isliye **naya GitHub
  account** (naye email se) chahiye. Repo public hai, naya account bhi deploy kar sakta hai.
  Naye account pe naya 30-din/$5 trial milta hai. Neeche ke saare steps.

---

## 1. Login + project (~5 min)

```bash
cd ~/Desktop/"APPS DEVELOPMENT"/jalrakshak
railway logout
railway login --browserless       # link + code print hoga -> browser mein NAYE account se approve
railway init --name jalrakshak
railway add --database mysql      # service ka naam "MySQL" hi rehne do
railway add --service api         # khaali service (code `railway up` se jaayega)
```

## 2. Variables (`api` service pe)

**Pehle secrets — `--stdin` se, taaki screen/log pe print na hon:**
```bash
(cd backend && php artisan key:generate --show) | railway variable set APP_KEY --stdin --service api --skip-deploys
base64 -i _secrets/firebase-admin.json | tr -d '\n' | railway variable set FIREBASE_CREDENTIALS_BASE64 --stdin --service api --skip-deploys
```
> **Kabhi `railway add -v KEY=secret` ya `--set KEY=secret` mat use karna** — CLI value ko
> screen pe print kar deta hai (28 Sept ko isi se Firebase key leak hui thi).

**Phir baaki (koi secret nahi) — ek command:**
```bash
railway variable set --service api --skip-deploys \
  "APP_NAME=JalRakshak" "APP_ENV=production" "APP_DEBUG=false" \
  'APP_URL=https://${{RAILWAY_PUBLIC_DOMAIN}}' "LOG_CHANNEL=stderr" "LOG_LEVEL=warning" \
  "DB_CONNECTION=mysql" \
  'DB_HOST=${{MySQL.MYSQLHOST}}' 'DB_PORT=${{MySQL.MYSQLPORT}}' \
  'DB_DATABASE=${{MySQL.MYSQLDATABASE}}' 'DB_USERNAME=${{MySQL.MYSQLUSER}}' \
  'DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}' \
  "CACHE_STORE=file" "SESSION_DRIVER=file" "QUEUE_CONNECTION=sync" \
  "CORS_ALLOWED_ORIGINS=https://jalrakshak-beta.vercel.app" \
  'CORS_ALLOWED_ORIGIN_PATTERN=#^https://jalrakshak-[a-z0-9]+-dev-x9\.vercel\.app$#' \
  "RAILWAY_DOCKERFILE_PATH=backend/Dockerfile"
```

| variable | kyun / kahan se |
|---|---|
| `APP_KEY` | laptop pe naya banta hai (upar). Naya key theek hai — koi encrypted data store nahi hota. |
| `FIREBASE_CREDENTIALS_BASE64` | `_secrets/firebase-admin.json` (laptop, git mein nahi). Na mile: Firebase Console -> Project settings -> Service accounts -> **Generate new private key**. Optional — na ho to push band, alert phir bhi save. |
| `DB_*` | `${{MySQL...}}` Railway khud bharta hai — **single quotes** mein hi likho, warna shell `$` kha jaata hai |
| `CORS_ALLOWED_ORIGINS` | Vercel ka production URL — badalta nahi |
| `CORS_ALLOWED_ORIGIN_PATTERN` | Vercel preview URLs (`jalrakshak-<hash>-dev-x9.vercel.app`) |
| **`RAILWAY_DOCKERFILE_PATH`** | **ZAROORI.** Iske bina Railway `railway.json` ka Dockerfile setting ignore karke "Railpack could not determine how to build" deta hai. |

## 3. Deploy (~4 min)

```bash
railway up --service api --detach          # repo root se chalao
railway domain --service api --port 8080   # naya URL print hoga — NOTE KARO
```
Status: `railway deployment list --service api` -> `SUCCESS` aane tak ruko (3-4 min).
Phir: `curl -s https://<naya-url>/api/health` -> `"status":"ok"`.

Database naya hai to migrate + seed khud chalta hai (30 gaon, 12 din replay). Kuch nahi karna.

**Jo 28 Sept ko hua aur uska ilaaj:**
| dikkat | ilaaj |
|---|---|
| `Railpack could not determine how to build` | `RAILWAY_DOCKERFILE_PATH` variable set nahi — step 2 |
| `failed to resolve source metadata for docker.io/...: i/o timeout` | Railway ka Docker Hub se network hichki — `railway up` dobara chalao |
| `symfony/... requires php >=8.4.1` | purana snapshot build hua. **`railway redeploy` mat use karo** — wo purana upload dobara banata hai. Hamesha `railway up`. |
| variable badla par asar nahi | Railway naye variables tab tak nahi lagata jab tak naya deploy na ho — `railway up` |

Logs: `railway logs --service api` — `[start] scheduler loop started` aur
`[start] serving on :8080` dikhna chahiye.

## 4. Vercel ko naya API batao (~2 min)

Laptop, **repo root se** (Vercel project ka Root Directory `dashboard` hai, aur link root pe hai):
```bash
cd ~/Desktop/"APPS DEVELOPMENT"/jalrakshak
vercel env rm VITE_API_URL production --yes
printf 'https://<naya-railway-url>/api' | vercel env add VITE_API_URL production
vercel deploy --prod --yes
```
**Redeploy zaroori hai** — `VITE_API_URL` build ke waqt bundle mein jaata hai.
Vercel URL https://jalrakshak-beta.vercel.app wahi rehta hai; SIH portal pe kuch nahi badalna.
(`.vercel/` folder na ho to pehle: `vercel link --yes --project jalrakshak`)

## 5. Uptime monitor ko naya URL

```bash
gh variable set API_HEALTH_URL --body "https://<naya-railway-url>/api/health"
gh workflow run uptime.yml && sleep 30 && gh run list --workflow uptime.yml --limit 1
```
`success` aana chahiye. (UptimeRobot bhi lagaya ho to wahan monitor ka URL edit karo.)

## 6. Jaanch

```bash
deploy/verify.sh https://jalrakshak-beta.vercel.app https://<naya-railway-url>
```
`ALL PASS` = ho gaya.

## 7. Android app (sirf agar naya APK chahiye)

Submit hua APK `https://api-production-6f20.up.railway.app/api/` pe baked hai — naye URL pe
**nahi** jaayega (wo offline cache se chalega). Naya APK chahiye to:
```bash
# app/app/src/main/java/com/blackbox/jalrakshak/core/Config.kt -> API_BASE_URL = "https://<naya-url>/api/"
cd app && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
# -> app/app/build/outputs/apk/debug/app-debug.apk
```

---

### Yaad dilane ke liye — ye sab kyun hota hai
Railway free: 30 din trial ($5), phir $1/mahina credit. Hamara API + MySQL ~$5/mahina
khaata hai, isliye har ~3-4 hafte credit khatam. Pakka ilaaj: Hobby plan $5/mahina.
