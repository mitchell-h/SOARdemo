package com.example.soar;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.util.*;

public class Main {

    private static final ObjectMapper mapper = new ObjectMapper();

    // Rough IP ranges per country prefix (first octet or two)
    private static final Map<String, String> IP_PREFIX_TO_COUNTRY = new LinkedHashMap<>();

    static {
        // US
        IP_PREFIX_TO_COUNTRY.put("12.", "US"); IP_PREFIX_TO_COUNTRY.put("73.", "US");
        IP_PREFIX_TO_COUNTRY.put("104.", "US"); IP_PREFIX_TO_COUNTRY.put("108.", "US");
        IP_PREFIX_TO_COUNTRY.put("184.", "US");
        // GB
        IP_PREFIX_TO_COUNTRY.put("81.", "GB"); IP_PREFIX_TO_COUNTRY.put("82.", "GB");
        IP_PREFIX_TO_COUNTRY.put("86.", "GB"); IP_PREFIX_TO_COUNTRY.put("87.", "GB");
        // CA
        IP_PREFIX_TO_COUNTRY.put("24.", "CA"); IP_PREFIX_TO_COUNTRY.put("64.", "CA");
        IP_PREFIX_TO_COUNTRY.put("70.", "CA"); IP_PREFIX_TO_COUNTRY.put("142.", "CA");
        // AU
        IP_PREFIX_TO_COUNTRY.put("1.", "AU"); IP_PREFIX_TO_COUNTRY.put("27.", "AU");
        IP_PREFIX_TO_COUNTRY.put("49.", "AU"); IP_PREFIX_TO_COUNTRY.put("58.", "AU");
        // DE
        IP_PREFIX_TO_COUNTRY.put("46.", "DE"); IP_PREFIX_TO_COUNTRY.put("78.", "DE");
        IP_PREFIX_TO_COUNTRY.put("217.", "DE"); IP_PREFIX_TO_COUNTRY.put("77.", "DE");
        // FR
        IP_PREFIX_TO_COUNTRY.put("90.", "FR"); IP_PREFIX_TO_COUNTRY.put("92.", "FR");
        IP_PREFIX_TO_COUNTRY.put("109.", "FR"); IP_PREFIX_TO_COUNTRY.put("176.", "FR");
        // JP
        IP_PREFIX_TO_COUNTRY.put("111.", "JP"); IP_PREFIX_TO_COUNTRY.put("122.", "JP");
        IP_PREFIX_TO_COUNTRY.put("126.", "JP"); IP_PREFIX_TO_COUNTRY.put("153.", "JP");
        // IN
        IP_PREFIX_TO_COUNTRY.put("103.", "IN"); IP_PREFIX_TO_COUNTRY.put("117.", "IN");
        IP_PREFIX_TO_COUNTRY.put("183.", "IN"); IP_PREFIX_TO_COUNTRY.put("223.", "IN");
        // BR
        IP_PREFIX_TO_COUNTRY.put("177.", "BR"); IP_PREFIX_TO_COUNTRY.put("179.", "BR");
        IP_PREFIX_TO_COUNTRY.put("186.", "BR"); IP_PREFIX_TO_COUNTRY.put("189.", "BR");
        // MX
        IP_PREFIX_TO_COUNTRY.put("148.", "MX"); IP_PREFIX_TO_COUNTRY.put("187.", "MX");
        IP_PREFIX_TO_COUNTRY.put("190.", "MX"); IP_PREFIX_TO_COUNTRY.put("201.", "MX");
        // RU / other
        IP_PREFIX_TO_COUNTRY.put("91.", "RU"); IP_PREFIX_TO_COUNTRY.put("95.", "RU");
        IP_PREFIX_TO_COUNTRY.put("194.", "RU"); IP_PREFIX_TO_COUNTRY.put("195.", "RU");
    }

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
        }).start(7004);

        app.post("/detect", ctx -> {
            Map<String, Object> tx = mapper.readValue(ctx.body(), Map.class);
            FraudResult result = score(tx);
            ctx.json(result);
        });

        System.out.println("[fraud-detection] Started on port 7004");
    }

    static FraudResult score(Map<String, Object> tx) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();

        // Rule 1: High amount
        if (tx.containsKey("amount")) {
            double amount = ((Number) tx.get("amount")).doubleValue();
            if (amount > 5000) {
                score += 0.40;
                reasons.add("Extremely high transaction amount: " + amount);
            } else if (amount > 2000) {
                score += 0.25;
                reasons.add("High transaction amount: " + amount);
            } else if (amount > 500) {
                score += 0.10;
                reasons.add("Elevated transaction amount: " + amount);
            }
        }

        // Rule 2: Country mismatch between IP origin and account origin
        String ipAddress = (String) tx.getOrDefault("ipAddress", "");
        String accountCountry = (String) tx.getOrDefault("previousCountryOfOrigin", "");
        String txCountry = (String) tx.getOrDefault("country", "");
        String detectedIpCountry = detectCountryFromIp(ipAddress);

        if (!accountCountry.isBlank() && !detectedIpCountry.equals("UNKNOWN")) {
            if (!detectedIpCountry.equals(accountCountry)) {
                score += 0.35;
                reasons.add("IP country (" + detectedIpCountry + ") does not match account country (" + accountCountry + ")");
            }
        }

        // Rule 3: Transaction country explicitly set and different from account country
        if (!txCountry.isBlank() && !accountCountry.isBlank() && !txCountry.equals(accountCountry)) {
            if (score < 0.55) { // avoid double-counting
                score += 0.20;
                reasons.add("Transaction country (" + txCountry + ") differs from account home country (" + accountCountry + ")");
            }
        }

        // Rule 4: High-risk IP ranges (non-US, non-EU flagged ranges)
        if (ipAddress.startsWith("95.") || ipAddress.startsWith("91.") || ipAddress.startsWith("194.")) {
            score += 0.20;
            reasons.add("IP in flagged range: " + ipAddress);
        }

        // Rule 5: Known suspicious event types
        String event = (String) tx.getOrDefault("event", "");
        if ("transfer".equals(event) || "wire".equals(event)) {
            score += 0.10;
            reasons.add("High-risk event type: " + event);
        }

        score = Math.max(0.0, Math.min(1.0, score));

        String riskLevel;
        if (score >= 0.80) riskLevel = "CRITICAL";
        else if (score >= 0.60) riskLevel = "HIGH";
        else if (score >= 0.35) riskLevel = "MEDIUM";
        else riskLevel = "LOW";

        return new FraudResult(score, riskLevel, reasons);
    }

    private static String detectCountryFromIp(String ip) {
        if (ip == null || ip.isBlank()) return "UNKNOWN";
        for (Map.Entry<String, String> entry : IP_PREFIX_TO_COUNTRY.entrySet()) {
            if (ip.startsWith(entry.getKey())) return entry.getValue();
        }
        // Also check 2-octet prefix
        String twoOctet = ip.contains(".") ? ip.substring(0, ip.indexOf(".", ip.indexOf(".") + 1) + 1) : "";
        for (Map.Entry<String, String> entry : IP_PREFIX_TO_COUNTRY.entrySet()) {
            if (!twoOctet.isBlank() && twoOctet.startsWith(entry.getKey())) return entry.getValue();
        }
        return "UNKNOWN";
    }

    // Simple POJO for response
    public static class FraudResult {
        public final double score;
        public final String riskLevel;
        public final List<String> reasons;

        public FraudResult(double score, String riskLevel, List<String> reasons) {
            this.score = score;
            this.riskLevel = riskLevel;
            this.reasons = reasons;
        }
    }
}
