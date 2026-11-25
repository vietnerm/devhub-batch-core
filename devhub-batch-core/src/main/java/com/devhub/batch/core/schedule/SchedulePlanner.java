package com.devhub.batch.core.schedule;

import com.devhub.batch.core.cron.CronCalculator;
import com.devhub.batch.core.misfire.MisfirePolicy;
import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.support.Times;

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
 *  - Y6: the planner speaks UTC in and out. Wall-clock inputs (SCHEDULED_TIME,
 *        START_AT, END_AT and the cron slots) belong to the task TIMEZONE and
 *        are converted to UTC at the boundary, so NEXT_RUN_AT stored in the
 *        database compares correctly against Times.nowUtc() on any host.
 */
public final class SchedulePlanner {

    private SchedulePlanner() {}

    /** Next run when a task first becomes schedulable (insert / resume). */
    public static LocalDateTime initialNextRun(ScheduledTask task, LocalDateTime nowUtc) {
        String type = ScheduleValidator.normalizeType(task.getScheduleType(), task.getCronExpression(), task.getScheduledTime());
        ZoneId zone = CronCalculator.zoneOf(task.getTimezone());
        if (ScheduleValidator.ONCE.equals(type)) return Times.toUtc(task.getScheduledTime(), zone);
        if (task.getStartAt() != null && Times.toUtc(task.getStartAt(), zone).isAfter(nowUtc)) {
            return capByEnd(task, Times.toUtc(task.getStartAt(), zone), zone);
        }
        return capByEnd(task, nextSlotUtc(task, zone, nowUtc), zone);
    }

    /**
     * Next run after a finished execution. ONCE tasks are done (null).
     * RECURRING tasks advance from the furthest of (previous slot, finish time).
     */
    public static LocalDateTime afterRun(ScheduledTask task, LocalDateTime finishedUtc) {
        String type = ScheduleValidator.normalizeType(task.getScheduleType(), task.getCronExpression(), task.getScheduledTime());
        if (ScheduleValidator.ONCE.equals(type)) return null;
        ZoneId zone = CronCalculator.zoneOf(task.getTimezone());
        LocalDateTime baseUtc = task.getNextRunAt() != null && task.getNextRunAt().isAfter(finishedUtc)
                ? task.getNextRunAt()
                : finishedUtc;
        return capByEnd(task, nextSlotUtc(task, zone, baseUtc), zone);
    }

    /**
     * Leader-side misfire handling for an overdue, unclaimed task.
     * Returns the new NEXT_RUN_AT, or null to keep the current (overdue) value
     * so a worker fires it now (FIRE_NOW policy).
     */
    public static LocalDateTime misfireAdvance(ScheduledTask task, LocalDateTime nowUtc) {
        MisfirePolicy policy = MisfirePolicy.from(task.getMisfirePolicy());
        if (policy == MisfirePolicy.FIRE_NOW) return null;
        ZoneId zone = CronCalculator.zoneOf(task.getTimezone());
        return capByEnd(task, nextSlotUtc(task, zone, nowUtc), zone);
    }

    /**
     * Next run after a timed-out claim was requeued (REQUEUE_ON_FAILURE).
     */
    public static LocalDateTime requeueAfterTimeout(ScheduledTask task, LocalDateTime nowUtc) {
        String type = ScheduleValidator.normalizeType(task.getScheduleType(), task.getCronExpression(), task.getScheduledTime());
        if (ScheduleValidator.ONCE.equals(type)) return nowUtc;
        MisfirePolicy policy = MisfirePolicy.from(task.getMisfirePolicy());
        if (policy == MisfirePolicy.FIRE_NOW) return nowUtc;
        ZoneId zone = CronCalculator.zoneOf(task.getTimezone());
        return capByEnd(task, nextSlotUtc(task, zone, nowUtc), zone);
    }

    /** Cron slot strictly after the given UTC instant, evaluated on the task wall clock. */
    private static LocalDateTime nextSlotUtc(ScheduledTask task, ZoneId zone, LocalDateTime baseUtc) {
        LocalDateTime baseWall = Times.fromUtc(baseUtc, zone);
        return Times.toUtc(CronCalculator.nextExecution(task.getCronExpression(), zone, baseWall), zone);
    }

    private static LocalDateTime capByEnd(ScheduledTask task, LocalDateTime nextUtc, ZoneId zone) {
        return nextUtc != null && task.getEndAt() != null && nextUtc.isAfter(Times.toUtc(task.getEndAt(), zone))
                ? null : nextUtc;
    }
}
