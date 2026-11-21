# Campus Hub app (React + TypeScript + Vite + Tailwind)

Mobile-first web app for students, shop owners and admins. Talks to the Spring Boot backend through the dev proxy (`/api`).

## Run
```
npm install
cp .env.example .env     # set VITE_API_TARGET if your backend isn't on 8080
npm run dev              # http://localhost:5173
npm run typecheck        # run this first; fix anything it reports
```
Start the backend first. With the sandbox payment gateway keep `VITE_SANDBOX=true`: payment screens show a "Simulate successful payment" button. Set it to `false` once real PhonePe / Google Pay is wired in (the app then redirects to the gateway's payment URL).

## Try the whole flow
1. Register a **shop owner** → create a shop (CampusDesk or Canteen).
2. Sign in as the **admin** (backend `ADMIN_EMAIL`) → approve the shop.
3. As the **admin**, open Overview → Categories and add a few for each shop type. As the owner, use **Catalog** (products, stock, printing prices), **Offers** and **Shop** (details, refund policy), then press **Open shop** on the Orders tab.
4. Register a **student** → pick a tab (Canteen / CampusDesk) → open the shop → add items (or upload a PDF and add a print job) → Cart → pay 50%.
5. Owner moves the order along and marks it READY. The student gets the OTP in Alerts, pays the remaining amount, and the owner enters the OTP.

## What's built
Login, register, forgot/reset password, email verify · role-based routes (student / owner / admin) · Canteen ⇄ CampusDesk tab · shop list + detail with offers and follow · print configurator with PDF upload · cart with server-calculated prices, offer code, platform fee and 50% advance · checkout · orders with status steps, cancel, OTP resend, review · notifications with unread badge · owner order queue (accept → … → READY, reject, OTP hand-over, open/close shop) · **owner Catalog** (add/edit/hide products and services, stock add/remove/set/damaged with history and low-stock level, printing rate card with double-sided rule and add-ons) · **owner Offers** (create, switch on/off, notify followers) · **owner Shop settings** (details, cancellation/refund policy) · admin shop approval, user suspend and categories · **owner Insights** (today's earnings, live queue, 7/30-day revenue, best sellers, popular print settings, busiest hours, low stock, payments, refunds) · **admin Money** (platform fee with live preview, system limits, per-shop settlements + CSV) · **admin Issues** (refunds with retry, stuck/failed payments, problem orders) · **admin Audit log** (filter + load more). Notifications moved to the bell in the header · **student Me page** (profile with photo, payment history, followed shops, favourites, reviews, notification preferences, saved print settings) · **live print price** from the server while choosing options · **save and reuse print settings** · **favourite products** (heart) · **order again** from the Shops page and from past orders.

## Not built yet
Saved addresses (not needed: every order is picked up at the shop) · push notifications · student profile, payments history, saved presets, favourites · live print price preview before adding to cart (the exact price shows in the cart) · push notifications (alerts refresh every 15 s).

## Mobile app
Wrap this build with Capacitor: `npm i @capacitor/core @capacitor/cli && npx cap init && npx cap add android`. Then add push notifications (Firebase) and store the refresh token in secure storage instead of localStorage.
