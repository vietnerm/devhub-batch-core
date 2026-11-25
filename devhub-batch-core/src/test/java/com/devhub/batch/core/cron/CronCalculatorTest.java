package com.devhub.batch.core.cron;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CronCalculatorTest {

    private static final ZoneId HCMC = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void validate() {
        assertNull(CronCalculator.validate("0 0 2 * * *"));
        assertNull(CronCalculator.validate("0 */5 * * * *"));
        assertNotNull(CronCalculator.validate("not a cron"));
        assertNotNull(CronCalculator.validate("0 0 25 * * *"));
        assertNotNull(CronCalculator.validate(""));
        assertNotNull(CronCalculator.validate(null));
    }

    /**
     * Fix Y2: the zone parameter decides the wall clock, never ZoneId.systemDefault().
     */
    @Test
    void nextExecutionHonoursTaskTimezone() {
        LocalDateTime after = LocalDateTime.of(2026, 11, 25, 0, 0);
        LocalDateTime inHcmc = CronCalculator.nextExecution("0 0 6 * * *", HCMC, after);
        LocalDateTime inUtc = CronCalculator.nextExecution("0 0 6 * * *", UTC, after);

        assertEquals(LocalDateTime.of(2026, 11, 25, 6, 0), inHcmc);
        assertEquals(LocalDateTime.of(2026, 11, 25, 6, 0), inUtc);

        // Same wall clock, but the instants differ by the zone offset (UTC+7).
        ZonedDateTime hcmcInstant = inHcmc.atZone(HCMC);
        ZonedDateTime utcInstant = inUtc.atZone(UTC);
        assertEquals(Duration.ofHours(7), Duration.between(utcInstant, hcmcInstant).abs());
    }

    @Test
    void nextExecutionIsStrictlyAfter() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 25, 6, 0);
        LocalDateTime next = CronCalculator.nextExecution("0 0 6 * * *", HCMC, from);
        assertEquals(LocalDateTime.of(2026, 11, 26, 6, 0), next);
    }

    /**
     * Fix Y5 regression guard: iterating "next after previous" must always move
     * strictly forward, so a scheduler polling NEXT_RUN_AT can never loop.
     */
    @Test
    void repeatedAdvancementNeverLoops() {
        LocalDateTime current = LocalDateTime.of(2026, 11, 25, 23, 59, 30);
        for (int i = 0; i < 200; i++) {
            LocalDateTime next = CronCalculator.nextExecution("0 */15 * * * *", HCMC, current);
            assertTrue(next.isAfter(current), "iteration " + i);
            current = next;
        }
        assertTrue(current.isAfter(LocalDateTime.of(2026, 11, 25, 23, 59, 30)));
    }

    @Test
    void zoneOfDefaultsToProjectZone() {
        assertEquals(HCMC, CronCalculator.zoneOf(null));
        assertEquals(HCMC, CronCalculator.zoneOf("  "));
        assertEquals(ZoneId.of("Europe/Berlin"), CronCalculator.zoneOf("Europe/Berlin"));
    }
}
