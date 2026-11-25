package com.devhub.batch.core;

import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.repository.TaskRepository;
import com.devhub.batch.core.schedule.SchedulePlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Shared-state recovery and misfire logic, isolated from the engine so it is
 * unit-testable and reusable at startup and in the leader loop.
 *
 * Fix Y1: RUNNING runs are NEVER blindly rewritten at startup. A run is only
 * declared timed out when its own claim is stale (CLAIMED_AT older than the
 * task's TIMEOUT_SECONDS). A RUNNING task with a fresh claim is left untouched
 * — another worker may legitimately be executing it right now.
 *
 * Fix Y3: overdue, unclaimed RECURRING tasks are advanced according to their
 * MISFIRE_POLICY by the leader instead of being fired blindly.
 */
public final class TaskRecovery {

    private static final Logger log = LoggerFactory.getLogger(TaskRecovery.class);

    private TaskRecovery() {}

    /**
     * Marks stale RUNNING tasks TIMEOUT (or requeues them when
     * REQUEUE_ON_FAILURE is set). Returns the number of affected tasks.
     */
    public static int recoverTimedOutTasks(TaskRepository repo, String byWorkerId, LocalDateTime now) {
        int affected = 0;
        List<ScheduledTask> running = repo.findRunningTasks();
        for (ScheduledTask task : running) {
            LocalDateTime claimedAt = task.getClaimedAt();
            int timeoutSeconds = task.effectiveTimeoutSeconds();
            boolean stale = claimedAt == null
                    || Duration.between(claimedAt, now).getSeconds() >= timeoutSeconds;
            if (!stale) continue;
            LocalDateTime requeue = task.isRequeueOnFailure()
                    ? SchedulePlanner.requeueAfterTimeout(task, now)
                    : null;
            String message = claimedAt == null
                    ? "RUNNING without claim time; recovered by worker " + byWorkerId
                    : "RUNNING exceeded TIMEOUT_SECONDS=" + timeoutSeconds
                      + " (claim from worker " + task.getWorkerId() + " at " + claimedAt + ")";
            repo.markTaskTimeout(task.getId(), byWorkerId, message, requeue);
            affected++;
            log.info("event=task.recovered taskId={} taskName={} requeue={} nextRun={}",
                    task.getId(), task.getTaskName(), requeue != null, requeue);
        }
        return affected;
    }

    /**
     * Leader-side misfire enforcement for overdue, unclaimed tasks.
     * Returns how many tasks were advanced (SKIP_TO_NEXT).
     */
    public static int applyMisfire(TaskRepository repo, LocalDateTime now, long graceMs) {
        int advanced = 0;
        List<ScheduledTask> overdue = repo.findOverdueUnclaimed(now.minus(graceMs, ChronoUnit.MILLIS), 100);
        for (ScheduledTask task : overdue) {
            LocalDateTime next = SchedulePlanner.misfireAdvance(task, now);
            if (next != null) {
                repo.updateNextRunAt(task.getId(), next);
                advanced++;
                log.info("event=task.misfire_advanced taskId={} taskName={} policy={} nextRun={}",
                        task.getId(), task.getTaskName(), task.getMisfirePolicy(), next);
            }
            // FIRE_NOW keeps the overdue value: the dispatch loop will claim and run it now.
        }
        return advanced;
    }
}
