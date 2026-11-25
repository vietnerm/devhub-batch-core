package com.devhub.batch.core.support;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Fix Y6: every DATETIME column in the scheduler schema stores UTC wall time.
 * Workers and leaders may run in different machine timezones — comparing
 * LocalDateTime.now() (JVM default zone) against task-zone wall clocks made
 * NEXT_RUN_AT / CLAIMED_AT comparisons wrong across hosts. All clock reads
 * that end up in the database go through this class.
 */
public final class Times {

    private Times() {}

    /** Current time as UTC wall clock, for storage and DB comparisons. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Task-timezone wall time (user input) -> UTC for storage. */
    public static LocalDateTime toUtc(LocalDateTime wall, ZoneId zone) {
        if (wall == null) return null;
        return wall.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Stored UTC -> task-timezone wall time (for cron math). */
    public static LocalDateTime fromUtc(LocalDateTime utc, ZoneId zone) {
        if (utc == null) return null;
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime();
    }
}
