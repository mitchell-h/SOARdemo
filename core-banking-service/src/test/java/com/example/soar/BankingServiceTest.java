package com.example.soar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class BankingServiceTest {

    @BeforeEach
    public void setup() {
        Main.accounts.clear();
        Account acc = new Account();
        acc.setUsername("testuser");
        acc.setAccountNumber("ACC-TEST-001");
        acc.setFrozen(false);
        Main.accounts.add(acc);
    }

    @Test
    public void testAccountLookup() {
        assertThat(Main.accounts).hasSize(1);
        assertThat(Main.accounts.get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    public void testFreezeAccount() {
        Account acc = Main.accounts.get(0);
        acc.setFrozen(true);
        assertThat(Main.accounts.get(0).isFrozen()).isTrue();
    }
}
