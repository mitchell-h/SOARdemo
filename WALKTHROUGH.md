# SOAR Demo | LittleHorse Orchestration Walkthrough

Welcome to the **Security Orchestration, Automation, and Response (SOAR)** demo. This application showcases how **LittleHorse.io** provides a high-performance orchestration layer to coordinate disparate microservices in response to security events.

## The Story
In this demo, we simulate a banking environment where a **Data Generation Service** produces a stream of transaction logs. Some of these transactions are suspicious. LittleHorse orchestrates the entire lifecycle of a fraud alert: from detection and scoring to automatic mitigation (freezing accounts) and human-in-the-loop investigation.

---

## Project Architecture
The demo consists of several Java-based microservices:
- **Core Banking**: Manages user accounts and balances.
- **Fraud Engine**: Scores transactions for fraud risk.
- **Logs API**: Searchable historical log storage.
- **Case Management**: Tracks suspected fraud cases.
- **Verification Service**: Validates credit cards and checks.
- **Admin UI**: A dashboard to monitor logs, alerts, and workflows.

**All inter-service communication is orchestrated by LittleHorse.**

---

## LittleHorse Features in Action

### 1. Robust Orchestration
Instead of microservices calling each other in a fragile "spaghetti" of REST calls, LittleHorse defines the logic in **WorkflowDefinitions.java**. 
- **Benefit**: The business logic is centralized and decoupled from the services themselves.

### 2. Built-in Resiliency (Retries)
Security actions like freezing an account are critical. 
- **How it works**: In `fraud-alert-workflow`, the `freeze-account` task is configured with `.withRetries(3)`. If the Core Banking service is temporarily down, LittleHorse automatically retries.
- **Benefit**: No lost events or failed mitigations due to transient network issues.

### 3. Human-in-the-Loop (External Events)
Not all fraud can be handled automatically.
- **The Flow**: High-risk alerts are escalated to the `alert-investigation-workflow`. This workflow **pauses** using `wf.waitForEvent("ANALYST_DECISION")`.
- **The Demo**: In the Admin UI, an analyst can review the case and click "Freeze Account" or "Close Case". This sends an `ExternalEvent` to LittleHorse, which then resumes the workflow to execute the chosen path.
- **Benefit**: Seamlessly blend automated logic with human decision-making.

### 4. Visibility & Auditability
Every step of the workflow is tracked.
- **Admin UI**: View the live status of every workflow run.
- **Logs**: LittleHorse tasks automatically log their progress to the Logs API.

---

## How to Demo the Flow

### Step 1: Start the Engine
Ensure all containers are running:
```bash
docker-compose up -d
```

### Step 2: Access the Dashboard
Open your browser to `http://localhost:7003`.
- You will see a live stream of **Logs** hitting the system.
- Look at the **Alerts** tab. Some will be marked "MEDIUM" or "CRITICAL".

### Step 3: Trigger a Manual Investigation
1. Go to the **Accounts** tab and pick a user.
2. Simulate a high-risk event (or wait for the generator to create one).
3. Find an open Alert and click **"Investigate"**.
4. This starts the `alert-investigation-workflow`.

### Step 4: The Analyst Decision
1. In the **Workflows** tab, you'll see a run waiting for "ANALYST_DECISION".
2. Go to the **Cases** tab. Review the details (merged from Core Banking and Fraud Engine).
3. Click **"Freeze Account"**.
4. Watch as the workflow resumes, calls the Core Banking API to freeze the account, creates a Case Management record, and sends a notification.

---

## Why LittleHorse?
- **Stateful**: LittleHorse remembers where every transaction is in the process.
- **Scalable**: Task workers can be scaled independently of the LittleHorse server.
- **Developer Friendly**: Workflows are defined in pure Java (no complex XML or JSON DSLs).

---
*Created for the SOAR Demo - Built by Expert LittleHorse Developers.*
