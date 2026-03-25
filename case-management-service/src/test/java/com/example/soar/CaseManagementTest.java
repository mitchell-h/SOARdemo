package com.example.soar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

public class CaseManagementTest {

    @BeforeEach
    public void setup() {
        Main.cases.clear();
    }

    @Test
    public void testCreateCase() {
        String id = "CASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Case newCase = new Case(id, "testuser", "FRAUD", "127.0.0.1", "Test details");
        Main.cases.put(id, newCase);

        assertThat(Main.cases).containsKey(id);
        assertThat(Main.cases.get(id).getUsername()).isEqualTo("testuser");
    }

    @Test
    public void testCloseCase() {
        String id = "CASE-123";
        Case c = new Case(id, "testuser", "FRAUD", "127.0.0.1", "Test details");
        Main.cases.put(id, c);

        Case retrieved = Main.cases.get(id);
        retrieved.setStatus("CLOSED");
        retrieved.setResolution("RESOLVED");

        assertThat(Main.cases.get(id).getStatus()).isEqualTo("CLOSED");
        assertThat(Main.cases.get(id).getResolution()).isEqualTo("RESOLVED");
    }
}
