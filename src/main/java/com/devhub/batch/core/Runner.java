package com.devhub.batch.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Abstract base every batch extends (kept from devhub-rbac-batch so existing
 * BEAN_CLASS plugins keep working).
 *
 * Usage:
 *   public class ZZA0_0100_batch extends Runner {
 *       @Override public void run() throws Exception {
 *           log("syncing...");
 *       }
 *   }
 *
 * Lifecycle when run by the scheduler:
 *   1. TaskJob calls execute(taskName)
 *   2. execute() logs start/done markers and delegates to run()
 *   3. If run() throws, returnCode=-1 and returnMessage=exception message
 *   4. TaskJob persists getReturnCode() + getLog() into SYS_TASK_EXECUTION_LOGS
 */
public abstract class Runner {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private int returnCode = 0;
    private String returnMessage;
    private final StringBuilder logBuffer = new StringBuilder();

    /** Main batch logic — override in subclasses. */
    public abstract void run() throws Exception;

    /** Called by TaskJob. Captures the outcome instead of printing stack traces. */
    public Runner execute(String taskName) {
        try {
            this.returnCode = 0;
            log("JobName: [" + taskName + "] is Run");
            run();
            log("JobName: [" + taskName + "] is Done");
        } catch (Exception e) {
            this.returnCode = -1;
            this.returnMessage = e.getMessage() != null ? e.getMessage() : e.toString();
            log("JobName: [" + taskName + "] is ERROR: " + e);
        }
        return this;
    }

    public int getReturnCode() { return returnCode; }
    public String getReturnMessage() { return returnMessage; }
    public String getLog() { return logBuffer.toString(); }

    /** Helper for subclasses. */
    protected void log(String message) {
        logBuffer.append(FMT.format(LocalDateTime.now())).append(" - ").append(message).append('\n');
    }
}
