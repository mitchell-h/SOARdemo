package com.example.soar;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

import java.util.Map;

public class Main {

    private static final String CORE_BANKING_URL = System.getenv().getOrDefault("CORE_BANKING_URL", "http://localhost:7002");
    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        // Add any modules if needed, keeping it consistent
    }

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
        }).start(7005);

        // POST /verify/card
        app.post("/verify/card", ctx -> {
            Map<String, Object> body = mapper.readValue(ctx.body(), Map.class);
            String username = (String) body.get("username");
            String cardNumber = (String) body.getOrDefault("cardNumber", "");

            if (username == null || username.isBlank()) {
                ctx.status(400).json(Map.of("valid", false, "reason", "Missing username"));
                return;
            }

            HttpResponse<JsonNode> accountResp = Unirest.get(CORE_BANKING_URL + "/accounts/" + username).asJson();
            if (!accountResp.isSuccess()) {
                ctx.json(Map.of("valid", false, "reason", "Account not found", "accountStatus", "NOT_FOUND"));
                return;
            }

            boolean frozen = accountResp.getBody().getObject().optBoolean("frozen", false);
            if (frozen) {
                ctx.json(Map.of("valid", false, "reason", "Account is frozen", "accountStatus", "FROZEN"));
                return;
            }

            // Basic card number validation (must be numeric, 13-19 digits)
            String digits = cardNumber.replaceAll("\\s+", "").replaceAll("-", "");
            if (!digits.matches("\\d{13,19}") || !passesLuhnCheck(digits)) {
                ctx.json(Map.of("valid", false, "reason", "Invalid card number", "accountStatus", "ACTIVE"));
                return;
            }

            ctx.json(Map.of("valid", true, "reason", "Card verified", "accountStatus", "ACTIVE"));
        });

        // POST /verify/check
        app.post("/verify/check", ctx -> {
            Map<String, Object> body = mapper.readValue(ctx.body(), Map.class);
            String username = (String) body.get("username");
            Object routingRaw = body.get("routingNumber");
            Object checkRaw = body.get("checkNumber");
            String routingNumber = routingRaw != null ? routingRaw.toString() : "";
            String checkNumber = checkRaw != null ? checkRaw.toString() : "";

            if (username == null || username.isBlank()) {
                ctx.status(400).json(Map.of("valid", false, "reason", "Missing username"));
                return;
            }

            HttpResponse<JsonNode> accountResp = Unirest.get(CORE_BANKING_URL + "/accounts/" + username).asJson();
            if (!accountResp.isSuccess()) {
                ctx.json(Map.of("valid", false, "reason", "Account not found", "accountStatus", "NOT_FOUND"));
                return;
            }

            boolean frozen = accountResp.getBody().getObject().optBoolean("frozen", false);
            if (frozen) {
                ctx.json(Map.of("valid", false, "reason", "Account is frozen", "accountStatus", "FROZEN"));
                return;
            }

            // Routing number must be 9 digits; check number 4-10 digits
            if (!routingNumber.matches("\\d{9}")) {
                ctx.json(Map.of("valid", false, "reason", "Invalid routing number format", "accountStatus", "ACTIVE"));
                return;
            }
            if (!checkNumber.matches("\\d{4,10}")) {
                ctx.json(Map.of("valid", false, "reason", "Invalid check number format", "accountStatus", "ACTIVE"));
                return;
            }

            ctx.json(Map.of("valid", true, "reason", "Check verified", "accountStatus", "ACTIVE"));
        });

        // POST /accounts/{username}/freeze  - delegate to core banking
        app.post("/accounts/{username}/freeze", ctx -> {
            String username = ctx.pathParam("username");
            HttpResponse<String> resp = Unirest.post(CORE_BANKING_URL + "/accounts/" + username + "/freeze").asString();
            ctx.status(resp.getStatus());
        });

        // POST /accounts/{username}/unfreeze
        app.post("/accounts/{username}/unfreeze", ctx -> {
            String username = ctx.pathParam("username");
            HttpResponse<String> resp = Unirest.post(CORE_BANKING_URL + "/accounts/" + username + "/unfreeze").asString();
            ctx.status(resp.getStatus());
        });

        System.out.println("[verification-service] Started on port 7005");
    }

    /** Luhn algorithm check */
    private static boolean passesLuhnCheck(String digits) {
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(digits.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}
