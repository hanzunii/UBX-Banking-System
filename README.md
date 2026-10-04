# UBX-Banking-System

A Distributed System using RMI, Build Web and Android App for User role and
Desktop App for System Admins.

| Component | Stack | Role |
|-----------|-------|------|
| `rmi-server` | Spring Boot 3, JPA, H2 | RMI core server - all business logic |
| `api-gateway` | Spring Boot 3, WebSocket | REST/WebSocket front, resolves RMI stubs |
| `atm-android` | Java, Material 3 | Android app for users (UBX Mobile) |
| `web-app` | React 18, Vite, Tailwind | Web app for users |
| `admin-desktop` | Java Swing | System admin console |
| `atm-server` | Spring Boot 3 | Legacy v1 single-module server, superseded |

Features: bcrypt PIN hashing, JWT sessions, login rate limiting and lockout,
input validation, atomic transfers with idempotency keys, live balance push
over WebSocket.

**Quick start:** `./start-server.sh` - no IP configuration required, the app
discovers the gateway automatically (see [Server address: zero
configuration](#3-server-address-zero-configuration)).

---

## Architecture

```
Android App (Java/HttpURLConnection)          web-app (React)      admin-desktop (Swing)
        │  HTTP + JWT bearer token                   │                       │
        └──────────────────┬─────────────────────────┴───────────────────────┘
                           ▼
         API Gateway  (0.0.0.0:8080)  ── also answers UDP discovery on 45560
                           │  RMI stubs via registry localhost:1099
                           ▼
         RMI Core Server (HTTP 8081, RMI registry 1099)
                           │  JPA
                           ▼
         H2 Embedded Database (file: ./data/atmbank)
```

---

## 1. Running the Server

One command starts both processes (builds the jars first if needed):

```bash
./start-server.sh
```

It launches, waits for readiness, and prints the gateway URL it is reachable on:

- **RMI core server** — HTTP 8081 + RMI registry 1099 (business logic, JPA, H2)
- **API gateway** — HTTP 8080 (the endpoint the app, web app and desktop client talk to)

On first run it creates the H2 file DB and tables automatically and seeds demo accounts.

**Demo accounts (auto-seeded):**
| Account | PIN  | Role   |
|---------|------|--------|
| 10000001 | 1234 | USER (Alice Johnson) |
| 10000002 | 5678 | USER (Bob Smith) |
| 10000003 | 1122 | MANAGER (Carol White) |

Logs go to `rmi-server.log` and `api-gateway.log`. `Ctrl+C` stops both.

---

## 2. Firewall

The phone needs to reach two ports:

```bash
sudo ufw allow 8080/tcp     # API traffic
sudo ufw allow 45560/udp    # discovery broadcast
```

If UDP 45560 is blocked, the app still finds the server — it falls back to
sweeping the phone's own subnet on port 8080. Blocking both means the app
cannot discover the gateway and you must type its URL by hand.

---

## 3. Server address: zero configuration

**You never hard-code an IP and never rebuild the app when your laptop's
address changes.** The gateway's LAN address comes from DHCP, so it moves
around; instead the app finds it at runtime.

At startup, and again whenever a request fails to connect, the app:

1. **Validates the cached URL** — a single `GET /api/v1/health` probe. This is
   the normal path and costs one round trip.
2. **Broadcasts a discovery probe** to `255.255.255.255:45560`. The gateway
   answers with the URL it is reachable on *right now*, choosing the LAN
   address that shares a subnet with the phone. This resolves per request, so
   a DHCP change is picked up without restarting anything.
3. **Sweeps the phone's /24** over HTTP looking for a gateway that answers
   `GET /api/v1/health`. This covers Wi-Fi networks that drop broadcast
   traffic, at the cost of a few seconds.
4. On the emulator, tries `10.0.2.2` (the host loopback alias).

Whatever is found is validated and cached in preferences for the next launch.

The login screen shows the address in use and has a **Detect server** button to
re-run discovery on demand. The **Server URL** field is still editable if you
need to override detection (e.g. a manually forwarded port).

Discovery is tuned in `api-gateway/src/main/resources/application.properties`:

| Property | Default | Purpose |
|----------|---------|---------|
| `discovery.enabled` | `true` | Set `false` to stop answering probes |
| `discovery.port` | `45560` | UDP port to answer on (change the client constant too) |
| `discovery.advertise-host` | *(empty)* | Force a specific address; empty = auto-detect |

The RMI core server's `application.properties` also lets you change JWT secret
/ expiry and the rate-limit attempts and lockout window.


---

## 4. Installing the Android app on your phone

The current APK in the repo root is:

- `ubx-mobile-v1.4-debug.apk` — zero-config server discovery (recommended)

Earlier builds are kept for reference: `ubx-mobile-v1.3-debug.apk`,
`ubx-mobile-v1.2-debug.apk`, `atm-mobile-v1.1-debug.apk` and the legacy
`atm-mobile-v1.0*.apk`. All `com.atm.mobile` debug APKs share one signing
certificate, so v1.4 installs straight over v1.3 without uninstalling (your
session and any manually entered URL are preserved — the app re-validates and
re-discovers them on launch).

### On the phone
1. Copy the APK to your phone (USB or cloud).
2. Tap the APK in your file manager.
3. Allow **"Install from unknown sources"** when prompted.
4. Open **UBX Mobile** — the splash screen locates the gateway on its own.

### Via adb (needs "Install via USB" enabled in Developer Options)
```bash
adb install -r ubx-mobile-v1.4-debug.apk
```

### Rebuilding
```bash
cd atm-android
gradle assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Using the app

1. **Login** with a demo account (10000001 / 1234) OR tap **Register** to create your own account (pick account #, phone, name, 4-digit PIN).
2. **Dashboard** shows your live balance.
3. **Withdraw / Deposit / Transfer** money.
4. **Transaction History** lists all recent debits/credits/transfers with running balance.

---

## 6. API reference (for testing)

All paths are prefixed with the gateway's base: `http://<host>:8080/api/v1`.
Authenticated endpoints require header: `Authorization: Bearer <token>`.

| Method | Endpoint | Body | Purpose |
|--------|----------|------|---------|
| GET | `/health` | - | Liveness + which RMI services are bound |
| GET | `/system/config` | - | Limits, currency, version |
| POST | `/auth/register` | `{accountNumber, phone, holderName, pin}` | Create account |
| POST | `/auth/login` | `{accountNumber, pin, deviceId}` | Login → JWT (+ MFA challenge) |
| POST | `/auth/mfa/verify` | `{mfaToken, otp}` | Complete MFA |
| POST | `/auth/refresh` | `{refreshToken}` | New access token |
| POST | `/auth/verify-pin` | `{pin}` | Re-auth for sensitive actions |
| GET | `/accounts/me` | - | Balance of the signed-in account |
| GET | `/accounts/lookup/{accountNumber}` | - | Beneficiary lookup |
| POST | `/transactions/transfer` | `{toAccountNumber, amount}` | Transfer (send `X-Idempotency-Key`) |
| GET | `/transactions/history?limit=50` | - | Transaction history |

---

## Security features implemented
- PIN stored as bcrypt hash (never plaintext)
- Stateless JWT sessions (configurable expiry)
- Login rate limiting: 5 wrong attempts → 15-min lockout
- Server-side input validation on all fields/amounts
- Atomic DB transfers with transaction history for both parties
- Client-generated idempotency keys on financial operations
- Money stored/rounded to 2 decimal places using BigDecimal

## Limitations (not a fully public product)
- Plain HTTP (no TLS). For real money use HTTPS + a domain + reverse proxy (e.g. nginx/caddy).
- Single shared DB file — fine for demo/LAN, not multi-node.
- PIN-based auth only (no biometrics/2FA).
- Discovery assumes the phone and the server share a routable subnet; across
  VLANs set `discovery.advertise-host` or type the URL manually.
