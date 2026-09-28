# RECOVERY — API band ho gayi (Railway credit khatam). Ise naye account pe le jao.

> Ye tab padhna jab **"uptime" workflow fail** ki GitHub email aaye, ya
> `https://<railway-url>/api/health` khulna band ho jaaye.
> **Ghabrao mat:** dashboard (Vercel) ab bhi chal raha hai — judge ko replay 2022, map,
> charts aur Satellite tab bundled data se dikh rahe hain. Sirf Live mode, relief list,
> alert bhejna aur Android app ka data ruka hai. Ye 20-30 minute ka kaam hai.

Poora kaam: **naya Railway account -> same repo deploy -> 1 variable Vercel pe -> 1 variable GitHub pe.**
Vercel URL (jo SIH portal pe submit hua) **kabhi nahi badalta** — usse chhedna hi nahi.

---

## 0. Kya chahiye (5 min)

- Ek **naya email** (Railway ek email pe ek trial deta hai). Gmail ka `+` trick
  (`you+jr2@gmail.com`) Railway ke GitHub login ke saath kaam nahi karta — Railway GitHub
  account se login karta hai. Isliye do raaste:
  - **(a) Paisa de do:** purane account pe **Hobby plan ($5/mahina)** le lo. Kuch move nahi
    karna padega — service khud wapas uth jaayegi. **Sabse aasan, sabse bharosemand.**
  - **(b) Naya account:** naya GitHub account (naye email se) -> usse Railway login.
    Repo public hai, to naya GitHub account bhi use deploy kar sakta hai.
- Laptop pe ye repo (`APP_KEY` wahi rakhna hai — neeche step 2).

---

## 1. Railway project (terminal se, ~10 min)

```bash
cd ~/Desktop/"APPS DEVELOPMENT"/jalrakshak
railway logout && railway login          # browser mein NAYE account se approve
railway init --name jalrakshak           # naya project
railway add --database mysql             # MySQL service (naam "MySQL" hi rehne do)
railway add --service api --repo shubhamverma-devx/jalrakshak   # GitHub repo se app service
railway link                             # "api" service chuno
```

(CLI mein dikkat aaye to browser: railway.com -> New Project -> Deploy from GitHub repo ->
jalrakshak; phir canvas pe **+ Create -> Database -> MySQL**.)

## 2. Variables — `api` service pe

Sab ek command mein (`<...>` bharo):

```bash
railway variables --service api \
  --set "APP_NAME=JalRakshak" \
  --set "APP_ENV=production" \
  --set "APP_DEBUG=false" \
  --set "APP_KEY=<neeche dekho>" \
  --set 'APP_URL=https://${{RAILWAY_PUBLIC_DOMAIN}}' \
  --set "LOG_CHANNEL=stderr" \
  --set "LOG_LEVEL=warning" \
  --set "DB_CONNECTION=mysql" \
  --set 'DB_HOST=${{MySQL.MYSQLHOST}}' \
  --set 'DB_PORT=${{MySQL.MYSQLPORT}}' \
  --set 'DB_DATABASE=${{MySQL.MYSQLDATABASE}}' \
  --set 'DB_USERNAME=${{MySQL.MYSQLUSER}}' \
  --set 'DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}' \
  --set "CACHE_STORE=file" \
  --set "SESSION_DRIVER=file" \
  --set "QUEUE_CONNECTION=sync" \
  --set "CORS_ALLOWED_ORIGINS=<Vercel URL — neeche dekho>" \
  --set 'CORS_ALLOWED_ORIGIN_PATTERN=#^https://jalrakshak-[a-z0-9-]+\.vercel\.app$#' \
  --set "FIREBASE_CREDENTIALS_BASE64=$(base64 -i _secrets/firebase-admin.json | tr -d '\n')"
```

