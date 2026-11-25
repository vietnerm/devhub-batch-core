package com.devhub.batch.core;

import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.schedule.ScheduleValidator;
import com.devhub.batch.core.support.H2Support;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the two rewrite bugs fixed in TaskRecovery:
 * Y1 (blind startup RUNNING->TIMEOUT) and Y3 (MISFIRE_POLICY ignored).
 */
class TaskRecoveryTest {

    private final H2Support h2 = H2Support.create();

    @AfterEach
    void tearDown() {
        h2.close();
    }

    private ScheduledTask recurring(String name, String misfire, boolean requeue) {
        ScheduledTask t = new ScheduledTask();
        t.setTaskName(name);
        t.setBeanClass("com.devhub.batch.core.support.NoopRunner");
        t.setScheduleType(ScheduleValidator.RECURRING);
        t.setCronExpression("0 */15 * * * *");
        t.setTimezone("Asia/Ho_Chi_Minh");
        t.setMisfirePolicy(misfire);
        t.setRequeueOnFailure(requeue);
        t.setStatus("SCHEDULED");
        t.setDescription("recovery test");
        return h2.repo().save(t);
    }

    private void claimAndBackdate(ScheduledTask task, long secondsBack) {
        assertTrue(h2.repo().claimTask(task.getId(), "w-old", LocalDateTime.now().plusSeconds(3600)));
        h2.update("UPDATE SYS_SCHEDULED_TASKS SET CLAIMED_AT = ? WHERE ID = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(secondsBack)), task.getId());
    }

    /**
     * Fix Y1: a RUNNING run with a FRESH claim must never be rewritten at
     * startup — another worker may be executing it right now.
     */
    @Test
    void freshRunningTaskIsLeftAlone() {
        ScheduledTask task = recurring("t-fresh-running", "FIRE_NOW", false);
        assertTrue(h2.repo().claimTask(task.getId(), "w-live", LocalDateTime.now().plusSeconds(3600)));

        int affected = TaskRecovery.recoverTimedOutTasks(h2.repo(), "recover", LocalDateTime.now());

        assertEquals(0, affected);
        assertEquals("RUNNING", h2.queryString("SELECT STATUS FROM SYS_SCHEDULED_TASKS WHERE ID=" + task.getId()));
    }

    /**
     * Fix Y1 (positive case): a stale claim is recovered, and without
     * REQUEUE_ON_FAILURE the task ends as TIMEOUT.
     */
    @Test
    void staleRunningTaskBecomesTimeout() {
        ScheduledTask task = recurring("t-stale", "FIRE_NOW", false);
        claimAndBackdate(task, 2 * 3600); // TIMEOUT_SECONDS default = 3600

        int affected = TaskRecovery.recoverTimedOutTasks(h2.repo(), "recover", LocalDateTime.now());

        assertEquals(1, affected);
        assertEquals("TIMEOUT", h2.queryString("SELECT STATUS FROM SYS_SCHEDULED_TASKS WHERE ID=" + task.getId()));
        assertEquals(1, h2.repo().findLogsByTaskId(task.getId(), 10).size());
        assertEquals("TIMEOUT", h2.repo().findLogsByTaskId(task.getId(), 10).get(0).getStatus());
    }

    /**
     * Fix Y1 + requeue: stale claim with REQUEUE_ON_FAILURE returns to
     * SCHEDULED with a strictly future NEXT_RUN_AT.
     */
    @Test
    void staleRunningTaskWithRequeueReturnsToSchedule() {
        ScheduledTask task = recurring("t-requeue", "SKIP_TO_NEXT", true);
        claimAndBackdate(task, 2 * 3600);

        TaskRecovery.recoverTimedOutTasks(h2.repo(), "recover", LocalDateTime.now());

        assertEquals("SCHEDULED", h2.queryString("SELECT STATUS FROM SYS_SCHEDULED_TASKS WHERE ID=" + task.getId()));
        String nextRun = h2.queryString("SELECT NEXT_RUN_AT FROM SYS_SCHEDULED_TASKS WHERE ID=" + task.getId());
        assertNotNull(nextRun);
        assertTrue(LocalDateTime.parse(nextRun.replace(' ', 'T')).isAfter(LocalDateTime.now()));
    }

    /**
     * Fix Y3: the leader advances overdue unclaimed tasks per MISFIRE_POLICY.
     */
    @Test
    void misfireSkipToNextAdvancesOverdueTask() {
        ScheduledTask task = recurring("t-misfire-skip", "SKIP_TO_NEXT", false);
        LocalDateTime overdue = LocalDateTime.now().minusMinutes(35);
        h2.repo().updateNextRunAt(task.getId(), overdue);

        int advanced = TaskRecovery.applyMisfire(h2.repo(), LocalDateTime.now(), 30_000);

        assertEquals(1, advanced);
        String nextRun = h2.queryString("SELECT NEXT_RUN_AT FROM SYS_SCHEDULED_TASKS WHERE ID=" + task.getId());
        LocalDateTime next = LocalDateTime.parse(nextRun.replace(' ', 'T'));
        assertTrue(next.isAfter(LocalDateTime.now()), "SKIP_TO_NEXT must land in the future");
    }

    /**
     * Fix Y3: FIRE_NOW keeps the overdue value so a worker fires it now.
     */
    @Test
    void misfireFireNowKeepsOverdueValue() {
        ScheduledTask task = recurring("t-misfire-fire", "FIRE_NOW", false);
        LocalDateTime overdue = LocalDateTime.now().minusMinutes(35);
        h2.repo().updateNextRunAt(task.getId(), overdue);

        int advanced = TaskRecovery.applyMisfire(h2.repo(), LocalDateTime.now(), 30_000);

        assertEquals(0, advanced);
        LocalDateTime next = LocalDateTime.parse(
                h2.queryString("SELECT NEXT_RUN_AT FROM SYS_SCHEDULED_TASKS WHERE ID=" + task.getId())
                        .replace(' ', 'T'));
        assertEquals(overdue.toLocalDate(), next.toLocalDate());
    }

    /**
     * Grace period: recently-due tasks are left for the dispatch loop.
     */
    @Test
    void misfireIgnoresRecentlyDueTasks() {
        ScheduledTask task = recurring("t-misfire-grace", "SKIP_TO_NEXT", false);
        h2.repo().updateNextRunAt(task.getId(), LocalDateTime.now().minusSeconds(5));

        int advanced = TaskRecovery.applyMisfire(h2.repo(), LocalDateTime.now(), 30_000);

        assertEquals(0, advanced);
    }
}
