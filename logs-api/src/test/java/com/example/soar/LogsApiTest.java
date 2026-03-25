package com.example.soar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class LogsApiTest {

    @BeforeEach
    public void setup() {
        Main.logs.clear();
        Main.alerts.clear();
    }

    @Test
    public void testAddLog() {
        LogEntry log = new LogEntry();
        log.setUsername("testuser");
        log.setEvent("login");
        Main.logs.add(log);

        assertThat(Main.logs).hasSize(1);
        assertThat(Main.logs.get(0).getEvent()).isEqualTo("login");
    }

    @Test
    public void testAddAlert() {
        Alert alert = new Alert();
        alert.setUsername("testuser");
        alert.setSeverity("HIGH");
        Main.alerts.add(alert);

        assertThat(Main.alerts).hasSize(1);
        assertThat(Main.alerts.get(0).getSeverity()).isEqualTo("HIGH");
    }
}
