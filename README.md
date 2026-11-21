# Campus Hub: backend + app, connected

```
campus-hub-fullstack/
  backend/   Spring Boot API (port 8080)
  app/       React app (port 5173, proxies /api to the backend)
  scripts/   smoke.mjs: one-command live check of the whole order flow
  docs/      API-MAP.md: every app call matched to its backend endpoint
```

## How they connect
- The app calls `/api/...`. In development Vite forwards that to the backend (`VITE_API_TARGET` in `app/.env`, default `http://localhost:8080`).
- Sign-in returns an access token (15 min) and a rotating refresh token; the app refreshes automatically and keeps one refresh in flight.
- Roles come from the token. The app hides screens by role, and the backend refuses anything outside the role anyway.
- Payments run through the backend's sandbox gateway while `VITE_SANDBOX=true`.

## Sign-up with an SMS code, and when owners can add products
1. **Everyone** registers with a mobile number. The backend texts a 6-digit code (valid 10 minutes, 5 wrong tries, one new code a minute, 5 an hour). Until it is entered the account cannot sign in and no token is issued.
2. **Shop owners** then create their shop. **Products, stock, prices and offers stay locked** (the API answers `SHOP_NOT_APPROVED`) until the admin approves the shop. Approval unlocks all of it automatically; the owner also gets a notification and the app re-checks every 15 seconds. Disabling a shop or suspending the owner locks it again.
3. **Development:** SMS is not really sent (`app.sms.provider: console`). Read the code in the backend console, in the line `SMS (development, NOT sent) to +91…`.
4. **Real SMS:** set `app.sms.provider: twilio` plus the `TWILIO_*` values (see `backend/.env.example`). In India carriers require a registered sender and message template (DLT), so an Indian provider such as MSG91 is usually easier; adding one means implementing the small `SmsSender` interface.
5. A phone number can belong to only one verified account. The number can't be changed from the profile screen yet (that would need its own verification).

## Run
1. **Backend:** create the MySQL database `campushub`, create `backend/src/main/resources/application-local.yml` (see `backend/.env.example` for every setting, including `app.admin.email` / `app.admin.password`), run with profile `local`. Needs JDK 21.
2. **App:** `cd app && npm install && copy .env.example .env && npm run typecheck && npm run dev`, then open http://localhost:5173.
3. **Prove the connection:** with the backend running,
   `ADMIN_EMAIL=you@example.com ADMIN_PASSWORD='your-password' node scripts/smoke.mjs`
   It registers an owner and a student, runs the full order (50% advance, READY, OTP, remaining payment, pickup, review) and prints one line per step.
   To also test the app's proxy, add `API=http://localhost:5173/api` (the app must be running).

## What has been checked (without running it)
- 91 distinct app calls, all matched to backend endpoints by method and path.
- Every field the app reads from a response exists in what the backend sends.
- All 281 SQL statements in the backend only use tables and columns that exist in the migrations.
Not yet checked here: actually running it, because the build sandbox has no internet. Use steps 1–3 above.
