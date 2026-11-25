package com.devhub.batch.core.schedule;

import com.devhub.batch.core.cron.CronCalculator;
import com.devhub.batch.core.misfire.MisfirePolicy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Shared schedule contract: canonical values ONCE and RECURRING (CRON accepted
 * as a compatibility alias and normalized to RECURRING). Mixed one-time/cron
 * payloads are rejected before they reach the database or the scheduler.
 */
public final class ScheduleValidator {
    public static final String ONCE = "ONCE";
    public static final String RECURRING = "RECURRING";

    private ScheduleValidator() {}

    public record Result(boolean valid, String scheduleType, String message) {
        public static Result ok(String type) { return new Result(true, type, ""); }
        public static Result invalid(String type, String message) { return new Result(false, type, message); }
    }

    public static String normalizeType(String rawType, String cronExpression, LocalDateTime scheduledTime) {
        String type = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        if ("CRON".equals(type)) type = RECURRING;
        if (type.isBlank()) {
            return cronExpression != null && !cronExpression.isBlank() ? RECURRING : ONCE;
        }
        return type;
    }

    public static Result validate(String rawType,
                                  String cronExpression,
                                  LocalDateTime scheduledTime,
                                  LocalDateTime startAt,
                                  LocalDateTime endAt,
                                  String description) {
        String type = normalizeType(rawType, cronExpression, scheduledTime);
        String cron = clean(cronExpression);
        String desc = clean(description).toLowerCase(Locale.ROOT);

        if (!ONCE.equals(type) && !RECURRING.equals(type)) {
            return Result.invalid(type, "Invalid schedule type. Use ONCE or RECURRING.");
        }
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            return Result.invalid(type, "Start window must be before End window.");
        }

        if (ONCE.equals(type)) {
            if (!cron.isBlank()) {
                return Result.invalid(type, "ONCE must not carry a cron expression. Remove the cron or switch to RECURRING.");
            }
            if (scheduledTime == null) {
                return Result.invalid(type, "ONCE requires scheduledTime.");
            }
            if (hasRecurringDescription(desc)) {
                return Result.invalid(type, "Description describes a recurring schedule but type is ONCE. Fix the description or switch to RECURRING.");
            }
            return Result.ok(type);
        }

        if (scheduledTime != null) {
            return Result.invalid(type, "RECURRING must not carry a one-time scheduledTime. Remove it or switch to ONCE.");
        }
        if (cron.isBlank()) {
            return Result.invalid(type, "RECURRING requires a six-field cron expression.");
        }
        String cronError = CronCalculator.validate(cron);
        if (cronError != null) {
            return Result.invalid(type, "Invalid cron expression: " + cronError);
        }
        if (hasOnceDescription(desc)) {
            return Result.invalid(type, "Description describes a one-time run but type is RECURRING. Fix the description or switch to ONCE.");
        }
        return Result.ok(type);
    }

    /** TIMEZONE must be a valid IANA zone (fix Y2: never silently fall back). */
    public static String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) return null; // blank -> schema default
        try {
            ZoneId.of(timezone.trim());
            return null;
        } catch (Exception e) {
            return "Unknown timezone '" + timezone + "'";
        }
    }

    /** MISFIRE_POLICY must be a known policy (fix Y3: policy is enforced, not ignored). */
    public static String validateMisfirePolicy(String misfirePolicy) {
        return MisfirePolicy.isKnown(misfirePolicy) ? null : "Unknown MISFIRE_POLICY '" + misfirePolicy + "'";
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static boolean hasRecurringDescription(String description) {
        return containsAny(description, List.of("mỗi ", "moi ", "every ", "cron", "định kỳ", "dinh ky", "lặp", "lap ", "recurring", "repeat"));
    }

    private static boolean hasOnceDescription(String description) {
        return containsAny(description, List.of("one-time", "one time", "once", "một lần", "mot lan", "duy nhất", "duy nhat", "chỉ chạy 1 lần", "chi chay 1 lan"));
    }

    private static boolean containsAny(String value, List<String> tokens) {
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }
}
