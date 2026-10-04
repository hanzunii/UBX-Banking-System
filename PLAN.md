# Android Mobile Payment System - Implementation Plan

## Problem
Android doesn't support Java RMI natively. We need a REST API gateway so the Android app can communicate with the banking server over HTTP.

## Architecture

```
Android App (Java)
    ↓ HTTP/REST (JSON)
REST API Gateway (new - runs on server port 8080)
    ↓ RMI
BankingServiceImpl (existing - port 1098)
    ↓ JDBC
Oracle Database
```

## Phase 1: Add REST API Gateway to Server

**New file:** `src/main/java/com/atm/server/RestApiServer.java`

A lightweight HTTP server using Java's built-in `com.sun.net.httpserver.HttpServer` (no extra dependencies needed). Runs alongside the RMI server.

### REST Endpoints:

| Method | Endpoint | Request Body | Response |
|--------|----------|--------------|----------|
| POST | `/api/auth` | `{"accountNumber":"...", "pin":"..."}` | `Account` JSON or 401 |
| GET | `/api/balance/{accountNumber}` | - | `{"balance": 1000.0}` |
| POST | `/api/withdraw` | `{"accountNumber":"...", "amount": 500}` | `{"success": true}` |
| POST | `/api/deposit` | `{"accountNumber":"...", "amount": 500}` | `{"success": true}` |
| POST | `/api/transfer` | `{"from":"...", "to":"...", "amount": 500}` | `{"success": true}` |

**Modify:** `ServerMain.java` - start both RMI registry (port 1098) and REST server (port 8080).

## Phase 2: Android App

**Location:** `/home/hanzunii/ATM-Banking-system/atm-android/`

### Project Structure:
```
app/src/main/java/com/atm/mobile/
├── model/
│   └── Account.java              (POJO matching server Account)
├── network/
│   └── ApiClient.java            (HTTP client for REST API)
├── ui/
│   ├── LoginActivity.java        (Login screen)
│   ├── DashboardActivity.java    (Main menu after login)
│   ├── BalanceActivity.java      (Check balance)
│   ├── WithdrawActivity.java     (Withdraw money)
│   ├── DepositActivity.java      (Deposit money)
│   └── TransferActivity.java     (Transfer money)
└── MainActivity.java             (Entry point, redirects to Login)
```

### Screens:

1. **Login** - Account number + PIN input, "Login" button
2. **Dashboard** - Welcome message, balance display, buttons for Withdraw/Deposit/Transfer/Logout
3. **Withdraw** - Amount input, confirm button
4. **Deposit** - Amount input, confirm button
5. **Transfer** - To account + amount input, confirm button

### Key Files:
- `app/build.gradle` - dependencies (okhttp for HTTP, Gson for JSON)
- `AndroidManifest.xml` - INTERNET permission, activity declarations
- Layout XMLs for each screen
- `ApiClient.java` - singleton HTTP client with methods matching REST endpoints

## Files to Create/Modify:

### Server side:
1. **CREATE** `atm-server/src/main/java/com/atm/server/RestApiServer.java`
2. **MODIFY** `atm-server/src/main/java/com/atm/server/ServerMain.java`

### Android side (new project):
3. **CREATE** `atm-android/` - full Android project structure
4. **CREATE** `atm-android/build.gradle` (root)
5. **CREATE** `atm-android/settings.gradle`
6. **CREATE** `atm-android/app/build.gradle`
7. **CREATE** `atm-android/app/src/main/AndroidManifest.xml`
8. **CREATE** `atm-android/app/src/main/java/com/atm/mobile/` - all Java files
9. **CREATE** `atm-android/app/src/main/res/layout/` - all XML layouts
10. **CREATE** `atm-android/app/src/main/res/values/` - strings, colors, styles

## Server Prerequisites:
- The RMI server must be running on port 1098
- The REST gateway runs on port 8080
- Android app connects to `http://<server-ip>:8080/api/...`
