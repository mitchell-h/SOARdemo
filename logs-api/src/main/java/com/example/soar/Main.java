package com.example.soar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Main {

    static final List<LogEntry> logs = new CopyOnWriteArrayList<>();
    static final List<Alert> alerts = new CopyOnWriteArrayList<>();
    private static final AtomicInteger alertCounter = new AtomicInteger(1);
    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule());
    }

    public static void main(String[] args) {
        loadLogs();

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
        }).start(7001);

        // POST /logs - accept new log entries
        app.post("/logs", ctx -> {
            List<LogEntry> newLogs = mapper.readValue(ctx.body(),
                mapper.getTypeFactory().constructCollectionType(List.class, LogEntry.class));
            logs.addAll(newLogs);
            ctx.status(201);
        });

        // GET /logs - search logs with filters + pagination
        app.get("/logs", ctx -> {
            String userId = ctx.queryParam("userId");
            String username = ctx.queryParam("username");
            String event = ctx.queryParam("event");
            String ipAddress = ctx.queryParam("ipAddress");
            String status = ctx.queryParam("status");
            String severity = ctx.queryParam("severity");
            String country = ctx.queryParam("country");
            String after = ctx.queryParam("after");
            String before = ctx.queryParam("before");
            String limitStr = ctx.queryParam("limit");
            String offsetStr = ctx.queryParam("offset");

            int limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
            int offset = offsetStr != null ? Integer.parseInt(offsetStr) : 0;

            Instant afterInst = after != null ? Instant.parse(after) : null;
            Instant beforeInst = before != null ? Instant.parse(before) : null;

            List<LogEntry> filtered = logs.stream()
                .filter(l -> userId == null || userId.equals(l.getUserId()))
                .filter(l -> username == null || username.equals(l.getUsername()))
                .filter(l -> event == null || event.equals(l.getEvent()))
                .filter(l -> ipAddress == null || ipAddress.equals(l.getIpAddress()))
                .filter(l -> status == null || status.equals(l.getStatus()))
                .filter(l -> severity == null || severity.equals(l.getSeverity()))
                .filter(l -> country == null || country.equals(l.getCountry()))
                .filter(l -> {
                    if (afterInst == null && beforeInst == null) return true;
                    try {
                        Instant ts = Instant.parse(l.getTimestamp());
                        if (afterInst != null && ts.isBefore(afterInst)) return false;
                        if (beforeInst != null && ts.isAfter(beforeInst)) return false;
                        return true;
                    } catch (Exception e) { return true; }
                })
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

            ctx.json(filtered);
        });

        // GET /logs/count
        app.get("/logs/count", ctx -> ctx.json(Map.of("count", logs.size())));

        // POST /alerts - create a new alert
        app.post("/alerts", ctx -> {
            Alert alert = mapper.readValue(ctx.body(), Alert.class);
            if (alert.getId() == null) {
                alert.setId("alert-" + String.format("%05d", alertCounter.getAndIncrement()));
            }
            if (alert.getTimestamp() == null) {
                alert.setTimestamp(Instant.now().toString());
            }
            if (alert.getStatus() == null) {
                alert.setStatus("OPEN");
            }
            alerts.add(alert);
            ctx.status(201).json(alert);
        });

        app.get("/alerts", ctx -> {
            String id = ctx.queryParam("id");
            String status = ctx.queryParam("status");
            String severity = ctx.queryParam("severity");
            String userId = ctx.queryParam("userId");

            List<Alert> filtered = alerts.stream()
                .filter(a -> id == null || id.isEmpty() || id.equals(a.getId()))
                .filter(a -> (status == null || status.isEmpty()) ? !"CLOSED".equals(a.getStatus()) : status.equals(a.getStatus()))
                .filter(a -> severity == null || severity.equals(a.getSeverity()))
                .filter(a -> userId == null || userId.equals(a.getUserId()))
                .sorted(Comparator.comparing(Alert::getTimestamp).reversed())
                .collect(Collectors.toList());

            ctx.json(filtered);
        });

        // GET /alerts/{id}
        app.get("/alerts/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Optional<Alert> alert = alerts.stream().filter(a -> id.equals(a.getId())).findFirst();
            if (alert.isPresent()) {
                ctx.json(alert.get());
            } else {
                ctx.status(404).result("Alert not found");
            }
        });

        // PATCH /alerts/{id}/status
        app.patch("/alerts/{id}/status", ctx -> {
            String id = ctx.pathParam("id");
            Map<String, String> body = mapper.readValue(ctx.body(), Map.class);
            String newStatus = body.get("status");
            alerts.stream().filter(a -> id.equals(a.getId())).findFirst().ifPresent(a -> a.setStatus(newStatus));
            ctx.status(204);
        });

        System.out.println("[logs-api] Started on port 7001 with " + logs.size() + " historical logs");
    }

    private static void loadLogs() {
        try (InputStream in = Main.class.getResourceAsStream("/logs.json")) {
            List<LogEntry> loaded = mapper.readValue(in,
                mapper.getTypeFactory().constructCollectionType(List.class, LogEntry.class));
            logs.addAll(loaded);
            System.out.println("[logs-api] Loaded " + loaded.size() + " historical log entries");
        } catch (Exception e) {
            System.err.println("[logs-api] Failed to load logs: " + e.getMessage());
        }
    }
}
