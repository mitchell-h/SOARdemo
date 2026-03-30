package com.example.soar;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.littlehorse.sdk.worker.LHTaskMethod;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * All LittleHorse task worker implementations.
 * One class with @LHTaskMethod annotated methods - one per registered TaskDef.
 *
 * TaskDefs registered:
 *   get-account-info, get-fraud-score, freeze-account, unfreeze-account,
 *   post-alert, post-log-entry, verify-card, verify-check, send-notification,
 *   create-case, close-case, get-alert-details, get-user-logs,
 *   check-global-failure, find-open-case, add-case-note, fail-workflow
 */
public class TaskWorkers {

    private static final String CORE_BANKING_URL   = System.getenv().getOrDefault("CORE_BANKING_URL",   "http://localhost:7002");
    private static final String FRAUD_ENGINE_URL   = System.getenv().getOrDefault("FRAUD_ENGINE_URL",   "http://localhost:7004");
    private static final String VERIFICATION_URL   = System.getenv().getOrDefault("VERIFICATION_URL",   "http://localhost:7005");
    private static final String LOGS_API_URL        = System.getenv().getOrDefault("LOGS_API_URL",        "http://localhost:7001");
    private static final String CASE_MANAGEMENT_URL = System.getenv().getOrDefault("CASE_MANAGEMENT_URL", "http://localhost:7007");

    private static final ObjectMapper mapper = new ObjectMapper();

    // -----------------------------------------------------------------
    // get-account-info: returns JSON string of the account
    // -----------------------------------------------------------------
    @LHTaskMethod("get-account-info")
    public String getAccountInfo(String username) {
        HttpResponse<String> resp = Unirest.get(CORE_BANKING_URL + "/accounts/" + username).asString();
        if (resp.isSuccess()) {
            System.out.println("[task:get-account-info] fetched account for: " + username);
            return resp.getBody();
        }
        throw new RuntimeException("Account not found: " + username + " (status=" + resp.getStatus() + ")");
    }

    // -----------------------------------------------------------------
    // get-alert-details: returns JSON string of the alert
    // -----------------------------------------------------------------
    @LHTaskMethod("get-alert-details")
    public String getAlertDetails(String alertId) {
        HttpResponse<String> resp = Unirest.get(LOGS_API_URL + "/alerts/" + alertId).asString();
        if (resp.isSuccess()) {
            System.out.println("[task:get-alert-details] fetched alert: " + alertId);
            return resp.getBody();
        }
        throw new RuntimeException("Alert not found: " + alertId + " (status=" + resp.getStatus() + ")");
    }

    // -----------------------------------------------------------------
    // get-user-logs: returns JSON string of the last 10 logs for a user
    // -----------------------------------------------------------------
    @LHTaskMethod("get-user-logs")
    public String getUserLogs(String username) {
        HttpResponse<String> resp = Unirest.get(LOGS_API_URL + "/logs")
            .queryString("username", username)
            .queryString("limit", 10)
            .asString();
        if (resp.isSuccess()) {
            System.out.println("[task:get-user-logs] fetched logs for: " + username);
            return resp.getBody();
        }
        return "[]";
    }

