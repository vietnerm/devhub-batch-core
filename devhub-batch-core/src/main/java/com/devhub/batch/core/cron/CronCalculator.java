package com.devhub.batch.core.cron;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Cron engine — cron-utils, Spring 6-field format (sec min hour dom mon dow).
 *
 * Rewrite note (fix Y2): every conversion is zone-aware. The scheduler passes
 * the ZoneId resolved from the task's TIMEZONE column; the JVM default zone is
 * never used implicitly.
 */
public final class CronCalculator {

    private static final CronDefinition DEFINITION = CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING53);
    private static final CronParser PARSER = new CronParser(DEFINITION);

    /** Fallback zone, matches the TIMEZONE column default of the scheduler schema. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private CronCalculator() {}

    /** Returns null when the expression is valid, an error message otherwise. */
    public static String validate(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) return "cron expression is blank";
        try {
            PARSER.parse(cronExpr.trim());
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /**
     * Next execution strictly after {@code after}, evaluated on the wall clock
     * of {@code zone}. Never returns {@code after} itself or an earlier time.
     */
    public static LocalDateTime nextExecution(String cronExpr, ZoneId zone, LocalDateTime after) {
        Cron cron = PARSER.parse(cronExpr.trim());
        ExecutionTime exec = ExecutionTime.forCron(cron);
        ZonedDateTime from = after.atZone(zone);
        Optional<ZonedDateTime> next = exec.nextExecution(from);
        return next.map(ZonedDateTime::toLocalDateTime).orElse(null);
    }

    /** Resolves the task TIMEZONE column to a ZoneId (blank -> DEFAULT_ZONE). */
    public static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) return DEFAULT_ZONE;
        return ZoneId.of(timezone.trim());
    }
}
