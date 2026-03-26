package com.example.soar;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.littlehorse.sdk.common.config.LHConfig;
import io.littlehorse.sdk.common.proto.*;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub;
import kong.unirest.Unirest;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data Generation Service
 *
 * On startup: loads 3000 accounts from /accounts.json resource for realistic username pool.
 * Every 3 seconds: generates a realistic banking event (login, purchase, card-verify, etc.)
 * and POSTs it to the logs-api.
 *
 * ~15% of generated events are "suspicious" (foreign IP, high amount, country mismatch).
 * When a suspicious purchase or transfer event is generated, this service uses the
 * LittleHorse client to trigger a fraud-alert-workflow WfRun. LH then orchestrates
 * the downstream fraud check, optional freeze, and alert creation.
 */
public class Main {

    private static final String LOGS_API_URL = System.getenv().getOrDefault("LOGS_API_URL", "http://localhost:7001");
    private static final String LH_ENABLED   = System.getenv().getOrDefault("LH_ENABLED", "true");

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Random random = new Random();
    private static final AtomicInteger logCounter = new AtomicInteger(1);

    // Loaded from accounts.json at startup
    private static final List<Map<String, Object>> accounts = new ArrayList<>();

    // Country -> IP prefixes for generating realistic IPs
    private static final Map<String, String[]> COUNTRY_IPS = Map.of(
        "US", new String[]{"12.","73.","104.","108.","184."},
        "GB", new String[]{"81.","82.","86.","87.","94."},
        "CA", new String[]{"24.","64.","70.","142.","206."},
        "AU", new String[]{"1.","27.","49.","58.","120."},
        "DE", new String[]{"46.","78.","217.","77.","195."},
        "FR", new String[]{"90.","92.","109.","176.","212."},
        "JP", new String[]{"111.","122.","126.","153.","202."},
        "IN", new String[]{"49.","103.","117.","183.","223."},
        "BR", new String[]{"177.","179.","186.","189.","200."},
        "MX", new String[]{"148.","187.","189.","190.","201."}
    );

    private static final List<String> COUNTRY_LIST = new ArrayList<>(COUNTRY_IPS.keySet());
    private static final String[] EVENTS = {"login","logout","purchase","card-verify","check-verify","balance-inquiry","transfer"};
    private static final double[] EVENT_WEIGHTS = {0.20, 0.15, 0.25, 0.10, 0.05, 0.15, 0.10};

    // LH client for triggering workflows
    private static io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub lhStub;

    public static void main(String[] args) throws Exception {
        loadAccounts();
        initLhClient();

        Javalin app = Javalin.create().start(7006);
        app.get("/", ctx -> ctx.result("Data Generation Service running. Loaded " + accounts.size() + " accounts."));
        app.get("/health", ctx -> ctx.json(Map.of("status", "UP", "accounts", accounts.size())));

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        // Generate a new event every 3 seconds
        executor.scheduleAtFixedRate(Main::generateAndPublishEvent, 2, 3, TimeUnit.SECONDS);

        // --- Demo Workflows: Ensure one of each every 30 seconds ---
        
            // 1. High Fraud (triggers fraud-alert-workflow)
            executor.scheduleAtFixedRate(Main::generateHighFraudEvent, 10, 30, TimeUnit.SECONDS);

            // 2. Transaction Verification Workflow
            executor.scheduleAtFixedRate(Main::generateTransactionVerification, 15, 30, TimeUnit.SECONDS);

            // 3. Account Freeze Workflow
            executor.scheduleAtFixedRate(Main::generateAccountFreeze, 20, 30, TimeUnit.SECONDS);

            // 4. Investigation Workflow
            executor.scheduleAtFixedRate(Main::generateInvestigation, 25, 30, TimeUnit.SECONDS);

        System.out.println("[data-gen] Started. Generating events every 3 seconds.");
    }

