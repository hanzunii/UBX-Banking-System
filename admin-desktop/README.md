# ATM Banking — Admin Desktop App

A Java Swing desktop application for bank administrators, used inside the bank
to review, approve, reject, and credit customer accounts.

It connects to the **API Gateway** (HTTP, port 8080), which forwards to the
RMI Core Server — the same production architecture used by the Web and Android
apps.

## Features

- **Admin sign in** with account number + PIN + two-factor OTP (MFA)
- **Pending approvals queue** — live list of accounts waiting for bank approval
- **Approve** an account to activate it so the customer can sign in
- **Reject** a registration
- **Credit funds** — record the cash deposit a customer handed over at the
  branch and add it to their mobile-payment balance (same as the Web Admin
  Panel)
- Automatic **JWT refresh** and **idempotency** protection against duplicate
  credits

## Requirements

- Java 17+
- The **API Gateway** and **RMI Core Server** running:
  ```bash
  cd rmi-server    && ./gradlew bootRun   # port 1099 (RMI) + 8081 (bridge)
  cd api-gateway   && ./gradlew bootRun   # port 8080 (HTTP)
  ```

## Build & Run

```bash
cd admin-desktop
gradle build -x test
java -jar build/libs/admin-desktop-1.0.0.jar
```

Or use the helper script (builds and runs on the default server URL):

```bash
./run-desktop.sh
# override the server address:
./run-desktop.sh http://192.168.1.108:8080
```

## Demo Admin Account

The RMI server seeds a MANAGER account (in addition to the two user accounts):

| Account   | PIN  | Role    |
|-----------|------|---------|
| 10000003  | 1122 | MANAGER |

MANAGER has ADMIN-level privileges for the admin endpoints. Any account where
`role` is `ADMIN` or `MANAGER` can use the desktop app.

On the OTP screen you'll be shown the **demo OTP** directly (the gateway
returns it in the login response), because the real delivery channel (SMS/email)
is not wired up in this demo.

## Project Layout

```
admin-desktop/
├── build.gradle          # Java 17 + org.json, builds a runnable fat jar
├── run-desktop.sh        # Convenience build + run script
└── src/main/java/com/atm/admin/
    ├── AdminApp.java     # Entry point, server health check, splash
    ├── AdminService.java # HTTP client for all auth + admin API calls
    ├── Account.java      # Account POJO
    ├── LoginFrame.java   # Sign-in + MFA OTP screen
    └── DashboardFrame.java # Approve / reject / credit dashboard
```

## Admin API endpoints used

| Operation | Endpoint |
|-----------|----------|
| Login step 1 (PIN → OTP) | `POST /api/v1/auth/login` |
| Login step 2 (OTP → tokens) | `POST /api/v1/auth/mfa/verify` |
| List pending | `GET /api/v1/admin/pending` |
| Approve | `POST /api/v1/admin/{acct}/approve` |
| Reject | `POST /api/v1/admin/{acct}/reject` |
| Credit | `POST /api/v1/admin/{acct}/credit` |
| Health | `GET /api/v1/health` |
