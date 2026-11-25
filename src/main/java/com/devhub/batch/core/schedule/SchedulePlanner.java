package com.devhub.batch.core.schedule;

import com.devhub.batch.core.cron.CronCalculator;
import com.devhub.batch.core.misfire.MisfirePolicy;
import com.devhub.batch.core.model.ScheduledTask;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Computes NEXT_RUN_AT for every lifecycle transition.
 *
 * Fixes carried by this class:
 *  - Y2: all cron math runs on the task TIMEZONE wall clock (never ZoneId.systemDefault()).
 *  - Y3: MISFIRE_POLICY is actually applied for overdue slots.
 *  - Y5: afterRun() is strictly increasing (base = max(nextRunAt, finished)),
 *        so a drifting scheduler can never re-fire the same slot forever.
 */
public final class SchedulePlanner {

    private SchedulePlanner() {}

    /** Next run when a task first becomes schedulable (insert / resume). */
    public static LocalDateTime initialNextRun(ScheduledTask task, LocalDateTime now) {
        String type = ScheduleValidator.normalizeType(task.getScheduleType(), task.getCronExpression(), task.getScheduledTime());
        if (ScheduleValidator.ONCE.equals(type)) return task.getScheduledTime();
        ZoneId zone = CronCalculator.zoneOf(task.getTimezone());
        if (task.getStartAt() != null && task.getStartAt().isAfter(now)) {
            return capByEnd(task, task.getStartAt());
        }
        return capByEnd(task, CronCalculator.nextExecution(task.getCronExpression(), zone, now));
    }

    /**
     * Next run after a finished execution. ONCE tasks are done (null).
     * RECURRING tasks advance from the furthest of (previous slot, finish time).
     */
    public static LocalDateTime afterRun(ScheduledTask task, LocalDateTime finished) {
        String type = ScheduleValidator.normalizeType(task.getScheduleType(), task.getCronExpression(), task.getScheduledTime());
        if (ScheduleValidator.ONCE.equals(type)) return null;
        ZoneId zone = CronCalculator.zoneOf(task.getTimezone());
        LocalDateTime base = task.getNextRunAt() != null && task.getNextRunAt().isAfter(finished)
                ? task.getNextRunAt()
                : finished;
        return capByEnd(task, CronCalculator.nextExecution(task.getCronExpression(), zone, base));
    }

    /**
     * Leader-side misfire handling for an overdue, unclaimed task.
     * Returns the new NEXT_RUN_AT, or null to keep the current (overdue) value
     * so a worker fires it now (FIRE_NOW policy).
     */
    public static LocalDateTime misfireAdvance(ScheduledTask task, LocalDateTime now) {
        MisfirePolicy policy = MisfirePolicy.from(task.getMisfirePolicy());
        if (policy == MisfirePolicy.FIRE_NOW) return null;
        ZoneId zone = CronCalculator.zoneOf(task.getTimezone());
        return capByEnd(task, CronCalculator.nextExecution(task.getCronExpression(), zone, now));
    }

    /**
     * Next run after a timed-out claim was requeued (REQUEUE_ON_FAILURE).
     */
    public static LocalDateTime requeueAfterTimeout(ScheduledTask task, LocalDateTime now) {
        String type = ScheduleValidator.normalizeType(task.getScheduleType(), task.getCronExpression(), task.getScheduledTime());
        if (ScheduleValidator.ONCE.equals(type)) return now;
        MisfirePolicy policy = MisfirePolicy.from(task.getMisfirePolicy());
        if (policy == MisfirePolicy.FIRE_NOW) return now;
        ZoneId zone = CronCalculator.zoneOf(task.getTimezone());
        return capByEnd(task, CronCalculator.nextExecution(task.getCronExpression(), zone, now));
    }

    private static LocalDateTime capByEnd(ScheduledTask task, LocalDateTime next) {
        return next != null && task.getEndAt() != null && next.isAfter(task.getEndAt()) ? null : next;
    }
}
