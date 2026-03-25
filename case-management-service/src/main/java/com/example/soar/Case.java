package com.example.soar;

import java.time.Instant;

/**
 * Represents a fraud case in the SOAR system.
 */
public class Case {
    private String id;
    private String username;
    private String status; // OPEN, CLOSED
    private String reason;
    private String sourceIp;
    private String details;
    private Instant createdAt;
    private Instant closedAt;
    private String resolution;

    public Case() {}

    public Case(String id, String username, String reason, String sourceIp, String details) {
        this.id = id;
        this.username = username;
        this.reason = reason;
        this.sourceIp = sourceIp;
        this.details = details;
        this.status = "OPEN";
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