    // -----------------------------------------------------------------
    // get-fraud-score: calls fraud detection engine, returns score (double)
    // -----------------------------------------------------------------
    @LHTaskMethod("get-fraud-score")
    public double getFraudScore(String transactionJson, String accountJson) {
        try {
            Map<String, Object> tx = mapper.readValue(transactionJson, Map.class);
            Map<String, Object> account = mapper.readValue(accountJson, Map.class);

            // Merge account context into the transaction payload for scoring
            Map<String, Object> payload = new HashMap<>(tx);
            payload.put("previousCountryOfOrigin", account.getOrDefault("previousCountryOfOrigin", ""));

            HttpResponse<JsonNode> resp = Unirest.post(FRAUD_ENGINE_URL + "/detect")
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(payload))
                .asJson();

            if (resp.isSuccess()) {
                double score = resp.getBody().getObject().getDouble("score");
                System.out.println("[task:get-fraud-score] score=" + score);
                return score;
            }
        } catch (Exception e) {
            System.err.println("[task:get-fraud-score] error: " + e.getMessage());
        }
        return 0.0;
    }

    // -----------------------------------------------------------------
    // freeze-account
    // -----------------------------------------------------------------
    @LHTaskMethod("freeze-account")
    public String freezeAccount(String username) {
        HttpResponse<String> resp = Unirest.post(CORE_BANKING_URL + "/accounts/" + username + "/freeze").asString();
        System.out.println("[task:freeze-account] " + username + " -> " + resp.getStatus());
        if (resp.getStatus() == 204) {
            return "SUCCESS";
        }
        return "FAILED";
    }

    // -----------------------------------------------------------------
    // unfreeze-account
    // -----------------------------------------------------------------
    @LHTaskMethod("unfreeze-account")
    public String unfreezeAccount(String username) {
        HttpResponse<String> resp = Unirest.post(CORE_BANKING_URL + "/accounts/" + username + "/unfreeze").asString();
        System.out.println("[task:unfreeze-account] " + username + " -> " + resp.getStatus());
        return resp.getStatus() == 204 ? "UNFROZEN" : "ERROR_" + resp.getStatus();
    }

    // -----------------------------------------------------------------
    // post-alert: creates a new alert in the logs-api
    // -----------------------------------------------------------------
    @LHTaskMethod("post-alert")
    public String postAlert(String username, double fraudScore, String transactionJson, String severity) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("username", username);
            alert.put("fraudScore", fraudScore);
            alert.put("severity", severity);
            alert.put("alertType", fraudScore > 0.60 ? "FRAUD_DETECTION" : "SUSPICIOUS_ACTIVITY");
            alert.put("message", "Fraud score " + String.format("%.2f", fraudScore) + " detected for account " + username);
            alert.put("status", "OPEN");
            alert.put("transactionJson", transactionJson);
            alert.put("timestamp", Instant.now().toString());

            HttpResponse<String> resp = Unirest.post(LOGS_API_URL + "/alerts")
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(alert))
                .asString();
            System.out.println("[task:post-alert] created alert for " + username + " score=" + fraudScore);
            return resp.getBody();
        } catch (Exception e) {
            System.err.println("[task:post-alert] error: " + e.getMessage());
            return "ERROR";
        }
    }

    // -----------------------------------------------------------------
    // post-log-entry: adds a log entry to the logs-api
    // -----------------------------------------------------------------
    @LHTaskMethod("post-log-entry")
    public String postLogEntry(String username, String event, String detail) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("username", username);
            logEntry.put("event", event);
            logEntry.put("status", "success");
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("severity", "LOW");
            logEntry.put("wfRunId", detail);

            HttpResponse<String> resp = Unirest.post(LOGS_API_URL + "/logs")
                .header("Content-Type", "application/json")
                .body("[" + mapper.writeValueAsString(logEntry) + "]")
                .asString();
            return resp.getStatus() == 201 ? "OK" : "ERROR_" + resp.getStatus();
        } catch (Exception e) {
            System.err.println("[task:post-log-entry] error: " + e.getMessage());
            return "ERROR";
        }
    }

    // -----------------------------------------------------------------
    // verify-card
    // -----------------------------------------------------------------
    @LHTaskMethod("verify-card")
    public String verifyCard(String username, String paymentDataJson) {
        try {
            Map<String, Object> payload = mapper.readValue(paymentDataJson, Map.class);
            payload.put("username", username);

            HttpResponse<JsonNode> resp = Unirest.post(VERIFICATION_URL + "/verify/card")
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(payload))
                .asJson();

            boolean valid = resp.getBody().getObject().optBoolean("valid", false);
            System.out.println("[task:verify-card] " + username + " valid=" + valid);
            return valid ? "VALID" : "INVALID";
        } catch (Exception e) {
            System.err.println("[task:verify-card] error: " + e.getMessage());
            return "ERROR";
        }
    }

    // -----------------------------------------------------------------
    // verify-check
    // -----------------------------------------------------------------
    @LHTaskMethod("verify-check")
    public String verifyCheck(String username, String paymentDataJson) {
        try {
            Map<String, Object> payload = mapper.readValue(paymentDataJson, Map.class);
            payload.put("username", username);

            HttpResponse<JsonNode> resp = Unirest.post(VERIFICATION_URL + "/verify/check")
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(payload))
                .asJson();

            boolean valid = resp.getBody().getObject().optBoolean("valid", false);
            System.out.println("[task:verify-check] " + username + " valid=" + valid);
            return valid ? "VALID" : "INVALID";
        } catch (Exception e) {
            System.err.println("[task:verify-check] error: " + e.getMessage());
            return "ERROR";
        }
    }

    // -----------------------------------------------------------------
    // send-notification: simulates email/SMS/Slack notification
    // -----------------------------------------------------------------
    @LHTaskMethod("send-notification")
    public String sendNotification(String username, String notificationType, String detail) {
        String msg = String.format("[NOTIFICATION] User=%s Type=%s Detail=%s Time=%s",
            username, notificationType, detail, Instant.now());
        System.out.println(msg);
        // In production this would call an email/SMS/Slack API
        return "SENT";
    }
    // -----------------------------------------------------------------
    // create-case
    // -----------------------------------------------------------------
    @LHTaskMethod("create-case")
    public String createCase(String username, String reason, String sourceIp, String details) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("username", username);
            payload.put("reason", reason);
            payload.put("sourceIp", sourceIp);
            payload.put("details", details);

            HttpResponse<JsonNode> resp = Unirest.post(CASE_MANAGEMENT_URL + "/api/cases")
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(payload))
                .asJson();

            if (resp.isSuccess()) {
                String caseId = resp.getBody().getObject().getString("id");
                System.out.println("[task:create-case] created case " + caseId + " for " + username);
                return caseId;
            }
        } catch (Exception e) {
            System.err.println("[task:create-case] error: " + e.getMessage());
        }
        return "ERROR";
    }

    // -----------------------------------------------------------------
    // close-case
    // -----------------------------------------------------------------
    @LHTaskMethod("close-case")
    public String closeCase(String caseId, String resolution) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("resolution", resolution);

            HttpResponse<JsonNode> resp = Unirest.post(CASE_MANAGEMENT_URL + "/api/cases/" + caseId + "/close")
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(payload))
                .asJson();

            if (resp.isSuccess()) {
                System.out.println("[task:close-case] closed case " + caseId);
                return "CLOSED";
            }
        } catch (Exception e) {
            System.err.println("[task:close-case] error: " + e.getMessage());
        }
        return "ERROR";
    }

    // -----------------------------------------------------------------
    // check-global-failure: 20% chance of returning "FAIL"
    // -----------------------------------------------------------------
    @LHTaskMethod("check-global-failure")
    public String checkGlobalFailure() {
        if (new Random().nextInt(5) == 0) {
            System.out.println("[task:check-global-failure] Global failure triggered!");
            return "FAIL";
        }
        return "PASS";
    }

    // -----------------------------------------------------------------
    // find-open-case: returns caseId or "NONE"
    // -----------------------------------------------------------------
    @LHTaskMethod("find-open-case")
    public String findOpenCase(String username) {
        HttpResponse<JsonNode> resp = Unirest.get(CASE_MANAGEMENT_URL + "/api/cases")
            .queryString("username", username)
            .queryString("status", "OPEN")
            .asJson();
        
        if (resp.isSuccess() && resp.getBody().getArray().length() > 0) {
            String caseId = resp.getBody().getArray().getJSONObject(0).getString("id");
            System.out.println("[task:find-open-case] found open case " + caseId + " for " + username);
            return caseId;
        }
        return "NONE";
    }

    // -----------------------------------------------------------------
    // add-case-note
    // -----------------------------------------------------------------
    @LHTaskMethod("add-case-note")
    public String addCaseNote(String caseId, String note) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("note", note);

            HttpResponse<String> resp = Unirest.post(CASE_MANAGEMENT_URL + "/api/cases/" + caseId + "/note")
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(payload))
                .asString();
            
            if (resp.getStatus() == 200) {
                System.out.println("[task:add-case-note] added note to case " + caseId);
                return "OK";
            }
        } catch (Exception e) {
            System.err.println("[task:add-case-note] error: " + e.getMessage());
        }
        return "ERROR";
    }

    // -----------------------------------------------------------------
    // fail-workflow: forces a failure in the workflow run
    // -----------------------------------------------------------------
    @LHTaskMethod("fail-workflow")
    public void failWorkflow(String reason) {
        System.err.println("[task:fail-workflow] Failing workflow: " + reason);
        throw new RuntimeException(reason);
    }
}