> **SECRETS ECHO HOTE HAIN:** `railway variables --set` aur `railway add -v` value ko screen pe
> print kar dete hain. `APP_KEY` aur Firebase key ke liye ye use karo (kuch print nahi hota):
> ```bash
> (cd backend && php artisan key:generate --show) | railway variable set APP_KEY --stdin --service api
> base64 -i _secrets/firebase-admin.json | tr -d '\n' | railway variable set FIREBASE_CREDENTIALS_BASE64 --stdin --service api
> ```
> Upar wale bade command se ye do lines hata do.

**Single quotes** (`'...'`) wali lines mein `${{ }}` Railway ka reference hai — shell use na
chhede isliye single quotes. Unhe jaisa hai waisa rehne do.

| variable | value kahan se |
|---|---|
| `APP_KEY` | **Purane Railway project se copy karo** (railway.com -> purana project -> api -> Variables -> APP_KEY -> copy). Band project ke variables bhi dikhte hain. Na mile to naya: `cd backend && php artisan key:generate --show`. Naya key bhi theek hai — koi encrypted data store nahi hota. |
| `CORS_ALLOWED_ORIGINS` | Vercel production URL — Vercel dashboard -> jalrakshak project -> Domains. **Bina `/` ke.** |
| `DB_*` | Kuch mat bharo — `${{MySQL...}}` Railway khud MySQL service se bharta hai |
| `FIREBASE_CREDENTIALS_BASE64` | `_secrets/firebase-admin.json` laptop pe hai (git mein nahi). Nahi mile: Firebase Console -> Project settings -> Service accounts -> Generate new private key. Ye optional hai — na ho to push band, baaki sab chalta hai. |

## 3. Deploy + URL

```bash
railway up --service api --detach        # ya: GitHub push se auto-deploy
railway domain --service api --port 8080 # naya public URL print hoga — NOTE KARO
curl -s https://<naya-railway-url>/api/health    # "status":"ok" aana chahiye (2-4 min lagte hain)
```

Logs: `railway logs --service api`. Ye lines dikhni chahiye:
`[start] scheduler loop started`, `[start] serving on :8080`.
Database naya hai to seed khud chalta hai (30 gaon, 12 din replay) — kuch nahi karna.

## 4. Vercel pe EK badlaav (dashboard ko naya API batao)

```bash
cd dashboard
vercel env rm VITE_API_URL production --yes
printf 'https://<naya-railway-url>/api' | vercel env add VITE_API_URL production
vercel --prod
```

**`vercel --prod` zaroori hai** — `VITE_API_URL` build ke waqt bundle mein jaata hai, sirf
variable badalne se kuch nahi hota. Vercel URL wahi rehta hai; SIH portal pe kuch nahi badalna.

## 5. Uptime monitor ko naya URL batao

```bash
gh variable set API_HEALTH_URL --body "https://<naya-railway-url>/api/health"
gh workflow run uptime.yml               # turant ek check
```
(UptimeRobot bhi lagaya tha to wahan monitor ka URL edit karo.)

## 6. Android app

APK mein purana Railway URL baked hai — **wo naye URL pe nahi jaayega**. Agar APK dobara
submit/share karna hai:
```bash
# app/app/src/main/java/com/blackbox/jalrakshak/core/Config.kt -> API_BASE_URL = "https://<naya-railway-url>/api/"
cd app && ./gradlew :app:assembleDebug
# APK: app/app/build/outputs/apk/debug/app-debug.apk
```
Portal pe pehle se submit hua APK nahi badal sakta — wo purane URL pe offline cache se hi
chalegi. Ye maan ke chalo.

## 7. Jaanch

```bash
deploy/verify.sh https://<vercel-url> https://<naya-railway-url>
```
`ALL PASS` = ho gaya.

---

### Kyun ye sab hota hai (yaad dilane ke liye)
Railway free: 30 din trial ($5), phir $1/mahina. Hamara API + MySQL ~$5/mahina khaata hai.
Isliye har ~3-4 hafte credit khatam. Pakka ilaaj: Hobby plan $5/mahina.
