package com.example.soar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.littlehorse.sdk.common.config.LHConfig;
import io.littlehorse.sdk.common.proto.*;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin UI Backend
 *
 * Serves the static SPA from /public and exposes proxy/aggregation endpoints:
 *   GET /api/logs           - proxy to logs-api
 *   GET /api/alerts         - proxy to logs-api alerts
 *   PATCH /api/alerts/{id}  - update alert status
 *   GET /api/accounts       - proxy to core-banking
 *   POST /api/accounts/{u}/freeze   - proxy freeze
 *   POST /api/accounts/{u}/unfreeze - proxy unfreeze
 *   GET /api/workflows      - list LH WfRuns
 *   POST /api/workflows/investigate - trigger alert-investigation-workflow
 *   POST /api/workflows/analyst-decision - send ExternalEvent ANALYST_DECISION
 */
public class Main {

    private static final String LOGS_API_URL     = System.getenv().getOrDefault("LOGS_API_URL",     "http://localhost:7001");
    private static final String CORE_BANKING_URL = System.getenv().getOrDefault("CORE_BANKING_URL", "http://localhost:7002");
    private static final String CASE_MANAGEMENT_URL = System.getenv().getOrDefault("CASE_MANAGEMENT_URL", "http://localhost:7007");

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule());
    }
    private static LittleHorseGrpc.LittleHorseBlockingStub lhStub;

    public static void main(String[] args) {
        initLhClient();

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
            config.staticFiles.add("/public");
        }).start(7003);

        // Add CORS header to all API responses
        app.before("/api/*", ctx -> ctx.header("Access-Control-Allow-Origin", "*"));
        app.get("/api/logs", ctx -> {
            Map<String, Object> params = new LinkedHashMap<>(ctx.queryParamMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                    e -> (Object) e.getValue().get(0))));
            HttpResponse<String> resp = Unirest.get(LOGS_API_URL + "/logs")
                .queryString(params).asString();
            ctx.contentType("application/json").result(resp.getBody());
        });

        app.get("/api/logs/count", ctx -> {
            HttpResponse<String> resp = Unirest.get(LOGS_API_URL + "/logs/count").asString();
            ctx.contentType("application/json").result(resp.getBody());
        });

        // ---- Alerts proxy ----
        app.get("/api/alerts", ctx -> {
            String status = ctx.queryParam("status");
            String severity = ctx.queryParam("severity");
            kong.unirest.GetRequest req = Unirest.get(LOGS_API_URL + "/alerts");
            if (status != null) req = req.queryString("status", status);
            if (severity != null) req = req.queryString("severity", severity);
            HttpResponse<String> resp = req.asString();
            ctx.contentType("application/json").result(resp.getBody());
        });

        app.patch("/api/alerts/{id}/status", ctx -> {
            String id = ctx.pathParam("id");
            HttpResponse<String> resp = Unirest.patch(LOGS_API_URL + "/alerts/" + id + "/status")
                .header("Content-Type", "application/json")
                .body(ctx.body()).asString();
            ctx.status(resp.getStatus());
        });

        // ---- Accounts proxy ----
        app.get("/api/accounts", ctx -> {
            Map<String, Object> params = new LinkedHashMap<>(ctx.queryParamMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                    e -> (Object) e.getValue().get(0))));
            HttpResponse<String> resp = Unirest.get(CORE_BANKING_URL + "/accounts")
                .queryString(params).asString();
            ctx.contentType("application/json").result(resp.getBody());
        });

        app.get("/api/accounts/count", ctx -> {
            HttpResponse<String> resp = Unirest.get(CORE_BANKING_URL + "/accounts/count").asString();
            ctx.contentType("application/json").result(resp.getBody());
        });

        app.get("/api/accounts/{username}", ctx -> {
            HttpResponse<String> resp = Unirest.get(CORE_BANKING_URL + "/accounts/" + ctx.pathParam("username")).asString();
            ctx.status(resp.getStatus()).contentType("application/json").result(resp.getBody());
        });

        app.post("/api/accounts/{username}/freeze", ctx -> {
            HttpResponse<String> resp = Unirest.post(CORE_BANKING_URL + "/accounts/" + ctx.pathParam("username") + "/freeze").asString();
            ctx.status(resp.getStatus());
        });

        app.post("/api/accounts/{username}/unfreeze", ctx -> {
            HttpResponse<String> resp = Unirest.post(CORE_BANKING_URL + "/accounts/" + ctx.pathParam("username") + "/unfreeze").asString();
            ctx.status(resp.getStatus());
        });

        // ---- Cases proxy ----
        app.get("/api/cases", ctx -> {
            HttpResponse<String> resp = Unirest.get(CASE_MANAGEMENT_URL + "/api/cases")
                .queryString(ctx.queryParamMap().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))))
                .asString();
            ctx.contentType("application/json").result(resp.getBody());
        });

        app.post("/api/cases/{id}/close", ctx -> {
            HttpResponse<String> resp = Unirest.post(CASE_MANAGEMENT_URL + "/api/cases/" + ctx.pathParam("id") + "/close")
                .header("Content-Type", "application/json")
                .body(ctx.body()).asString();
            ctx.status(resp.getStatus()).contentType("application/json").result(resp.getBody());
        });

        // ---- LittleHorse Workflow endpoints ----

        // List recent WfRuns across all SOAR workflows
        app.get("/api/workflows", ctx -> {
            if (lhStub == null) { ctx.json(List.of()); return; }
            List<Map<String, Object>> runs = new ArrayList<>();
            for (String wfName : List.of("fraud-alert-workflow", "alert-investigation-workflow",
                    "transaction-verification-workflow", "account-freeze-workflow")) {
                try {
                    SearchWfRunRequest req = SearchWfRunRequest.newBuilder()
                        .setWfSpecName(wfName)
                        .setLimit(20)
                        .build();
                    WfRunIdList list = lhStub.searchWfRun(req);
                    for (WfRunId id : list.getResultsList()) {
                        try {
                            WfRun run = lhStub.getWfRun(id);
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", id.getId());
                            m.put("wfSpecName", wfName);
                            m.put("status", run.getStatus().name());
                            m.put("startTime", run.getStartTime() != null ? run.getStartTime().toString() : "");
                            runs.add(m);
                        } catch (Exception ignore) {}
                    }
                } catch (Exception ignore) {}
            }
            ctx.json(runs);
        });

        // Trigger alert-investigation-workflow for an alert
        app.post("/api/workflows/investigate", ctx -> {
            if (lhStub == null) { ctx.status(503).result("LH not connected"); return; }
            Map<String, Object> body = mapper.readValue(ctx.body(), Map.class);
            String alertId   = (String) body.get("alertId");
            String username  = (String) body.get("username");
            double fraudScore = body.containsKey("fraudScore") ? ((Number) body.get("fraudScore")).doubleValue() : 0.0;

            RunWfRequest req = RunWfRequest.newBuilder()
                .setWfSpecName("alert-investigation-workflow")
                .putVariables("alertId",    VariableValue.newBuilder().setStr(alertId).build())
                .putVariables("username",   VariableValue.newBuilder().setStr(username).build())
                .putVariables("fraudScore", VariableValue.newBuilder().setDouble(fraudScore).build())
                .build();
            WfRun run = lhStub.runWf(req);
            ctx.json(Map.of("wfRunId", run.getId().getId(), "status", run.getStatus().name()));
        });

        // Send ANALYST_DECISION external event to a running investigation workflow
        app.post("/api/workflows/analyst-decision", ctx -> {
            if (lhStub == null) { ctx.status(503).result("LH not connected"); return; }
            Map<String, Object> body = mapper.readValue(ctx.body(), Map.class);
            String wfRunId  = (String) body.get("wfRunId");
            String decision = (String) body.get("decision"); // "FREEZE" or "CLOSE"

            PutExternalEventRequest event = PutExternalEventRequest.newBuilder()
                .setWfRunId(WfRunId.newBuilder().setId(wfRunId).build())
                .setExternalEventDefId(ExternalEventDefId.newBuilder().setName("ANALYST_DECISION").build())
                .setContent(VariableValue.newBuilder().setStr(decision).build())
                .build();
            lhStub.putExternalEvent(event);
            ctx.json(Map.of("result", "Decision '" + decision + "' sent to workflow " + wfRunId));
        });

        System.out.println("[admin-ui] Started on port 7003");
    }

    private static void initLhClient() {
        try {
            LHConfig config = new LHConfig();
            lhStub = config.getBlockingStub();
            System.out.println("[admin-ui] Connected to LittleHorse");
        } catch (Exception e) {
            System.err.println("[admin-ui] LH not available (UI will work without workflow status): " + e.getMessage());
        }
    }
}
