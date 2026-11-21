# Campus Hub

<div align="center">

**A complete campus commerce platform for canteen orders, campus printing, shop management, and administration.**

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.6-3178C6?logo=typescript&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-5-646CFF?logo=vite&logoColor=white)

</div>

## Overview

Campus Hub connects students with campus shops through a role-based web application. It supports two shop types:

- **Canteen — `FOOD`**: food and general item ordering
- **CampusDesk — `DESK`**: products, stationery, printing jobs, and PDF uploads

The project consists of a Spring Boot REST API and a React/Vite frontend. The frontend communicates with the backend through `/api`, with Vite proxying requests to Spring Boot during development.

```text
CampusHub/
├── app/       React, TypeScript, and TailwindCSS frontend
├── backend/   Java 21 and Spring Boot REST API
├── docs/      Frontend-to-backend API reference
└── scripts/   End-to-end smoke test
```

## Table of Contents

- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [SMS and Phone Verification](#sms-and-phone-verification)
- [Running the Smoke Test](#running-the-smoke-test)
- [API Documentation](#api-documentation)
- [Database](#database)
- [Testing](#testing)
- [Production Checklist](#production-checklist)
- [Known Limitations](#known-limitations)

## Key Features

### For Students

- Phone-number-based registration and verification
- Role-based login and automatic access-token refresh
- Browse canteen and CampusDesk shops
- View products, prices, offers, printing prices, and shop details
- Add products and PDF print jobs to the cart
- Follow shops and save favourites
- View previous orders and reorder items
- Pay 50% in advance and the remaining amount before pickup
- Receive pickup OTP notifications
- Cancel eligible orders and submit reviews
- Manage profile details, print presets, notification preferences, and payment history

### For Shop Owners

- Create a shop and configure shop details
- Wait for administrator approval before creating catalog data
- Manage products, stock, minimum stock levels, and inventory history
- Configure CampusDesk printing rates
- Create discounts and shop announcements
- Accept or reject orders
- Move orders through processing and ready states
- Verify pickup OTPs
- View payments, refunds, dashboards, and analytics

### For Administrators

- Approve or reject shops
- Activate or disable users
- Manage shop categories
- Configure platform fee settings
- Monitor orders, payments, settlements, and problem cases
- Retry failed refunds
- Review platform analytics and audit logs
- Moderate shop reviews

## Technology Stack

### Frontend

| Technology | Purpose |
|---|---|
| React 18 | Component-based user interface |
| TypeScript | Static type checking |
| Vite 5 | Development server and production builds |
| React Router 6 | Role-based routing |
| TanStack React Query | Server state and caching |
| Axios | HTTP client and token refresh |
| React Hook Form + Zod | Forms and validation |
| Tailwind CSS | Styling |
| Lucide React | Icons |

### Backend

| Technology | Purpose |
|---|---|
| Java 21 | Application runtime |
| Spring Boot 3.3 | REST API and dependency injection |
| Spring Security | Authentication and role authorization |
| Spring Data JPA | Database access |
| Flyway | Versioned database migrations |
| MySQL 8 | Persistent data store |
| JJWT | Access and refresh tokens |
| SpringDoc OpenAPI | Swagger API documentation |
| AWS S3 SDK | S3-compatible private file storage |
| Apache PDFBox | PDF validation |
| Testcontainers | Docker-based integration tests |

## Project Structure

```text
.
├── app/
│   ├── src/
│   │   ├── components/       Shared layout, UI, notifications, and payment panels
│   │   ├── lib/              API client, authentication, hooks, and utilities
│   │   ├── pages/
│   │   │   ├── auth/         Login, registration, and verification
│   │   │   ├── student/      Shop browsing, cart, print jobs, and orders
│   │   │   ├── owner/        Catalog, stock, offers, and owner analytics
│   │   │   └── admin/        Shop approval, money, issues, and audit
│   │   └── App.tsx           Role-based application routes
│   ├── package.json
│   └── vite.config.ts
├── backend/
│   ├── src/main/java/com/campushub/
│   │   ├── auth/             Registration, login, refresh, and passwords
│   │   ├── security/         JWT and Spring Security configuration
│   │   ├── catalog/          Shops, categories, and public catalog
│   │   ├── cart/             Cart and print-job handling
│   │   ├── order/            Checkout and order state transitions
│   │   ├── payments/         Sandbox and webhook payment handling
│   │   ├── pricing/          Server-side prices and platform fees
│   │   ├── refund/           Refund processing
│   │   ├── files/            PDF validation and private file storage
│   │   └── notification/     In-app notifications and SSE
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/     Flyway migrations
│   ├── src/test/java/        Unit and integration tests
│   ├── docker-compose.yml
│   └── pom.xml
├── docs/
│   └── API-MAP.md            Frontend-to-backend API reference
└── scripts/
    └── smoke.mjs             End-to-end order-flow verification
```

## How It Works

### Frontend and Backend Connection

- The React app makes requests to `/api/...`.
- Vite forwards `/api` requests to `http://localhost:8080` by default.
- `VITE_API_TARGET` in `app/.env` controls the development backend URL.
- The backend exposes the API under `/api` and rejects unauthorized roles on the server.

### Authentication

- Access tokens expire after 15 minutes and are kept in memory.
- Refresh tokens are stored in local storage and rotate after use.
- Axios performs automatic refresh when an access token expires.
- Parallel refresh requests are prevented to avoid refresh-token reuse detection.
- The frontend role guard provides navigation convenience, but the backend remains the source of authorization.

### Shop Approval

1. A user registers as a shop owner.
2. The owner creates a shop.
3. Product, stock, price, and offer operations remain locked.
4. An administrator approves the shop.
5. Catalog management becomes available automatically.

### Order Lifecycle

```text
PLACED
  → ACCEPTED
  → PROCESSING
  → READY
  → COLLECTED
```

At `READY`, the backend creates a six-digit pickup OTP. Pickup is allowed only after:

- The order is in `READY` state
- The remaining payment is successful
- The OTP is valid and unused

## Prerequisites

Install the following before running the project:

- Java 21
- Maven
- Node.js 18 or newer
- npm
- Docker and Docker Compose
- MySQL 8.4, or Docker for the bundled MySQL service

## Quick Start

### 1. Start MySQL

```bash
cd backend
docker compose up -d mysql
```

The compose service creates a UTC MySQL database named `campushub` on port `3306`.

### 2. Configure the Backend

```bash
cd backend
cp .env.example .env
```

Edit `backend/.env` and set at least:

```dotenv
DB_URL=jdbc:mysql://localhost:3306/campushub?useSSL=false&serverTimezone=UTC
DB_USER=campus
DB_PASSWORD=change-me
JWT_SECRET=replace-with-at-least-32-random-characters
OTP_SECRET=replace-with-another-random-secret
PAYMENT_WEBHOOK_SECRET=replace-with-a-gateway-secret
CORS_ORIGINS=http://localhost:5173
APP_BASE_URL=http://localhost:5173
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=change-this-long-password
```

Load the variables on Linux or macOS:

```bash
export $(grep -v '^#' .env | xargs)
```

On Windows PowerShell, set the variables directly in the terminal before starting Spring Boot.

### 3. Start the Backend

```bash
cd backend
mvn spring-boot:run
```

The API runs at:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

The first administrator is created at startup from `ADMIN_EMAIL` and `ADMIN_PASSWORD`. The password must contain at least 12 characters.

### 4. Configure the Frontend

```bash
cd app
npm install
cp .env.example .env
npm run typecheck
npm run dev
```

On Windows, copy the environment file with:

```powershell
copy .env.example .env
```

Open:

```text
http://localhost:5173
```

Default frontend configuration:

```dotenv
VITE_API_TARGET=http://localhost:8080
VITE_SANDBOX=true
```

## SMS and Phone Verification

Every account must register with a mobile number and verify it before login.

- Verification codes contain six digits
- Codes expire after 10 minutes
- A maximum of five wrong attempts is allowed
- A new code can be requested once per minute
- A maximum of five codes can be requested per hour
- A verified phone number belongs to only one account

### Development SMS

Keep `app.sms.provider` set to `console`. The code is not sent externally; it is printed in the backend console:

```text
SMS (development, NOT sent) to +91...
```

The development-only `/api/dev/last-sms` endpoint can also retrieve the last generated code.

### Twilio SMS

Set `app.sms.provider` to `twilio` and configure:

```dotenv
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_FROM=
TWILIO_MESSAGING_SERVICE_SID=
```

In India, SMS providers and Indian carriers may require a registered sender and DLT-approved message template.

## Running the Smoke Test

With the backend running and development SMS enabled, run:

```bash
ADMIN_EMAIL=admin@example.com \
ADMIN_PASSWORD='your-long-admin-password' \
node scripts/smoke.mjs
```

The smoke test verifies:

- Administrator and shop-owner authentication
- Student phone verification
- Role restrictions
- Shop approval
- Product and stock creation
- Cart pricing
- 50% advance payment
- Idempotent payment confirmation
- Order status changes
- Pickup OTP delivery and verification
- Remaining-payment confirmation
- Reviews
- Owner and administrator analytics
- Settlement reporting

To test the complete frontend proxy path, start the React app and run:

```bash
API=http://localhost:5173/api \
ADMIN_EMAIL=admin@example.com \
ADMIN_PASSWORD='your-long-admin-password' \
node scripts/smoke.mjs
```

## API Documentation

Runtime Swagger documentation:

```text
http://localhost:8080/swagger-ui.html
```

A generated frontend-to-backend endpoint map is available in:

```text
docs/API-MAP.md
```

Main API prefixes:

| Area | Prefix |
|---|---|
| Authentication | `/api/auth` |
| Public shops and catalog | `/api/shops`, `/api/categories` |
| Student operations | `/api/student` |
| Shop-owner operations | `/api/shop-owner` |
| Administrator operations | `/api/admin` |
| Payments | `/api/payments` |
| Notifications | `/api/notifications` |
| Uploaded files | `/api/files` |

## Database

The database schema is managed by Flyway migrations:

| Migration | Purpose |
|---|---|
| `V1__init.sql` | Core users, shops, categories, products, carts, and orders |
| `V2__features.sql` | Discounts and additional platform features |
| `V3__platform_fee.sql` | Platform fee configuration |
| `V4__student_features.sql` | Student profiles and social features |
| `V5__phone_verification.sql` | Phone verification and OTP support |

Hibernate is configured with `ddl-auto: validate`, so Flyway is responsible for schema creation and updates.

## Testing

Run backend tests with:

```bash
cd backend
mvn test
```

Test coverage includes:

- Pricing and money calculations
- Platform fee calculations
- Order state transitions
- Backend integration behavior
- Docker-based MySQL tests using Testcontainers

Testcontainers requires Docker to be running.

## Production Checklist

Before deploying to production:

- [ ] Replace `SandboxGateway` with a real payment provider
- [ ] Configure payment webhook signatures and merchant credentials
- [ ] Configure Twilio or another SMS provider
- [ ] Register required Indian SMS sender and DLT templates
- [ ] Configure AWS S3, Cloudflare R2, or MinIO
- [ ] Use secure storage for refresh tokens in the mobile app
- [ ] Add Redis if running multiple backend instances
- [ ] Add malware scanning, such as ClamAV, for PDF uploads
- [ ] Add Firebase Cloud Messaging for background/mobile notifications
- [ ] Implement automatic payouts if required
- [ ] Review GST, tax, refund, and platform-fee rules with an accountant
- [ ] Keep databases and application instances using UTC
- [ ] Secure SMTP and other external service credentials
- [ ] Avoid exposing plaintext pickup OTPs through notification APIs
- [ ] Run a complete Maven build and integration test suite

## Known Limitations

- The included payment gateway is only a sandbox implementation
- Push notifications are not implemented; open-browser notifications use SSE
- Rate limiting is process-local and should use Redis for horizontal deployments
- Uploaded PDF files receive structural and filename validation, but need malware scanning in production
- PDF files are private rather than encrypted at rest
- No automatic payout workflow exists for shop owners
- Student phone numbers cannot currently be changed from the profile screen
- Plain pickup OTP values are available through notifications and should be treated as sensitive

## License

Add a license that matches how you intend to distribute and use this project.
