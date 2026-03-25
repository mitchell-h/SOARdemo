package com.example.soar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Alert {
    private String id;
    private String timestamp;
    private String userId;
    private String username;
    private String alertType;
    private String severity;
    private String message;
    private Double fraudScore;
    private String wfRunId;
    private String status; // OPEN, INVESTIGATING, CLOSED
    private String transactionJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Double getFraudScore() { return fraudScore; }
    public void setFraudScore(Double fraudScore) { this.fraudScore = fraudScore; }
    public String getWfRunId() { return wfRunId; }
    public void setWfRunId(String wfRunId) { this.wfRunId = wfRunId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTransactionJson() { return transactionJson; }
    public void setTransactionJson(String transactionJson) { this.transactionJson = transactionJson; }
}
