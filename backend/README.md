# Campus Hub backend (Canteen + CampusDesk)

Java 21 · Spring Boot 3.3 · MySQL 8 · Flyway · JWT. One backend, two verticals (`shops.vertical` = `FOOD` | `DESK`).

## Run
1. `cp .env.example .env`, fill it in, then `export $(grep -v '^#' .env | xargs)`
2. `docker compose up -d mysql`  (runs MySQL in UTC; keep it that way)
3. `mvn spring-boot:run` → Swagger at http://localhost:8080/swagger-ui.html
4. `mvn test` (unit tests) · the integration test needs Docker (Testcontainers)

First admin: set `ADMIN_EMAIL` / `ADMIN_PASSWORD` (12+ chars); it is created at startup. There is no public way to make admins.

## Modules
| Area | Endpoints (prefix) |
|---|---|
| Auth | `/api/auth` register, login, refresh (rotating, reuse detection), logout, forgot/reset password, verify email |
| Browse | `/api/shops`, `/api/shops/{id}/products`, `/printing-prices`, `/reviews`, `/api/categories` |
| Owner | `/api/shop-owner` shop, cancellation-policy, products, inventory, printing-prices, discounts, announcements, orders, refunds, payments, dashboard, analytics |
| Student | `/api/student` cart, files, orders (checkout, pay, cancel, otp), payments, refunds, follow, notification-preferences, review |
| Shared | `/api/notifications` (+ SSE `/stream`), `/api/files/{id}/download-url` |
| Admin | `/api/admin` users, shops, orders, payments, refunds, categories, settings, audit-logs, reviews, analytics |
| Payments | `POST /api/payments/webhook/{provider}` (HMAC signed) · dev only: `/api/payments/sandbox/{txn}/succeed` |

## Rules enforced on the server
- Prices, discounts, totals and the 50% advance are always recomputed from DB rows. The client sends ids and quantities only.
- Order status changes go through one state machine and write `order_status_history`.
- Stock is deducted once, only after the advance is verified (event-id table + row locks + unique key on `inventory_transactions`).
- Pickup OTP: created at READY, HMAC-hashed, 15 min, 3 attempts (attempts survive failed requests), single use. Collect needs READY + remaining payment SUCCESS + valid OTP.
- Files: private bucket, PDF only (magic bytes, no JS/attachments, not encrypted, ≤10 MB, ≤500 pages), signed URLs (5 min), owner-or-active-order access, retention job.
- Rate limits: 10/min on auth endpoints, 10/min OTP verify, 5/min OTP resend, 20/min uploads, 180/min general (in-memory; use Redis if you run more than one instance).

## Platform fee
A fee charged to the student on top of the discounted item total. It is **off by default** (0%). Admins set it with `PUT /api/admin/settings/{key}`:
`platform_fee_percent` (0–20), `platform_fee_min` (₹0–100), `platform_fee_max` (₹0–500, 0 = no cap).
- Quote and order show `subtotal`, `discount`, `platformFee`, `total`. The 50% advance is taken on the full total, fee included.
- The fee is saved on the order when it is created, so later setting changes never alter existing orders.
- Shop earnings = total − fee. Owner revenue/analytics already exclude the fee; `GET /api/admin/analytics` shows fees earned (collected orders) and in progress; `GET /api/admin/settlements?from=&to=` gives per-shop gross, fees and payout owed.
- Refunds follow the shop's cancellation policy on the **total paid, fee included**. Full refunds (shop rejects, item out of stock) return the fee too. If you'd rather keep the fee on student cancellations, say so and it's a small change.
- Not handled: tax (e.g. GST) on the fee, and automatic payouts to shops. Check the tax treatment with an accountant.

## Before production (important)
1. **Payments:** only a `SandboxGateway` exists (it refuses to start under profile `prod`). Implement `PaymentGateway` for PhonePe / Google Pay with your merchant credentials and their signature scheme, then set `app.payments.gateway`.
2. **Compile it:** the Spring layer was written without network access and has never been compiled. Run `mvn test` and fix anything it reports. The pure-Java core (pricing, money, state machine, OTP hashing) is verified: 29 tests pass.
3. **Push notifications:** SSE works for open apps. Add Firebase Cloud Messaging for background/closed-app alerts in the mobile build.
4. **Malware scanning:** `FileService` rejects risky PDFs; plug ClamAV in at the marked line for real scanning.
5. Review times: DB and JVM both run in UTC; analytics use the shop time zone (`?tz=`, default Asia/Kolkata).
6. Notifications hold the plain OTP (as specified). Treat notification access as sensitive, or send OTPs by push/SMS only.