    private static void generateAndPublishEvent() {
        try {
            if (accounts.isEmpty()) return;
            Map<String, Object> account = accounts.get(random.nextInt(accounts.size()));
            String username  = (String) account.get("username");
            String userId    = (String) account.get("userId");
            String homeCountry = (String) account.getOrDefault("previousCountryOfOrigin", "US");

            // 15% chance of suspicious activity
            boolean suspicious = random.nextDouble() < 0.15;

            String ipCountry  = suspicious
                ? COUNTRY_LIST.get(random.nextInt(COUNTRY_LIST.size()))
                : homeCountry;
            // Make sure suspicious uses a different country
            if (suspicious && ipCountry.equals(homeCountry)) {
                ipCountry = COUNTRY_LIST.stream().filter(c -> !c.equals(homeCountry)).findFirst().orElse("RU");
            }

            String ip = randomIp(ipCountry);
            String event = weightedEvent();

            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("id", "gen-" + String.format("%08d", logCounter.getAndIncrement()));
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("userId", userId);
            logEntry.put("username", username);
            logEntry.put("event", event);
            logEntry.put("ipAddress", ip);
            logEntry.put("country", ipCountry);
            logEntry.put("severity", suspicious ? "HIGH" : "LOW");
            logEntry.put("status", "success");

            double amount = 0;
            if ("purchase".equals(event) || "transfer".equals(event)) {
                amount = suspicious
                    ? 1000 + random.nextDouble() * 7000
                    : 10 + random.nextDouble() * 500;
                amount = Math.round(amount * 100.0) / 100.0;
                logEntry.put("amount", amount);
                logEntry.put("currency", "USD");
            }

            // POST log entry
            Unirest.post(LOGS_API_URL + "/logs")
                .header("Content-Type", "application/json")
                .body("[" + mapper.writeValueAsString(logEntry) + "]")
                .asEmpty();

            // If this is a suspicious purchase or transfer, trigger LH fraud workflow
            if (suspicious && ("purchase".equals(event) || "transfer".equals(event)) && lhStub != null) {
                triggerFraudWorkflow(username, logEntry, ip, amount, ipCountry);
            }

        } catch (Exception e) {
            System.err.println("[data-gen] Error generating event: " + e.getMessage());
        }
    }

    /** Generate a guaranteed high-fraud score event every 30s for demo visibility */
    private static void generateHighFraudEvent() {
        try {
            if (accounts.isEmpty()) return;
            Map<String, Object> account = accounts.get(random.nextInt(accounts.size()));
            String username = (String) account.get("username");
            String userId   = (String) account.get("userId");
            String homeCountry = (String) account.getOrDefault("previousCountryOfOrigin", "US");

            // Definitely foreign IP + high amount
            String foreignCountry = COUNTRY_LIST.stream().filter(c -> !c.equals(homeCountry)).findFirst().orElse("RU");
            String ip = randomIp(foreignCountry);
            double amount = 3000 + random.nextDouble() * 5000;
            amount = Math.round(amount * 100.0) / 100.0;

            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("id", "hf-" + String.format("%08d", logCounter.getAndIncrement()));
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("userId", userId);
            logEntry.put("username", username);
            logEntry.put("event", "purchase");
            logEntry.put("ipAddress", ip);
            logEntry.put("country", foreignCountry);
            logEntry.put("amount", amount);
            logEntry.put("currency", "USD");
            logEntry.put("severity", "CRITICAL");
            logEntry.put("status", "success");

            Unirest.post(LOGS_API_URL + "/logs")
                .header("Content-Type", "application/json")
                .body("[" + mapper.writeValueAsString(logEntry) + "]")
                .asEmpty();

            System.out.println("[data-gen] Triggered high-fraud event for " + username + " amount=" + amount + " country=" + foreignCountry);

            if (lhStub != null) {
                triggerFraudWorkflow(username, logEntry, ip, amount, foreignCountry);
            }

        } catch (Exception e) {
            System.err.println("[data-gen] Error generating high-fraud event: " + e.getMessage());
        }
    }

    /** Periodically trigger a transaction verification workflow */
    private static void generateTransactionVerification() {
        try {
            if (accounts.isEmpty() || lhStub == null) return;
            Map<String, Object> account = accounts.get(random.nextInt(accounts.size()));
            String username = (String) account.get("username");

            RunWfRequest request = RunWfRequest.newBuilder()
                .setWfSpecName("transaction-verification-workflow")
                .putVariables("username", VariableValue.newBuilder().setStr(username).build())
                .putVariables("verifyType", VariableValue.newBuilder().setStr("CARD").build())
                .putVariables("paymentData", VariableValue.newBuilder().setStr("{\"amount\": 49.99, \"vendor\": \"Demo Store\"}").build())
                .build();

            WfRun wfRun = lhStub.runWf(request);
            System.out.println("[data-gen] Triggered transaction-verification-workflow for " + username + " wfRunId=" + wfRun.getId().getId());
        } catch (Exception e) {
            System.err.println("[data-gen] Error triggering transaction-verification: " + e.getMessage());
        }
    }

    /** Periodically trigger an account freeze workflow */
    private static void generateAccountFreeze() {
        try {
            if (accounts.isEmpty() || lhStub == null) return;
            Map<String, Object> account = accounts.get(random.nextInt(accounts.size()));
            String username = (String) account.get("username");

            RunWfRequest request = RunWfRequest.newBuilder()
                .setWfSpecName("account-freeze-workflow")
                .putVariables("username", VariableValue.newBuilder().setStr(username).build())
                .putVariables("reason", VariableValue.newBuilder().setStr("Random Periodic Security Check").build())
                .build();

            WfRun wfRun = lhStub.runWf(request);
            System.out.println("[data-gen] Triggered account-freeze-workflow for " + username + " wfRunId=" + wfRun.getId().getId());
        } catch (Exception e) {
            System.err.println("[data-gen] Error triggering account-freeze: " + e.getMessage());
        }
    }

