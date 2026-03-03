# Hospital Management System (HMS)

A microservices-based Hospital Management System built with Spring Boot 3, Spring Cloud Gateway, Keycloak, and NATS JetStream.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Project Structure](#3-project-structure)
4. [Step 1 — Start Infrastructure (Docker)](#4-step-1--start-infrastructure-docker)
5. [Step 2 — Provision Keycloak (first run only)](#5-step-2--provision-keycloak-first-run-only)
6. [Step 3 — Build the Services](#6-step-3--build-the-services)
7. [Step 4 — Start All Services](#7-step-4--start-all-services)
8. [Step 5 — Get an Access Token](#8-step-5--get-an-access-token)
9. [Step 6 — Call Services via the Gateway](#9-step-6--call-services-via-the-gateway)
10. [Gateway — How It Works](#10-gateway--how-it-works)
11. [RBAC — Managing Route Access at Runtime](#11-rbac--managing-route-access-at-runtime)
12. [Service Reference](#12-service-reference)
13. [Stopping Services](#13-stopping-services)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. Architecture Overview

```
                        ┌─────────────────────────────────────────────┐
                        │           Spring Cloud Gateway :3000         │
                        │                                              │
  Client ──────────────►│  RateLimitFilter → JwtAuthenticationFilter  │
  (REST / HTTP)         │         (validates Keycloak JWT)             │
                        │         (enforces RBAC rules)                │
                        └──────────────────┬──────────────────────────┘
                                           │ injects X-User-Id / X-User-Role / X-User-Name
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                       │
               :3001 Reception      :3002 Clinical          :3003 Staff
               :3004 Finance        :3005 Lab               :3006 Ward
                    │                      │                       │
                    └──────────────────────┼──────────────────────┘
                                           │
               ┌───────────────────────────┼───────────────────────────┐
               │                           │                           │
          PostgreSQL :5432            NATS :4222               Redis :6379
          (all schemas)           (JetStream events)          (rate limiting,
                                                               RBAC rules)
               │
          MinIO :9000               Keycloak :8080             ws-bridge :3010
          (file storage)            (auth server)           (Socket.IO + NATS)
```

**Key rule:** Every client request goes through the gateway on port **3000**. Services on ports 3001–3006 should never be called directly in production. Authentication is done directly with Keycloak on port **8080** (not through the gateway).

---

## 2. Prerequisites

| Tool | Minimum Version | Check |
|------|----------------|-------|
| Java (JDK) | 21 | `java -version` |
| Maven | 3.9 | `mvn -version` |
| Docker & Docker Compose | Docker 24 | `docker -v` |
| Node.js | 18 | `node -v` |
| curl | any | `curl --version` |
| python3 | 3.8 | `python3 --version` |

---

## 3. Project Structure

```
Hospital management project/
├── gateway/                   Spring Cloud Gateway (port 3000)
├── reception-service/         Patient registration, OPD tokens, queue (port 3001)
├── clinical-service/          Sessions, vitals, prescriptions, pharmacy (port 3002)
├── staff-service/             Doctors, departments, nurses, schedules (port 3003)
├── finance-service/           Invoices, payments, reports (port 3004)
├── lab-service/               Lab tests, orders, results (port 3005)
├── ward-service/              Wards, beds, admissions, discharge (port 3006)
├── ws-bridge/                 Socket.IO + NATS bridge (port 3010)
├── scripts/
│   ├── setup-keycloak.sh      Keycloak provisioning (run once)
│   └── start-services.sh      Start / stop all services
├── docker-compose.yml         Infrastructure containers
├── api-collection.http        Gateway-level HTTP test file
└── README.md
```

---

## 4. Step 1 — Start Infrastructure (Docker)

Start all infrastructure containers (PostgreSQL, NATS, MinIO, Redis, Keycloak):

```bash
docker compose up -d
```

Wait for all containers to be healthy:

```bash
docker compose ps
```

All containers should show `healthy` or `running`. Keycloak takes about 30–60 seconds on first start.

### Infrastructure ports

| Container | Port | Credentials |
|-----------|------|-------------|
| PostgreSQL | 5432 | `hms_user` / `hms_password` / db: `hms_db` |
| NATS | 4222 | — |
| NATS monitoring | 8222 | http://localhost:8222 |
| MinIO API | 9000 | `minioadmin` / `minioadmin` |
| MinIO Console | 9001 | http://localhost:9001 |
| Redis | 6379 | — |
| Keycloak | 8080 | `admin` / `admin` |

> **Note:** Keycloak uses `KC_DB: dev-file` (embedded storage). All realm data is stored inside the container. If you remove the Keycloak container (`docker compose down`) and recreate it, you must re-run `setup-keycloak.sh`.

---

## 5. Step 2 — Provision Keycloak (first run only)

Run this **once** after a fresh Keycloak container start:

```bash
./scripts/setup-keycloak.sh
```

The script waits for Keycloak to be ready, then creates:

- The `hms` realm (token TTL: 5 minutes)
- 6 realm roles: `admin`, `doctor`, `nurse`, `receptionist`, `lab_tech`, `ward_staff`
- 1 public client: `hms-client` (Direct Access Grants enabled — no secret needed)
- 6 seed user accounts with roles assigned

### Seed accounts (password: `Password@123`)

| Username | Role | Full Name |
|----------|------|-----------|
| `admin` | admin | System Admin |
| `dr.smith` | doctor | Jenny Smith |
| `nurse.silva` | nurse | Sarah Silva |
| `reception1` | receptionist | Reception Desk |
| `labtech1` | lab_tech | Lab Technician |
| `ward.staff1` | ward_staff | Ward Staff |

> The script is **idempotent** — safe to run multiple times. It skips resources that already exist.

> **When to re-run:** Only if you recreate the Keycloak container (`docker compose down && docker compose up -d`). Normal restarts of Docker containers do not lose data.

---

## 6. Step 3 — Build the Services

Build all Java services (skip if JARs are already built):

```bash
# Build all services at once using the start script
./scripts/start-services.sh --build

# Or build each service manually
cd gateway && mvn package -DskipTests
cd reception-service && mvn package -DskipTests
cd clinical-service && mvn package -DskipTests
cd staff-service && mvn package -DskipTests
cd finance-service && mvn package -DskipTests
cd lab-service && mvn package -DskipTests
cd ward-service && mvn package -DskipTests
```

> You only need to rebuild when you change source code. JARs persist between sessions.

---

## 7. Step 4 — Start All Services

```bash
./scripts/start-services.sh
```

This starts all 8 services (7 Java + 1 Node.js) in order, waiting for each port to open before proceeding.

### What the script does

- Starts each service and waits up to 60 seconds for it to open its port
- Skips services that are already running
- Writes logs to `logs/<service-name>.log`
- Saves PIDs to `logs/<service-name>.pid`

### Expected output

```
Starting HMS Java services...

[INFO]  Starting reception-service on port 3001...
[OK]    reception-service is UP  (PID 12345, port 3001)

[INFO]  Starting clinical-service on port 3002...
[OK]    clinical-service is UP  (PID 12346, port 3002)

... (all 7 Java services)

Starting ws-bridge (Node.js)...
[OK]    ws-bridge is UP  (PID 12350, port 3010)

════════════════════════════════════════
  All services started successfully!
════════════════════════════════════════

  Service             Port   PID
  reception-service   3001   12345
  clinical-service    3002   12346
  staff-service       3003   12347
  finance-service     3004   12348
  lab-service         3005   12349
  ward-service        3006   12350
  gateway             3000   12351
  ws-bridge           3010   12352

  Logs: /path/to/logs/
  To stop all: ./scripts/start-services.sh --stop
```

### Verify the gateway is up

```bash
curl http://localhost:3000/health
```

Expected response:

```json
{"success": true, "data": {"status": "ok", "service": "hms-gateway"}}
```

---

## 8. Step 5 — Get an Access Token

All API calls go through the gateway and require a Keycloak JWT. Get a token by calling Keycloak **directly** (not through the gateway):

```bash
curl -s -X POST http://localhost:8080/realms/hms/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=hms-client&username=admin&password=Password%40123" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
```

Tokens expire after **5 minutes**. Re-run the above command to get a fresh one.

### Login endpoint

```
POST http://localhost:8080/realms/hms/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=hms-client
username=<username>
password=<password>
```

> `hms-client` is a **public** client — no `client_secret` is needed.

---

## 9. Step 6 — Call Services via the Gateway

All API calls go to `http://localhost:3000`. Include the token in the `Authorization` header:

```bash
# Verify the token works and RBAC allows access
curl -s http://localhost:3000/patients \
  -H "Authorization: Bearer <your-token>"

# Or store the token in a variable
TOKEN=$(curl -s -X POST http://localhost:8080/realms/hms/protocol/openid-connect/token \
  -d "grant_type=password&client_id=hms-client&username=admin&password=Password%40123" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s http://localhost:3000/patients -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:3000/wards    -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:3000/orders   -H "Authorization: Bearer $TOKEN"
```

> **Do not send `X-User-Id`, `X-User-Role`, or `X-User-Name` headers.** The gateway extracts them from the JWT and injects them automatically. Anything you send will be overwritten.

### HTTP test file

Open `api-collection.http` in VS Code (with the REST Client extension) or IntelliJ for a full set of ready-to-use requests covering every endpoint in every service.

---

## 10. Gateway — How It Works

### Filter chain

Every request passes through three layers in order:

```
Request
  │
  ▼
[1] AdminAuthFilter       — WebFilter, order HIGHEST_PRECEDENCE+10
    Only activates for /admin/** paths.
    Requires admin JWT. All other paths pass through instantly.
  │
  ▼
[2] RateLimitFilter       — GlobalFilter, order HIGHEST_PRECEDENCE
    100 requests/minute per IP (Redis counter).
    Returns 429 if exceeded.
  │
  ▼
[3] JwtAuthenticationFilter — GlobalFilter, order HIGHEST_PRECEDENCE+100
    Skips /health (public).
    For all other paths:
      a. Match path against RBAC rules → 404 if no rule matches
      b. Extract Bearer token → 401 if missing/malformed
      c. Validate JWT via Keycloak JWKS (RS256) → 401 if invalid/expired
      d. Extract HMS role from realm_access.roles[] → 401 if no recognised role
      e. Check role is allowed for matched rule → 403 if denied
      f. Inject X-User-Id, X-User-Role, X-User-Name into request
      g. Remove Authorization header
      h. Forward to microservice
  │
  ▼
Microservice (3001–3006)
```

### Headers injected by the gateway

Microservices receive these headers on every authenticated request:

| Header | Source | Example |
|--------|--------|---------|
| `X-User-Id` | JWT `sub` claim (Keycloak UUID) | `a1b2c3d4-...` |
| `X-User-Role` | First HMS role in `realm_access.roles[]` | `doctor` |
| `X-User-Name` | JWT `name` claim (fallback: `preferred_username`) | `Jenny Smith` |

The `Authorization` header is removed before forwarding — microservices never see the raw JWT.

### Route table

| Path pattern | Service | Port |
|---|---|---|
| `/patients/*/vitals-history` | clinical-service | 3002 |
| `/patients/*/history` | clinical-service | 3002 |
| `/patients/*/admissions` | ward-service | 3006 |
| `/patients/**` | reception-service | 3001 |
| `/tokens/**` | reception-service | 3001 |
| `/queue/**` | reception-service | 3001 |
| `/appointments/**` | reception-service | 3001 |
| `/sessions/**` | clinical-service | 3002 |
| `/pharmacy/**` | clinical-service | 3002 |
| `/doctors/**` | staff-service | 3003 |
| `/departments/**` | staff-service | 3003 |
| `/schedules/**` | staff-service | 3003 |
| `/nurse-allocations/**` | staff-service | 3003 |
| `/staff/**` | staff-service | 3003 |
| `/invoices/**` | finance-service | 3004 |
| `/reports/**` | finance-service | 3004 |
| `/prescriptions/**` | finance-service | 3004 |
| `/tests/**` | lab-service | 3005 |
| `/orders/**` | lab-service | 3005 |
| `/wards/**` | ward-service | 3006 |
| `/beds/**` | ward-service | 3006 |
| `/admissions/**` | ward-service | 3006 |
| `/health` | gateway (local) | 3000 |
| `/admin/routes` | gateway (local) | 3000 |

---

## 11. RBAC — Managing Route Access at Runtime

RBAC rules define which roles can access which paths. Rules are stored in Redis and cached in memory — changes take effect immediately without a gateway restart.

### Default access rules

| Path | Allowed Roles |
|---|---|
| `/patients/*/vitals-history` | admin, doctor, nurse, receptionist |
| `/patients/*/history` | admin, doctor, nurse, receptionist |
| `/patients/*/admissions` | admin, ward_staff, doctor, nurse |
| `/patients/**` | admin, receptionist, doctor, nurse |
| `/tokens/**` | admin, receptionist |
| `/queue/**` | admin, receptionist, doctor, nurse |
| `/appointments/**` | admin, receptionist, doctor |
| `/sessions/**` | admin, doctor, nurse |
| `/pharmacy/**` | admin, doctor, nurse |
| `/doctors/**` | admin |
| `/departments/**` | admin |
| `/schedules/**` | admin |
| `/nurse-allocations/**` | admin, nurse |
| `/staff/**` | admin |
| `/invoices/**` | admin, receptionist |
| `/reports/**` | admin |
| `/prescriptions/**` | admin, receptionist |
| `/tests/**` | admin, lab_tech |
| `/orders/**` | admin, lab_tech, doctor |
| `/wards/**` | admin, ward_staff |
| `/beds/**` | admin, ward_staff |
| `/admissions/**` | admin, ward_staff, doctor, nurse |

### Admin API (requires `admin` JWT)

**List all rules with their indices:**

```bash
curl http://localhost:3000/admin/routes \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Add a new rule (or replace an existing one with the same pattern):**

```bash
curl -X POST http://localhost:3000/admin/routes \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "pattern": "/reports/**",
    "allowedRoles": ["admin", "doctor"],
    "position": 0
  }'
```

- `position` is optional. Omit it (or use `-1`) to append at the end.
- If a rule for the same pattern already exists, it is removed first and the new one inserted at the requested position.

**Update roles for an existing rule** (find its index from GET first):

```bash
curl -X PUT http://localhost:3000/admin/routes/15 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"allowedRoles": ["admin", "doctor", "nurse"]}'
```

**Delete a rule:**

```bash
curl -X DELETE http://localhost:3000/admin/routes/15 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Restore all 22 built-in defaults:**

```bash
curl -X POST http://localhost:3000/admin/routes/reset \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Reload rules from Redis** (useful when another gateway instance changed the rules):

```bash
curl -X POST http://localhost:3000/admin/routes/reload \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

> **Rule ordering matters.** Rules are matched top-to-bottom — first match wins. More specific patterns (e.g. `/patients/*/history`) must have a **lower index** than broader ones (e.g. `/patients/**`). When using `POST` with a `position`, insert specific rules before the broad ones.

---

## 12. Service Reference

### Response envelope

All services return a consistent JSON envelope:

```json
{
  "success": true,
  "data": { ... },
  "meta": { "page": 1, "limit": 20, "total": 45, "totalPages": 3 },
  "error": null
}
```

On error:

```json
{
  "success": false,
  "data": null,
  "meta": null,
  "error": { "code": "RESOURCE_NOT_FOUND", "message": "Patient not found", "statusCode": 404 }
}
```

### Reception Service — port 3001

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/patients` | admin, receptionist, doctor, nurse | List all patients |
| POST | `/patients` | admin, receptionist, doctor, nurse | Register new patient |
| GET | `/patients/{id}` | admin, receptionist, doctor, nurse | Get patient by ID |
| PUT | `/patients/{id}` | admin, receptionist, doctor, nurse | Update patient |
| GET | `/patients/search?q=` | admin, receptionist, doctor, nurse | Search patients |
| POST | `/tokens` | admin, receptionist | Issue OPD token |
| GET | `/tokens/today` | admin, receptionist | Today's token list |
| PUT | `/tokens/{id}/status` | admin, receptionist | Update token status |
| GET | `/queue/{type}` | admin, receptionist, doctor, nurse | Live queue by type |
| POST | `/appointments` | admin, receptionist, doctor | Book appointment |
| GET | `/appointments` | admin, receptionist, doctor | List appointments |
| DELETE | `/appointments/{id}` | admin, receptionist, doctor | Cancel appointment |

### Clinical Service — port 3002

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/sessions` | admin, doctor, nurse | Start clinical session |
| GET | `/sessions` | admin, doctor, nurse | List sessions |
| GET | `/sessions/{id}` | admin, doctor, nurse | Get session |
| POST | `/sessions/{id}/vitals` | admin, doctor, nurse | Record vitals |
| POST | `/sessions/{id}/prescriptions` | admin, doctor, nurse | Add prescriptions |
| POST | `/sessions/{id}/lab-requests` | admin, doctor, nurse | Request lab tests |
| PUT | `/sessions/{id}/complete` | admin, doctor, nurse | Complete session (triggers billing + pharmacy events) |
| GET | `/patients/{id}/history` | admin, doctor, nurse, receptionist | Patient clinical history |
| GET | `/patients/{id}/vitals-history` | admin, doctor, nurse, receptionist | Patient vitals history |
| GET | `/pharmacy/queue` | admin, doctor, nurse | Pending internal prescriptions |
| PUT | `/pharmacy/{rxId}/dispense` | admin, doctor, nurse | Dispense prescription |

### Staff Service — port 3003

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/departments` | admin | List departments |
| POST | `/departments` | admin | Create department |
| GET | `/doctors` | admin | List doctors |
| POST | `/doctors` | admin | Create doctor (multipart, creates Keycloak account) |
| DELETE | `/doctors/{id}` | admin | Deactivate doctor |
| GET | `/staff` | admin | List staff members |
| POST | `/staff` | admin | Create staff member (nurse / receptionist) |
| GET | `/staff/available?date=&role=` | admin | Available staff for a date |
| POST | `/nurse-allocations` | admin, nurse | Allocate nurse to session |
| PUT | `/nurse-allocations/{id}/complete` | admin, nurse | Mark allocation complete |

### Finance Service — port 3004

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/invoices` | admin, receptionist | Create invoice manually |
| GET | `/invoices` | admin, receptionist | List invoices |
| GET | `/invoices/{id}` | admin, receptionist | Get invoice |
| POST | `/invoices/{id}/discount` | admin, receptionist | Apply discount |
| PUT | `/invoices/{id}/pay` | admin, receptionist | Record payment |
| GET | `/invoices/{id}/print` | admin, receptionist | Print-formatted invoice |
| POST | `/prescriptions/print` | admin, receptionist | Generate prescription PDF |
| GET | `/reports/summary` | admin | Revenue summary |
| GET | `/reports/earnings` | admin | Earnings chart |
| GET | `/reports/departments` | admin | Department statistics |

> Invoices are also created automatically via NATS events: `billing.opd` (clinical), `billing.ward` (ward), `billing.lab` (lab).

### Lab Service — port 3005

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/tests` | admin, lab_tech | List test catalog |
| POST | `/tests` | admin | Add test to catalog |
| POST | `/orders` | admin, lab_tech, doctor | Register walk-in order |
| GET | `/orders` | admin, lab_tech, doctor | List orders |
| GET | `/orders/{id}` | admin, lab_tech, doctor | Get order |
| PUT | `/orders/{id}/results` | admin, lab_tech | Enter test results |
| POST | `/orders/{id}/report` | admin, lab_tech | Upload PDF report to MinIO |
| PUT | `/orders/{id}/pay` | admin, lab_tech | Record payment (triggers billing.lab event) |

### Ward Service — port 3006

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/wards` | admin, ward_staff | List wards with occupancy |
| POST | `/wards` | admin, ward_staff | Create ward (auto-generates beds) |
| GET | `/beds` | admin, ward_staff | List beds (filter by ward/status) |
| PUT | `/beds/{id}` | admin, ward_staff | Update bed status |
| POST | `/admissions` | admin, ward_staff, doctor, nurse | Admit patient |
| GET | `/admissions` | admin, ward_staff, doctor, nurse | List admissions |
| GET | `/admissions/{id}` | admin, ward_staff, doctor, nurse | Get admission with running total |
| POST | `/admissions/{id}/services` | admin, ward_staff, doctor, nurse | Add service charge |
| DELETE | `/admissions/{id}/services/{sid}` | admin, ward_staff, doctor, nurse | Remove service charge |
| PUT | `/admissions/{id}/discharge` | admin, ward_staff, doctor, nurse | Discharge patient (triggers billing.ward event) |
| GET | `/patients/{id}/admissions` | admin, ward_staff, doctor, nurse | Patient admission history |

---

## 13. Stopping Services

```bash
# Stop all services
./scripts/start-services.sh --stop

# Stop infrastructure containers
docker compose down

# Stop infrastructure but keep data volumes
docker compose stop
```

---

## 14. Troubleshooting

### Gateway returns 401 "Invalid or unrecognised token"

- Token has expired (TTL is 5 minutes) — get a fresh token.
- Wrong realm or client — ensure you are using `client_id=hms-client` and realm `hms`.

### Gateway returns 401 "Token does not contain a recognised HMS role"

- The Keycloak user does not have one of the 6 HMS roles assigned.
- Run `./scripts/setup-keycloak.sh` again — it is idempotent and will assign missing roles.

### Gateway returns 403

- The user's role is not in the allowed list for that path.
- Check the RBAC rules with `GET /admin/routes` using an admin token.
- Add or update a rule with `POST /admin/routes` or `PUT /admin/routes/{index}`.

### Gateway returns 404 for a valid path

- No RBAC rule exists for that path pattern.
- Add one: `POST /admin/routes` with the pattern and allowed roles.

### Service returns 500 on startup

- The database or NATS might not be ready yet.
- Check `docker compose ps` — all containers should be `healthy`.
- Check the service log: `tail -50 logs/<service-name>.log`.

### Keycloak realm data lost after `docker compose down`

- Keycloak uses embedded file storage (`KC_DB: dev-file`) stored inside the container.
- After recreating the container, re-run `./scripts/setup-keycloak.sh`.

### Port already in use

```bash
# Find and kill the process on a port (e.g. 3001)
fuser -k 3001/tcp

# Or use the stop script which handles all services
./scripts/start-services.sh --stop
```

### View live service logs

```bash
tail -f logs/gateway.log
tail -f logs/reception-service.log
tail -f logs/clinical-service.log
# etc.
```

### Check which services are running

```bash
cat logs/*.pid 2>/dev/null | xargs -I{} sh -c 'kill -0 {} 2>/dev/null && echo "running: {}" || echo "dead: {}"'
```
