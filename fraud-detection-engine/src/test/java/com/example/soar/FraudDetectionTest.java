package com.example.soar;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

public class FraudDetectionTest {

    @Test
    public void testLowAmountInternalTransaction() {
        Map<String, Object> tx = new HashMap<>();
        tx.put("amount", 100.0);
        tx.put("previousCountryOfOrigin", "US");
        tx.put("ipAddress", "12.1.2.3"); // US IP

        Main.FraudResult result = Main.score(tx);

        assertThat(result.score).isLessThan(0.35);
        assertThat(result.riskLevel).isEqualTo("LOW");
        assertThat(result.reasons).isEmpty();
    }

    @Test
    public void testHighAmountTransaction() {
        Map<String, Object> tx = new HashMap<>();
        tx.put("amount", 6000.0);
        tx.put("previousCountryOfOrigin", "US");
        tx.put("ipAddress", "12.1.2.3"); // US IP

        Main.FraudResult result = Main.score(tx);

        assertThat(result.score).isGreaterThanOrEqualTo(0.40);
        assertThat(result.riskLevel).isEqualTo("MEDIUM");
        assertThat(result.reasons).contains("Extremely high transaction amount: 6000.0");
    }

    @Test
    public void testCountryMismatch() {
        Map<String, Object> tx = new HashMap<>();
        tx.put("amount", 100.0);
        tx.put("previousCountryOfOrigin", "US");
        tx.put("ipAddress", "81.1.2.3"); // GB IP

        Main.FraudResult result = Main.score(tx);

        assertThat(result.score).isGreaterThanOrEqualTo(0.35);
        assertThat(result.riskLevel).isEqualTo("MEDIUM");
        assertThat(result.reasons).anyMatch(r -> r.contains("IP country (GB) does not match account country (US)"));
    }

    @Test
    public void testCriticalFraud() {
        Map<String, Object> tx = new HashMap<>();
        tx.put("amount", 7000.0); // 0.40
        tx.put("previousCountryOfOrigin", "US");
        tx.put("ipAddress", "91.1.2.3"); // RU IP + flagged range (0.35 + 0.20)
        tx.put("event", "transfer"); // 0.10

        Main.FraudResult result = Main.score(tx);

        assertThat(result.score).isGreaterThanOrEqualTo(0.80);
        assertThat(result.riskLevel).isEqualTo("CRITICAL");
        assertThat(result.reasons).hasSizeGreaterThanOrEqualTo(3);
    }
}
