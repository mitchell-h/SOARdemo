# SOAR Demo: Services Overview

This document provides a detailed technical overview of the microservices that make up the SOAR (Security Orchestration, Automation, and Response) demo. Each service is built using Java and the Javalin framework.

---

## Core Infrastructure (LittleHorse)
The SOAR demo relies on a running LittleHorse instance for orchestration.

- **LittleHorse Server (gRPC)**: `2023` - Primary communication port for task workers and admin actions.
- **LittleHorse Dashboard**: `8080` - Web UI for monitoring workflows and task status.

## Architecture Overview (LittleHorse Standpoint)

The diagram below illustrates how LittleHorse orchestrates the interaction between various microservices. The **Workflows Service** hosts task workers that poll the **LittleHorse Server** for jobs, while other services trigger workflows or send external events to influence running processes.

```text
                      +-------------------+
                      |   LH Dashboard    |
                      |    (Port 8080)    |
                      +---------+---------+
                                |
                                v
+----------------+    +-------------------+    +-------------------+
|  Admin UI      |--->|     LH Server     |<---|     Data Gen      |
|  (Port 7003)   |    |    (Port 2023)    |    |   (Port 7006)     |
+----------------+    +---------+---------+    +-------------------+
                                |
                                | (gRPC Poll)
                                v
                      +-------------------+
                      |  Workflows Service|
                      |  (Task Workers)   |
                      +---------+---------+
                                |
         +----------------------+----------------------+
         |            (Invoke REST APIs)               |
         v                      v                      v
+----------------+    +-------------------+    +-------------------+
|    Logs API    |    |   Core Banking    |    |  Fraud Detection  |
|  (Port 7001)   |    |    (Port 7002)    |    |    (Port 7004)    |
+----------------+    +-------------------+    +-------------------+
         |                      |                      |
         +----------+-----------+-----------+----------+
                    |                       |
                    v                       v
          +-------------------+   +-------------------+
          |   Verification    |   |  Case Management  |
          |    (Port 7005)    |   |    (Port 7007)    |
          +-------------------+   +-------------------+
```

---

## 1. Logs API
**Port**: `7001`  
**Description**: The central repository for all system events and security alerts. It supports ingestion, searching, and alert status management.

### Endpoints
| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/logs` | Ingest an array of log entries. |
| `GET` | `/logs` | Search logs with filters (`username`, `event`, `severity`, etc.). |
| `GET` | `/logs/count` | Returns the total number of logs in the system. |
| `POST` | `/alerts` | Create a new security alert. |
| `GET` | `/alerts` | List all alerts (filter by `status`, `severity`, `userId`). |
| `GET` | `/alerts/{id}` | Get details for a specific alert. |
| `PATCH` | `/alerts/{id}/status` | Update an alert's status (e.g., OPEN, INVESTIGATING, CLOSED). |

### Sample Usage
```bash
# Ingest a log entry
curl -X POST http://localhost:7001/logs \
     -H "Content-Type: application/json" \
     -d '[{"username":"james.smith","event":"login","ipAddress":"104.1.2.3","severity":"LOW","timestamp":"2024-03-26T12:00:00Z"}]'

# Search for critical alerts
curl "http://localhost:7001/alerts?severity=CRITICAL"
```

---

## 2. Core Banking Service
**Port**: `7002`  
**Description**: Manages 3,000 accounts and 2,000 users. It serves as the source of truth for account state (frozen/active) and balance information.

### Endpoints
| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/accounts` | List/search accounts (query by `username`, `country`, `frozen`). |
| `GET` | `/accounts/{username}` | Get full account profile. |
| `POST` | `/accounts/{username}/freeze` | Set account status to frozen. |
| `POST` | `/accounts/{username}/unfreeze` | Set account status to active. |
| `GET` | `/users/{userId}` | Get user personal details. |

### Sample Usage
```bash
# Get account details
curl http://localhost:7002/accounts/mitch.heward

# Freeze an account
curl -X POST http://localhost:7002/accounts/mitch.heward/freeze
```

---

## 3. Fraud Detection Engine
**Port**: `7004`  
**Description**: A stateless scoring service that evaluates transactions for fraud risk based on rules like amount size, IP origin, and event type.

### Endpoints
| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/detect` | Score a transaction and return risk level (LOW to CRITICAL). |

### Sample Usage
```bash
# Score a transaction
curl -X POST http://localhost:7004/detect \
     -H "Content-Type: application/json" \
     -d '{
       "username": "james.smith",
       "amount": 6000.0,
       "ipAddress": "95.12.3.4",
       "previousCountryOfOrigin": "US",
       "event": "transfer"
     }'
```

---

## 4. Verification Service
**Port**: `7005`  
**Description**: Validates payment instruments (Credit Cards and Checks) and proxies administrative actions to Core Banking.

### Endpoints
| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/verify/card` | Perform Luhn check and account status check for a card. |
| `POST` | `/verify/check` | Validate routing and account numbers for a check. |

### Sample Usage
```bash
# Verify a credit card
curl -X POST http://localhost:7005/verify/card \
     -H "Content-Type: application/json" \
     -d '{"username": "james.smith", "cardNumber": "4111-1111-1111-1111"}'
```

---

## 5. Case Management Service
**Port**: `7007`  
**Description**: Tracks manually and automatically created fraud investigation cases. Stores persistent records of security incidents.

### Endpoints
| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/api/cases` | List all cases (filter by `status`, `username`). |
| `POST` | `/api/cases` | Create a new investigation case. |
| `POST` | `/api/cases/{id}/close` | Close a case with a resolution note. |

### Sample Usage
```bash
# Create a case
curl -X POST http://localhost:7007/api/cases \
     -H "Content-Type: application/json" \
     -d '{"username": "james.smith", "reason": "HIGH_FRAUD_SCORE", "sourceIp": "1.2.3.4", "details": "Suspicious login from RU"}'
```

---

## 6. Admin UI (Backend Proxies)
**Port**: `7003`  
**Description**: While serving the frontend, the backend acts as an API Gateway, aggregating data from all other services and interacting with the LittleHorse gRPC API.

### Key API Endpoints
- `GET /api/logs`: Aggregated logs search.
- `GET /api/workflows`: List active LittleHorse WfRuns.
- `POST /api/workflows/investigate`: Launch an investigation workflow.
- `POST /api/workflows/analyst-decision`: Send an `ANALYST_DECISION` external event.

---

## 7. Data Generation Service
**Port**: `7006`  
**Description**: Continually generates realistic banking logs and triggers LittleHorse workflows for suspicious activity. It has no complex API but exposes a `/health` endpoint for monitoring.
