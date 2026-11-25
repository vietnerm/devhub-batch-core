package com.devhub.batch.core.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleValidatorTest {

    @Test
    void recurringWithCronIsValid() {
        ScheduleValidator.Result r = ScheduleValidator.validate("RECURRING", "0 0 6 * * *", null, null, null, "daily report");
        assertTrue(r.valid());
        assertEquals("RECURRING", r.scheduleType());
    }

    @Test
    void cronAliasNormalizesToRecurring() {
        assertEquals("RECURRING", ScheduleValidator.normalizeType("cron", "0 0 6 * * *", null));
        ScheduleValidator.Result r = ScheduleValidator.validate("CRON", "0 0 6 * * *", null, null, null, "");
        assertTrue(r.valid());
        assertEquals("RECURRING", r.scheduleType());
    }

    @Test
    void typeInferredFromPayload() {
        assertEquals("RECURRING", ScheduleValidator.normalizeType(null, "0 0 6 * * *", null));
        assertEquals("ONCE", ScheduleValidator.normalizeType(null, null, LocalDateTime.now()));
    }

    @Test
    void onceWithCronIsRejected() {
        ScheduleValidator.Result r = ScheduleValidator.validate("ONCE", "0 0 6 * * *", null, null, null, "");
        assertFalse(r.valid());
    }

    @Test
    void onceWithoutTimeIsRejected() {
        ScheduleValidator.Result r = ScheduleValidator.validate("ONCE", null, null, null, null, "");
        assertFalse(r.valid());
    }

    @Test
    void recurringWithOneShotTimeIsRejected() {
        ScheduleValidator.Result r = ScheduleValidator.validate("RECURRING", null, LocalDateTime.now(), null, null, "");
        assertFalse(r.valid());
    }

    @Test
    void recurringWithoutCronIsRejected() {
        ScheduleValidator.Result r = ScheduleValidator.validate("RECURRING", null, null, null, null, "");
        assertFalse(r.valid());
    }

    @Test
    void invalidCronIsRejected() {
        ScheduleValidator.Result r = ScheduleValidator.validate("RECURRING", "0 0 25 * * *", null, null, null, "");
        assertFalse(r.valid());
    }

    @Test
    void contradictoryDescriptionsAreRejected() {
        assertFalse(ScheduleValidator.validate("ONCE", null, LocalDateTime.now(), null, null, "chạy mỗi 2 phút").valid());
        assertFalse(ScheduleValidator.validate("RECURRING", "0 0 6 * * *", null, null, null, "one-time only").valid());
    }

    @Test
    void windowOrderIsChecked() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);
        assertFalse(ScheduleValidator.validate("RECURRING", "0 0 6 * * *", null, start, end, "").valid());
    }

    @Test
    void timezoneValidation() {
        assertEquals(null, ScheduleValidator.validateTimezone("Asia/Ho_Chi_Minh"));
        assertEquals(null, ScheduleValidator.validateTimezone(""));
        assertTrue(ScheduleValidator.validateTimezone("Not/AZone").contains("Unknown timezone"));
    }

    @Test
    void misfirePolicyValidationAndLegacyAliases() {
        assertEquals(null, ScheduleValidator.validateMisfirePolicy(null));
        assertEquals(null, ScheduleValidator.validateMisfirePolicy("FIRE_ONCE"));
        assertEquals(null, ScheduleValidator.validateMisfirePolicy("skip"));
        assertEquals(null, ScheduleValidator.validateMisfirePolicy("RESCHEDULE_NEXT"));
        assertTrue(ScheduleValidator.validateMisfirePolicy("NOPE").contains("Unknown MISFIRE_POLICY"));
    }
}
