# SOAR Demo - Security Orchestration, Automation, and Response

A production-quality SOAR demo application built on **[LittleHorse](https://littlehorse.io)** orchestration. Seven microservices coordinate to detect fraud, investigate alerts, verify payment instruments, and manage accounts - all with LittleHorse workflows as the orchestration backbone.

---

## Project Structure

```
SOARdemo/
├── build.gradle                     # Root Gradle build (Gradle 9.1.0, Shadow 9.0.0-beta4)
├── settings.gradle                  # Multi-module project config
├── docker-compose.yml               # Full stack deployment
│
├── logs-api/                        # Port 7001 - Log ingestion & alert management
├── core-banking-service/            # Port 7002 - Account & user data
├── admin-ui/                        # Port 7003 - Admin dashboard SPA
├── fraud-detection-engine/          # Port 7004 - Fraud scoring engine
├── verification-service/            # Port 7005 - Card & check verification
├── data-generation-service/         # Port 7006 - Realistic event generator
└── workflows/                       # LittleHorse task workers & WfSpec registration
```

Each module follows standard Java/Gradle layout:
```
<module>/
├── build.gradle
├── Dockerfile
└── src/main/java/com/example/soar/
    └── Main.java
```

---

## Services

### Logs API (`:7001`)
Stores and retrieves banking event logs. On startup, ~600 historical log entries are pre-loaded from `src/main/resources/logs.json`.

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/logs` | Ingest log entries (JSON array) |
| `GET` | `/logs` | Search logs with filters: `username`, `event`, `severity`, `country`, `ipAddress`, `limit`, `offset` |
| `GET` | `/logs/count` | Total log count |
| `GET` | `/alerts` | List alerts (filter: `status`, `severity`) |
| `POST` | `/alerts` | Create a new alert |
| `PATCH` | `/alerts/{id}/status` | Update alert status (`OPEN` / `INVESTIGATING` / `CLOSED`) |

---

### Core Banking Service (`:7002`)
Manages 3,000 accounts and 2,000 users, pre-loaded from JSON flat files on boot. No external database.

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/accounts` | List/search accounts (`username`, `frozen`, `limit`) |
| `GET` | `/accounts/count` | Total account count |
| `GET` | `/accounts/{username}` | Get account details |
| `POST` | `/accounts/{username}/freeze` | Freeze an account |
| `POST` | `/accounts/{username}/unfreeze` | Unfreeze an account |
| `GET` | `/users/{userId}` | Get user details |

Account fields: `username`, `accountNumber`, `balance`, `previousCountryOfOrigin`, `email`, `phone`, `cardNumber`, `frozen`.

---

### Admin UI (`:7003`)
A dark-themed single-page application with a Javalin backend. The backend proxies requests to all other services and communicates directly with LittleHorse via gRPC for workflow control.

**Tabs:**
- **Dashboard** - Live stats (open alerts, log count, frozen accounts, active WfRuns) + live event feed, auto-refreshes every 10s
- **Alerts** - Filter by status/severity, launch investigation workflows, send analyst decisions
- **Log Search** - Search by username, event type, severity, country, or IP
- **Accounts** - Search accounts, freeze/unfreeze directly or trigger a workflow
- **Workflows** - View all LittleHorse WfRuns, send `ANALYST_DECISION` ExternalEvents

---

### Fraud Detection Engine (`:7004`)
Stateless HTTP scoring service. Returns a fraud score (0.0-1.0) and risk level.

**Scoring rules:**
- Amount > $5,000: +0.40 | > $2,000: +0.25 | > $500: +0.10
- IP country differs from account's home country: +0.35
- Transaction country differs from account home country: +0.20
- High-risk IP ranges (Russian/Eastern European prefixes): +0.20
- High-risk event type (`transfer`, `wire`): +0.10

**Risk levels:** `LOW` (<0.35), `MEDIUM` (0.35-0.60), `HIGH` (0.60-0.80), `CRITICAL` (>0.80)

**Endpoint:** `POST /detect` - body: `{ username, amount, ipAddress, country, event, previousCountryOfOrigin }`

---

### Verification Service (`:7005`)
Validates payment instruments and delegates account freeze/unfreeze to the core banking service.

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/verify/card` | Validate card number (Luhn check, 13-19 digits) + check account is not frozen |
| `POST` | `/verify/check` | Validate routing (9 digits) + check number (4-10 digits) |
| `POST` | `/accounts/{username}/freeze` | Proxy to core banking |
| `POST` | `/accounts/{username}/unfreeze` | Proxy to core banking |

---

### Data Generation Service (`:7006`)
Continuously generates realistic banking events using the 3,000 accounts as a data pool.

**Behavior:**
- Every **3 seconds**: generates a random event (login, purchase, transfer, balance-inquiry, etc.)
- 15% of events are "suspicious" (foreign IP, mismatched country)
- Every **30 seconds**: generates a guaranteed high-fraud event (foreign IP + $3,000-$8,000 amount) for demo visibility
- When a suspicious purchase/transfer is generated, it uses the **LittleHorse Java client** to trigger a `fraud-alert-workflow` WfRun directly

---

### Workflows (LittleHorse Orchestration Brain)
On startup, this service:
1. Registers all **4 WfSpecs** with the LittleHorse server
2. Starts **9 LHTaskWorkers** - one per TaskDef

**TaskDefs (task workers):**

| TaskDef | What it does |
|---------|-------------|
| `get-account-info` | HTTP GET to core-banking, returns account JSON |
| `get-fraud-score` | Merges account context into transaction, calls fraud engine |
| `freeze-account` | HTTP POST to core-banking freeze endpoint |
| `unfreeze-account` | HTTP POST to core-banking unfreeze endpoint |
| `post-alert` | Creates an alert via the logs-api |
| `post-log-entry` | Appends a log entry via the logs-api |
| `verify-card` | Calls verification service card endpoint |
| `verify-check` | Calls verification service check endpoint |
| `send-notification` | Simulates email/SMS/Slack (logs to stdout; swap for real provider) |

---

## LittleHorse Workflows

### `fraud-alert-workflow`
Triggered by `data-generation-service` when a suspicious event is detected.

```
[get-account-info] -> [get-fraud-score] -> [post-log-entry]
                                              |
                      score > 0.60: [freeze-account] + [post-alert CRITICAL] + [send-notification]
                      score 0.35-0.60: [post-alert MEDIUM] + [send-notification]
```

Uses: conditionals, `TaskNodeOutput.withRetries(2)`, chained task outputs as inputs.

---

### `alert-investigation-workflow`
Triggered manually from the Admin UI. Demonstrates **LittleHorse ExternalEvents** - the workflow pauses indefinitely waiting for an analyst decision.

```
[send-notification ESCALATED] -> [post-log-entry] -> WAIT for ExternalEvent "ANALYST_DECISION"
                                                           |
                                       decision=FREEZE: [freeze-account] + [post-log] + [send-notification]
                                       decision=CLOSE:  [post-log] + [send-notification]
```

Uses: `wf.waitForEvent("ANALYST_DECISION")`, `PutExternalEventRequest` from admin-ui.

---

### `transaction-verification-workflow`
```
verifyType=CARD:  [verify-card]  -> assign result -> [post-log-entry]
verifyType=CHECK: [verify-check] -> assign result -> [post-log-entry]
```

Uses: conditional branching, `wf.mutate()` with `VariableMutationType.ASSIGN`.

---

### `account-freeze-workflow`
Standalone compliance-triggered freeze:
```
[freeze-account x3 retries] -> [post-alert HIGH] -> [post-log-entry] -> [send-notification]
```

---

## Starting the Demo

### Prerequisites
- Docker and Docker Compose
- (Optional) JDK 25 + Gradle 9.1.0 for local builds

### Quick Start

```bash
# Clone and start all 8 services (LH server + 7 microservices)
cd SOARdemo
docker compose up --build
```

Services start up in dependency order. The `workflows` service waits for LH server to be healthy before registering WfSpecs and starting task workers.

### Ports

| URL | Service |
|-----|---------|
| http://localhost:7003 | Admin UI (SOAR Command Center) |
| http://localhost:8080 | LittleHorse Server Dashboard |
| http://localhost:7001 | Logs API |
| http://localhost:7002 | Core Banking API |
| http://localhost:7004 | Fraud Detection API |
| http://localhost:7005 | Verification API |
| http://localhost:7006 | Data Generation status |

### Local Build (without Docker)

```bash
# Build all shadow JARs
./gradlew shadowJar -x test

# Run individual services (set env vars for service URLs)
java -jar logs-api/build/libs/logs-api-all.jar
java -jar core-banking-service/build/libs/core-banking-service-all.jar
# ... etc

# Workflows service - point to a running LH server
LHW_SERVER_HOST=localhost LHW_SERVER_PORT=2023 \
  java -jar workflows/build/libs/workflows-all.jar
```

### LittleHorse Configuration

The `workflows`, `admin-ui`, and `data-generation-service` connect to LittleHorse using `LHConfig`, which reads from `littlehorse.config` or environment variables:

| Env Var | Default | Description |
|---------|---------|-------------|
| `LHW_SERVER_HOST` | `localhost` | LH server hostname |
| `LHW_SERVER_PORT` | `2023` | LH server gRPC port |

### Demo Flow

1. Open **http://localhost:7003** - the Admin UI
2. Watch the **Dashboard** - events appear in the live feed every 3 seconds
3. After ~30 seconds, a high-fraud event triggers a `fraud-alert-workflow` WfRun automatically
4. Go to **Alerts** tab - click **Investigate** on a `CRITICAL` alert to launch an `alert-investigation-workflow`
5. Go to **Workflows** tab - find the running investigation workflow, click **Send Decision**
6. Choose **Freeze Account** or **Close - No Action** - this sends a LittleHorse `ANALYST_DECISION` ExternalEvent
7. The workflow unpauses and executes the appropriate branch
8. Go to **Accounts** tab - search for the username to confirm the account state

---

## Technology Stack

| Technology | Version | Role |
|------------|---------|------|
| LittleHorse | 1.0.1 | Workflow orchestration, task scheduling, ExternalEvents |
| Java | 17 | Application language |
| Javalin | 5.6.3 | HTTP API framework (all services) |
| Jackson | 2.17.1 | JSON serialization |
| Unirest | 3.14.5 | HTTP client for inter-service calls |
| Gradle | 9.1.0 | Build system |
| Shadow Plugin | 9.0.0-beta4 | Fat JAR packaging |
| Docker Compose | v3.9 | Container orchestration |
| Eclipse Temurin | 17 | Docker base image (JRE) |

---

## Data

All data is stored in JSON flat files - no external database required.

| File | Contents | Location |
|------|----------|----------|
| `accounts.json` | 3,000 accounts | `core-banking-service/src/main/resources/` |
| `users.json` | 2,000 users | `core-banking-service/src/main/resources/` |
| `logs.json` | ~600 historical log entries | `logs-api/src/main/resources/` |
| `accounts.json` | copy for IP generation | `data-generation-service/src/main/resources/` |
