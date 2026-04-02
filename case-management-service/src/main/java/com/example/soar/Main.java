package com.example.soar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Case Management Service
 * Tracks suspected fraud cases and stores them in a flat JSON file.
 */
public class Main {
    private static final String DATA_FILE = "cases.json";
    static final Map<String, Case> cases = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule());
    }

    public static void main(String[] args) {
        loadCases();

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
        }).start(7007);

        // GET /api/cases - List all cases
        app.get("/api/cases", ctx -> {
            String status = ctx.queryParam("status");
            String username = ctx.queryParam("username");

            List<Case> filtered = cases.values().stream()
                .filter(c -> status == null || c.getStatus().equalsIgnoreCase(status))
                .filter(c -> username == null || c.getUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(Case::getCreatedAt).reversed())
                .collect(Collectors.toList());

            ctx.json(filtered);
        });

        // POST /api/cases - Create a new case
        app.post("/api/cases", ctx -> {
            Map<String, String> body = mapper.readValue(ctx.body(), new TypeReference<Map<String, String>>() {});
            String username = body.get("username");
            String reason = body.get("reason");
            String sourceIp = body.get("sourceIp");
            String details = body.get("details");

            String id = "CASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Case newCase = new Case(id, username, reason, sourceIp, details);
            
            // Check for past cases for this user
            long pastCasesCount = cases.values().stream()
                .filter(c -> c.getUsername().equalsIgnoreCase(username))
                .count();
            
            if (pastCasesCount > 0) {
                newCase.setDetails(newCase.getDetails() + " | Note: User has " + pastCasesCount + " historical case(s).");
            }

            cases.put(id, newCase);
            saveCases();
            
            System.out.println("[case-management] Created new case: " + id + " for user: " + username);
            ctx.status(201).json(newCase);
        });

        // POST /api/cases/{id}/close - Close a case
        app.post("/api/cases/{id}/close", ctx -> {
            String id = ctx.pathParam("id");
            Case c = cases.get(id);
            if (c == null) {
                ctx.status(404).result("Case not found");
                return;
            }

            Map<String, String> body = mapper.readValue(ctx.body(), new TypeReference<Map<String, String>>() {});
            String resolution = body.getOrDefault("resolution", "RESOLVED");

            c.setStatus("CLOSED");
            c.setClosedAt(Instant.now());
            c.setResolution(resolution);
            
            saveCases();
            System.out.println("[case-management] Closed case: " + id + " with resolution: " + resolution);
            ctx.json(c);
        });

        // POST /api/cases/{id}/note - Add a note to a case
        app.post("/api/cases/{id}/note", ctx -> {
            String id = ctx.pathParam("id");
            Case c = cases.get(id);
            if (c == null) {
                ctx.status(404).result("Case not found");
                return;
            }

            Map<String, String> body = mapper.readValue(ctx.body(), new TypeReference<Map<String, String>>() {});
            String note = body.get("note");
            if (note != null) {
                c.setDetails(c.getDetails() + "\n[Note " + Instant.now() + "]: " + note);
                saveCases();
                System.out.println("[case-management] Added note to case: " + id);
                ctx.json(c);
            } else {
                ctx.status(400).result("Note content is required");
            }
        });
    }

    private static void loadCases() {
        try {
            File file = new File(DATA_FILE);
            if (file.exists()) {
                Case[] loaded = mapper.readValue(file, Case[].class);
                for (Case c : loaded) {
                    cases.put(c.getId(), c);
                }
                System.out.println("[case-management] Loaded " + cases.size() + " cases from data file.");
            }
        } catch (Exception e) {
            System.err.println("[case-management] Failed to load cases: " + e.getMessage());
        }
    }

    private static void saveCases() {
        try {
            mapper.writeValue(new File(DATA_FILE), cases.values());
        } catch (Exception e) {
            System.err.println("[case-management] Failed to save cases: " + e.getMessage());
        }
    }
}
