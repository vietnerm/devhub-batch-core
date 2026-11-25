package com.devhub.batch.core.repository;

import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.model.WorkerInfo;
import com.devhub.batch.core.schedule.ScheduleValidator;
import com.devhub.batch.core.support.H2Support;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTaskRepositoryTest {

    private final H2Support h2 = H2Support.create();

    @AfterEach
    void tearDown() {
        h2.close();
    }

    private ScheduledTask recurringTask(String name) {
        ScheduledTask t = new ScheduledTask();
        t.setTaskName(name);
        t.setBeanClass("com.devhub.batch.core.support.NoopRunner");
        t.setScheduleType(ScheduleValidator.RECURRING);
        t.setCronExpression("0 */2 * * * *");
        t.setStatus("SCHEDULED");
        t.setDescription("integration test task");
        return t;
    }

    private ScheduledTask onceTask(String name) {
        ScheduledTask t = new ScheduledTask();
        t.setTaskName(name);
        t.setBeanClass("com.devhub.batch.core.support.NoopRunner");
        t.setScheduleType(ScheduleValidator.ONCE);
        t.setScheduledTime(LocalDateTime.now().plusSeconds(3600));
        t.setStatus("SCHEDULED");
        t.setDescription("one-shot");
        return t;
    }

    @Test
    void saveComputesInitialNextRun() {
        ScheduledTask saved = h2.repo().save(recurringTask("t-next"));
        assertNotNull(saved.getId());
        assertNotNull(saved.getNextRunAt());
        assertTrue(saved.getNextRunAt().isAfter(LocalDateTime.now().minusSeconds(5)));
    }

    @Test
    void invalidScheduleIsRejected() {
        ScheduledTask bad = recurringTask("t-bad");
        bad.setCronExpression("0 0 25 * * *");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> h2.repo().save(bad));
    }

    /**
     * The claim is the most race-sensitive statement of the whole scheduler:
     * N concurrent workers, exactly one winner per due task.
     */
    @Test
    void claimIsAtomicUnderConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int round = 0; round < 25; round++) {
                ScheduledTask saved = h2.repo().save(recurringTask("t-claim-" + round));
                long id = saved.getId();
                AtomicInteger winners = new AtomicInteger();
                CountDownLatch ready = new CountDownLatch(4);
                List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    final String worker = "w" + i;
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        try { ready.await(); } catch (InterruptedException ignored) { }
                        if (h2.repo().claimTask(id, worker, LocalDateTime.now().plusSeconds(3600))) {
                            winners.incrementAndGet();
                        }
                    }));
                }
                for (java.util.concurrent.Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
                assertEquals(1, winners.get(), "round " + round + ": exactly one claimer must win");
                assertEquals("RUNNING", h2.queryString("SELECT STATUS FROM SYS_SCHEDULED_TASKS WHERE ID=" + id));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void claimFailsWhileFreshlyClaimed() {
        ScheduledTask saved = h2.repo().save(onceTask("t-fresh"));
        long id = saved.getId();
        assertTrue(h2.repo().claimTask(id, "w1", LocalDateTime.now().plusSeconds(3600)));
        assertFalse(h2.repo().claimTask(id, "w2", LocalDateTime.now().plusSeconds(3600)));
    }

    @Test
    void claimReplacesStaleClaim() {
        ScheduledTask saved = h2.repo().save(onceTask("t-stale"));
        long id = saved.getId();
        assertTrue(h2.repo().claimTask(id, "dead-worker", LocalDateTime.now().plusSeconds(3600)));
        // Crash of dead-worker: claim is 2 hours old, timeout is 1 hour.
        // Recovery/requeue puts the task back to SCHEDULED keeping the old claim,
        // exactly like markTaskTimeout(..., requeueNextRun) does.
        h2.update("UPDATE SYS_SCHEDULED_TASKS SET CLAIMED_AT = ?, STATUS = 'SCHEDULED' WHERE ID = ?",
                Timestamps.of(LocalDateTime.now().minusHours(2)), id);
        assertTrue(h2.repo().claimTask(id, "w2", LocalDateTime.now()));
    }

    @Test
    void updateAfterRunClearsClaimState() {
        ScheduledTask saved = h2.repo().save(onceTask("t-after"));
        long id = saved.getId();
        assertTrue(h2.repo().claimTask(id, "w1", LocalDateTime.now().plusSeconds(3600)));
        LocalDateTime finished = LocalDateTime.now();
        h2.repo().updateAfterRun(id, finished, null, "COMPLETED");
        assertEquals("COMPLETED", h2.queryString("SELECT STATUS FROM SYS_SCHEDULED_TASKS WHERE ID=" + id));
        assertNull(h2.queryString("SELECT WORKER_ID FROM SYS_SCHEDULED_TASKS WHERE ID=" + id));
        assertNull(h2.queryString("SELECT CLAIMED_AT FROM SYS_SCHEDULED_TASKS WHERE ID=" + id));
    }

    @Test
    void findDueTasksFiltersAndOrders() {
        ScheduledTask a = h2.repo().save(recurringTask("t-due-a"));
        ScheduledTask b = h2.repo().save(recurringTask("t-due-b"));
        ScheduledTask c = h2.repo().save(recurringTask("t-due-c"));
        // Persist priorities through the update path of save().
        a.setPriority(1);
        b.setPriority(5);
        c.setPriority(5);
        h2.repo().save(a);
        h2.repo().save(b);
        h2.repo().save(c);
        LocalDateTime due = LocalDateTime.now().plusSeconds(60);
        h2.repo().updateNextRunAt(a.getId(), due.minusSeconds(10));
        h2.repo().updateNextRunAt(b.getId(), due.minusSeconds(5));
        h2.repo().updateNextRunAt(c.getId(), due.plusSeconds(600)); // future -> excluded

        List<ScheduledTask> dueTasks = h2.repo().findDueTasks(due, 10);
        assertEquals(2, dueTasks.size());
        assertEquals(b.getId(), dueTasks.get(0).getId()); // higher priority first
        assertEquals(a.getId(), dueTasks.get(1).getId());
    }

    @Test
    void heartbeatUpsertsAndGoesOffline() {
        WorkerInfo worker = WorkerInfo.detect("w-hb", "unknown", "unknown", "unknown", "hb-host",
                null, null, null, "test", "test", "test", "unknown");
        h2.repo().heartbeat(worker, null, null, "ONLINE", null);
        h2.repo().heartbeat(worker, null, null, "ONLINE", null);
        assertEquals(1, Integer.parseInt(h2.queryString("SELECT COUNT(*) FROM SYS_SCHEDULER_WORKERS WHERE WORKER_ID='w-hb'")));
        h2.repo().markWorkerOffline("w-hb");
        assertEquals("OFFLINE", h2.queryString("SELECT STATUS FROM SYS_SCHEDULER_WORKERS WHERE WORKER_ID='w-hb'"));
    }

    @Test
    void executionLogsAreLimitedAndPurgeable() {
        ScheduledTask saved = h2.repo().save(onceTask("t-logs"));
        long id = saved.getId();
        for (int i = 0; i < 5; i++) {
            h2.repo().saveExecutionLog(id, "w1", "SUCCESS", "run " + i, null,
                    LocalDateTime.now().minusMinutes(i), LocalDateTime.now().minusMinutes(i), 10);
        }
        h2.repo().saveExecutionLog(id, "w1", "FAILED", null, "ancient failure",
                LocalDateTime.now().minusDays(40), LocalDateTime.now().minusDays(40), 5);

        assertEquals(5, h2.repo().findLogsByTaskId(id, 5).size());
        assertEquals(6, h2.repo().findLogsByTaskId(id, 100).size());

        h2.repo().purgeLogsBefore(LocalDateTime.now().minusDays(30));
        assertEquals(5, h2.repo().findLogsByTaskId(id, 100).size());
    }

    /** Timestamp holder keeping the test imports tidy. */
    private static final class Timestamps {
        static java.sql.Timestamp of(LocalDateTime dt) { return java.sql.Timestamp.valueOf(dt); }
    }
}
