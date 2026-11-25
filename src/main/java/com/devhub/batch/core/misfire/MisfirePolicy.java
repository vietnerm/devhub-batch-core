package com.devhub.batch.core.misfire;

import java.util.Locale;

/**
 * Missed-fire handling for overdue RECURRING tasks.
 *
 * Legacy values from devhub-rbac-batch are normalized:
 *   FIRE_ONCE -> FIRE_NOW, SKIP / RESCHEDULE_NEXT -> SKIP_TO_NEXT.
 */
public enum MisfirePolicy {

    /** Overdue slot still fires immediately (legacy default FIRE_ONCE). */
    FIRE_NOW,

    /** Overdue slot is dropped; the task waits for the next cron occurrence. */
    SKIP_TO_NEXT;

    public static MisfirePolicy from(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "", "FIRE_NOW", "FIRE_ONCE" -> FIRE_NOW;
            case "SKIP_TO_NEXT", "SKIP", "RESCHEDULE_NEXT" -> SKIP_TO_NEXT;
            default -> FIRE_NOW;
        };
    }

    /** True when the raw value is a known policy (used by validation). */
    public static boolean isKnown(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty()
                || value.equals("FIRE_NOW") || value.equals("FIRE_ONCE")
                || value.equals("SKIP_TO_NEXT") || value.equals("SKIP") || value.equals("RESCHEDULE_NEXT");
    }
}
