package com.devhub.batch.core;

import java.time.LocalDateTime;

/**
 * Failure retry policy: counts attempts and spaces requeues with exponential
 * backoff instead of requeueing a failing recurring task on every tick.
 *
 * A task is dead-lettered (STATUS='DEAD') when the attempt count reaches its
 * MAX_ATTEMPT, so operators can inspect the error instead of the task silently
 * failing forever.
 */
public final class RetryPolicy {

    private static final long MAX_BACKOFF_SECONDS = 3600;
    private static final int MAX_SHIFT = 20; // guard against overflow

    private final int defaultMaxAttempt;
    private final int backoffBaseSeconds;

    public RetryPolicy(int defaultMaxAttempt, int backoffBaseSeconds) {
        this.defaultMaxAttempt = Math.max(1, defaultMaxAttempt);
        this.backoffBaseSeconds = Math.max(1, backoffBaseSeconds);
    }

    /** Used when the task row has no positive MAX_ATTEMPT of its own. */
    public int defaultMaxAttempt() {
        return defaultMaxAttempt;
    }

    /**
     * Backoff before requeue after the given attempt (1-based):
     * base * 2^(attempt-1), capped at 1 hour.
     */
    public long backoffSeconds(int attempt) {
        int shift = Math.min(Math.max(attempt - 1, 0), MAX_SHIFT);
        return Math.min((long) backoffBaseSeconds << shift, MAX_BACKOFF_SECONDS);
    }

    /** UTC time of the next requeue for a task that just failed its given attempt. */
    public LocalDateTime nextRetryAt(int attempt, LocalDateTime failedAtUtc) {
        return failedAtUtc.plusSeconds(backoffSeconds(attempt));
    }
}
