# SOAR Demo Quick Start

Welcome to the **SOAR (Security Orchestration, Automation, and Response)** demo! This project showcases a banking fraud detection system orchestrated by **[LittleHorse.io](https://littlehorse.io)**.

## Prerequisites

- **Docker** and **Docker Compose**
- **Java 17+** (if running locally without Docker)
- **Gradle 9.1+** (if building from source)

---

## 1. Start the Demo

The easiest way to run the entire stack (8 services) is via Docker Compose:

```bash
# From the project root
docker compose up --build
```

Wait until you see messages indicating that the `workflows` service has registered its `WfSpecs` and started its task workers.

---

## 2. Access the Command Center

Once everything is up, open your browser to:

| Service | URL | Description |
| :--- | :--- | :--- |
| **Admin UI** | [http://localhost:7003](http://localhost:7003) | **Primary Demo Entrypoint** |
| **LH Dashboard** | [http://localhost:8080](http://localhost:8080) | LittleHorse Server UI |
| **Logs API** | [http://localhost:7001](http://localhost:7001) | Raw Log/Alert Data |
| **Core Banking** | [http://localhost:7002](http://localhost:7002) | Account/User Data |
| **Case Management** | [http://localhost:7007](http://localhost:7007) | Fraud Case Tracking |

---

## 3. Core Demo Flow

### Phase 1: Witness Automation
1. Open the [Admin UI](http://localhost:7003) to the **Dashboard**.
2. Watch the live log feed. Every 3 seconds, a new banking event is generated.
3. Every ~30 seconds, a "High Fraud" event is intentionally triggered.
4. Watch as LittleHorse automatically executes a `fraud-alert-workflow`, scores the risk, and potentially freezes the account without human intervention.

### Phase 2: Human-in-the-Loop
1. Go to the **Alerts** tab in the Admin UI.
2. Find a `CRITICAL` or `HIGH` alert and click **Investigate**.
3. This launches an `alert-investigation-workflow` which **pauses** waiting for your decision.
4. Go to the **Workflows** tab to see the paused run.
5. Go to the **Cases** tab, review the data, and click **Freeze Account**.
6. Watch the workflow resume and complete the mitigation.

---

## 4. Project Structure (Microservices)

- `admin-ui`: The central SOAR dashboard.
- `workflows`: The "brain" containing LittleHorse task workers and workflow logic.
- `core-banking-service`: Stores 3,000 accounts and 2,000 users.
- `fraud-detection-engine`: Scores transactions for risk.
- `logs-api`: Stores historical logs and active alerts.
- `case-management-service`: Tracks security incidents.
- `data-generation-service`: Simulates live user traffic.
- `verification-service`: Validates payment methods.

---

## 5. Troubleshooting

- **Containers failing to start**: Ensure ports 7001-7007 and 8080/2023 are not in use.
- **No data in UI**: Check the `lh-server` health at [http://localhost:8080/liveness](http://localhost:8080/liveness).
- **Workflows not running**: Verify that the `workflows` container output does not show gRPC connection errors.

---
*Built by the LittleHorse Team.*
