package com.devhub.batch.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(3, 60);

    @Test
    void backoffDoublesPerAttempt() {
        assertEquals(60, policy.backoffSeconds(1));
        assertEquals(120, policy.backoffSeconds(2));
        assertEquals(240, policy.backoffSeconds(3));
    }

    @Test
    void backoffCapsAtOneHour() {
        assertEquals(3600, policy.backoffSeconds(7));   // 60 * 2^6 = 3840 -> cap
        assertEquals(3600, policy.backoffSeconds(30));
    }

    @Test
    void nextRetryAddsDelayToFailureTime() {
        LocalDateTime failed = LocalDateTime.of(2026, 11, 25, 10, 0);
        assertEquals(LocalDateTime.of(2026, 11, 25, 10, 1), policy.nextRetryAt(1, failed));
        assertEquals(LocalDateTime.of(2026, 11, 25, 10, 2), policy.nextRetryAt(2, failed));
    }

    @Test
    void rejectsNonPositiveInputs() {
        RetryPolicy p = new RetryPolicy(0, -5);
        assertEquals(1, p.defaultMaxAttempt());
        assertEquals(1, p.backoffSeconds(1));
    }
}