    /** Periodically trigger an investigation workflow */
    private static void generateInvestigation() {
        try {
            if (accounts.isEmpty() || lhStub == null) return;
            Map<String, Object> account = accounts.get(random.nextInt(accounts.size()));
            String username = (String) account.get("username");
            String alertId = "gen-alert-" + (1000 + random.nextInt(9000));
            double score = 0.5 + random.nextDouble() * 0.4;

            io.littlehorse.sdk.common.proto.RunWfRequest request = io.littlehorse.sdk.common.proto.RunWfRequest.newBuilder()
                .setWfSpecName("alert-investigation-workflow")
                .putVariables("alertId", io.littlehorse.sdk.common.proto.VariableValue.newBuilder().setStr(alertId).build())
                .putVariables("username", io.littlehorse.sdk.common.proto.VariableValue.newBuilder().setStr(username).build())
                .putVariables("fraudScore", io.littlehorse.sdk.common.proto.VariableValue.newBuilder().setDouble(score).build())
                .build();

            io.littlehorse.sdk.common.proto.WfRun wfRun = lhStub.runWf(request);
            System.out.println("[data-gen] Triggered alert-investigation-workflow for " + username + " alert=" + alertId + " wfRunId=" + wfRun.getId().getId());
        } catch (Exception e) {
            System.err.println("[data-gen] Error triggering investigation: " + e.getMessage());
        }
    }

    /**
     * Trigger a LittleHorse fraud-alert-workflow WfRun for the given transaction.
     * LH will orchestrate: get-account-info -> get-fraud-score -> conditionally freeze + alert.
     */
    private static void triggerFraudWorkflow(String username, Map<String, Object> tx, String ip, double amount, String country) {
        try {
            String txJson = mapper.writeValueAsString(tx);

            RunWfRequest request = RunWfRequest.newBuilder()
                .setWfSpecName("fraud-alert-workflow")
                .putVariables("username", VariableValue.newBuilder().setStr(username).build())
                .putVariables("transactionJson", VariableValue.newBuilder().setStr(txJson).build())
                .putVariables("ipAddress", VariableValue.newBuilder().setStr(ip).build())
                .putVariables("amount", VariableValue.newBuilder().setDouble(amount).build())
                .putVariables("country", VariableValue.newBuilder().setStr(country).build())
                .build();

            var wfRun = lhStub.runWf(request);
            System.out.println("[data-gen] Triggered fraud-alert-workflow for " + username +
                " wfRunId=" + wfRun.getId().getId());

        } catch (Exception e) {
            System.err.println("[data-gen] Failed to trigger fraud workflow: " + e.getMessage());
        }
    }

    private static void initLhClient() {
        if (!"true".equalsIgnoreCase(LH_ENABLED)) {
            System.out.println("[data-gen] LH integration disabled (LH_ENABLED != true)");
            return;
        }
        try {
            LHConfig config = new LHConfig();
            lhStub = config.getBlockingStub();
            System.out.println("[data-gen] Connected to LittleHorse server.");
        } catch (Exception e) {
            System.err.println("[data-gen] Could not connect to LH (will retry): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadAccounts() {
        try (InputStream in = Main.class.getResourceAsStream("/accounts.json")) {
            if (in == null) { System.err.println("[data-gen] accounts.json not found"); return; }
            List<Map<String, Object>> loaded = mapper.readValue(in, List.class);
            accounts.addAll(loaded);
            System.out.println("[data-gen] Loaded " + accounts.size() + " accounts");
        } catch (Exception e) {
            System.err.println("[data-gen] Failed to load accounts: " + e.getMessage());
        }
    }

    private static String weightedEvent() {
        double r = random.nextDouble();
        double cum = 0;
        for (int i = 0; i < EVENTS.length; i++) {
            cum += EVENT_WEIGHTS[i];
            if (r < cum) return EVENTS[i];
        }
        return EVENTS[0];
    }

    private static String randomIp(String country) {
        String[] prefixes = COUNTRY_IPS.getOrDefault(country, new String[]{"10."});
        String prefix = prefixes[random.nextInt(prefixes.length)];
        return prefix + (random.nextInt(254) + 1) + "." + (random.nextInt(254) + 1) + "." + (random.nextInt(254) + 1);
    }
}
