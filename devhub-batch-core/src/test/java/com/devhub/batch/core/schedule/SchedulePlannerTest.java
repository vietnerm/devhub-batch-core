package com.devhub.batch.core.schedule;

import com.devhub.batch.core.model.ScheduledTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulePlannerTest {

    private static final String EVERY_15_MIN = "0 */15 * * * *";

    private static ScheduledTask recurring(String cron, String timezone, String misfire) {
        ScheduledTask t = new ScheduledTask();
        t.setTaskName("t");
        t.setBeanClass("com.example.B");
        t.setScheduleType(ScheduleValidator.RECURRING);
        t.setCronExpression(cron);
        t.setTimezone(timezone);
        t.setMisfirePolicy(misfire);
        return t;
    }

    /* ---------- initialNextRun ---------- */

    @Test
    void initialOnceIsScheduledTime() {
        ScheduledTask t = new ScheduledTask();
        t.setScheduleType(ScheduleValidator.ONCE);
        t.setScheduledTime(LocalDateTime.of(2026, 12, 1, 9, 0));
        t.setTimezone("Asia/Ho_Chi_Minh");
        // 09:00 SGT wall time -> 02:00 UTC (planner returns UTC).
        assertEquals(LocalDateTime.of(2026, 12, 1, 2, 0),
                SchedulePlanner.initialNextRun(t, LocalDateTime.of(2026, 11, 25, 0, 0)));
    }

    @Test
    void initialRecurringHonoursFutureStartAt() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "FIRE_NOW");
        t.setStartAt(LocalDateTime.of(2026, 12, 1, 0, 0));
        // 2026-12-01 00:00 SGT -> 2026-11-30 17:00 UTC.
        assertEquals(LocalDateTime.of(2026, 11, 30, 17, 0),
                SchedulePlanner.initialNextRun(t, LocalDateTime.of(2026, 11, 25, 0, 0)));
    }

    @Test
    void initialRecurringUsesTaskTimezone() {
        ScheduledTask t = recurring("0 0 6 * * *", "Asia/Ho_Chi_Minh", "FIRE_NOW");
        // now = 2026-11-25 00:00 UTC = 07:00 SGT -> next 06:00 SGT slot is 11-26,
        // which is 2026-11-25 23:00 UTC.
        LocalDateTime next = SchedulePlanner.initialNextRun(t, LocalDateTime.of(2026, 11, 25, 0, 0));
        assertEquals(LocalDateTime.of(2026, 11, 25, 23, 0), next);
    }

    /* ---------- afterRun (fix Y5: strictly increasing) ---------- */

    @Test
    void afterRunAdvancesFromFinishTimeWhenSlotOverrun() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "FIRE_NOW");
        t.setNextRunAt(LocalDateTime.of(2026, 11, 25, 10, 0));
        LocalDateTime next = SchedulePlanner.afterRun(t, LocalDateTime.of(2026, 11, 25, 10, 20));
        assertEquals(LocalDateTime.of(2026, 11, 25, 10, 30), next);
    }

    @Test
    void afterRunAdvancesFromScheduledSlotWhenNotOverrun() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "FIRE_NOW");
        t.setNextRunAt(LocalDateTime.of(2026, 11, 25, 11, 0));
        LocalDateTime next = SchedulePlanner.afterRun(t, LocalDateTime.of(2026, 11, 25, 10, 5));
        assertEquals(LocalDateTime.of(2026, 11, 25, 11, 15), next);
        assertTrue(next.isAfter(t.getNextRunAt()));
    }

    @Test
    void afterRunOnceReturnsNull() {
        ScheduledTask t = new ScheduledTask();
        t.setScheduleType(ScheduleValidator.ONCE);
        t.setScheduledTime(LocalDateTime.of(2026, 11, 25, 9, 0));
        assertNull(SchedulePlanner.afterRun(t, LocalDateTime.of(2026, 11, 25, 9, 0)));
    }

    @Test
    void afterRunCapsByEndWindow() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "FIRE_NOW");
        t.setNextRunAt(LocalDateTime.of(2026, 11, 25, 10, 0));
        t.setEndAt(LocalDateTime.of(2026, 11, 25, 10, 20));
        assertNull(SchedulePlanner.afterRun(t, LocalDateTime.of(2026, 11, 25, 10, 20)));
    }

    /* ---------- misfireAdvance (fix Y3) ---------- */

    @Test
    void misfireSkipToNextDropsOverdueSlot() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "SKIP_TO_NEXT");
        t.setNextRunAt(LocalDateTime.of(2026, 11, 25, 10, 0));
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 7);
        assertEquals(LocalDateTime.of(2026, 11, 25, 10, 15), SchedulePlanner.misfireAdvance(t, now));
    }

    @Test
    void misfireFireNowKeepsOverdueValue() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "FIRE_NOW");
        t.setNextRunAt(LocalDateTime.of(2026, 11, 25, 10, 0));
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 7);
        assertNull(SchedulePlanner.misfireAdvance(t, now));
    }

    @Test
    void legacyMisfireNamesAreUnderstood() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "RESCHEDULE_NEXT");
        t.setNextRunAt(LocalDateTime.of(2026, 11, 25, 10, 0));
        assertEquals(LocalDateTime.of(2026, 11, 25, 10, 15),
                SchedulePlanner.misfireAdvance(t, LocalDateTime.of(2026, 11, 25, 10, 7)));
    }

    /* ---------- requeueAfterTimeout ---------- */

    @Test
    void requeueFireNowIsImmediate() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "FIRE_NOW");
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 7);
        assertEquals(now, SchedulePlanner.requeueAfterTimeout(t, now));
    }

    @Test
    void requeueSkipToNextWaitsForNextSlot() {
        ScheduledTask t = recurring(EVERY_15_MIN, "Asia/Ho_Chi_Minh", "SKIP_TO_NEXT");
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 7);
        assertEquals(LocalDateTime.of(2026, 11, 25, 10, 15), SchedulePlanner.requeueAfterTimeout(t, now));
    }

    @Test
    void requeueOnceRerunsImmediately() {
        ScheduledTask t = new ScheduledTask();
        t.setScheduleType(ScheduleValidator.ONCE);
        t.setScheduledTime(LocalDateTime.of(2026, 11, 25, 9, 0));
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 7);
        assertEquals(now, SchedulePlanner.requeueAfterTimeout(t, now));
    }
}
