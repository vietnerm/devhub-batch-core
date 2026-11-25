package com.devhub.batch.core;

import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.repository.TaskRepository;
import com.devhub.batch.core.schedule.SchedulePlanner;
import com.devhub.batch.core.schedule.ScheduleValidator;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One execution attempt: atomic claim -> RUNNING -> execution log -> next schedule.
 *
 * Rewrite notes:
 *  - The claim is a single conditional UPDATE; losing callers simply skip.
 *  - NEXT_RUN_AT is maintained in the database (SchedulePlanner.afterRun is
 *    strictly increasing, fix Y5) — there are no in-memory futures to drift.
 *  - The claim guard uses the task's own TIMEOUT_SECONDS, so a crashed worker
 *    releases its claim without the old start-up RUNNING->TIMEOUT sweep (fix Y1).
 */
public class TaskJob implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TaskJob.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("devhub.batch.core");
    private static final Meter meter = GlobalOpenTelemetry.getMeter("devhub.batch.core");
    private static final LongCounter executionCounter = meter.counterBuilder("devhub.batch.core.executions")
            .setDescription("Completed scheduled batch executions")
            .build();

    private final long taskId;
    private final TaskRepository repo;
    private final String workerId;
    private final BiConsumer<Long, String> onClaim;
    private final Consumer<Long> onFinish;

    public TaskJob(TaskRepository repo, long taskId, String workerId,
                   BiConsumer<Long, String> onClaim, Consumer<Long> onFinish) {
        this.repo = repo;
        this.taskId = taskId;
        this.workerId = workerId;
        this.onClaim = onClaim;
        this.onFinish = onFinish;
    }

    @Override
    public void run() {
        ScheduledTask task = repo.findById(taskId);
        if (task == null) {
            log.warn("event=task.not_found taskId={}", taskId);
            return;
        }
        ScheduleValidator.Result scheduleCheck = ScheduleValidator.validate(task.getScheduleType(),
                task.getCronExpression(), task.getScheduledTime(), task.getStartAt(), task.getEndAt(), task.getDescription());
        if (!scheduleCheck.valid()) {
            repo.updateStatus(taskId, "ERROR");
            repo.saveExecutionLog(taskId, workerId, "FAILED", null, scheduleCheck.message(),
                    LocalDateTime.now(), LocalDateTime.now(), 0);
            log.warn("event=task.invalid_schedule taskId={} reason={}", taskId, scheduleCheck.message());
            return;
        }

        LocalDateTime staleClaimBefore = LocalDateTime.now().minusSeconds(task.effectiveTimeoutSeconds());
        if (!repo.claimTask(taskId, workerId, staleClaimBefore)) {
            log.info("event=task.claim_skipped taskId={}", taskId);
            return;
        }

        String batchCode = safeBatchCode(task.getFunctionCode());
        LocalDateTime started = LocalDateTime.now();
        Span span = tracer.spanBuilder("devhub.batch " + batchCode).setSpanKind(SpanKind.INTERNAL).startSpan();
        MDC.put("devhub.batch.code", batchCode);
        MDC.put("devhub.batch.status", "RUNNING");
        if (onClaim != null) onClaim.accept(taskId, task.getTaskName());
        String result = "";
        String error = null;
        boolean success = false;
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("devhub.batch.code", batchCode);
            span.setAttribute("devhub.batch.status", "RUNNING");
            span.addEvent("devhub.batch.started");
            log.info("event=task.started taskId={} batch_code={}", taskId, batchCode);
            try {
                Outcome outcome = invokeHandler(task);
                success = outcome.success();
                result = outcome.log();
                if (!success) error = outcome.error();
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
                span.setStatus(StatusCode.ERROR);
                span.setAttribute("error.type", e.getClass().getSimpleName());
            }

            LocalDateTime finished = LocalDateTime.now();
            long duration = Duration.between(started, finished).toMillis();
            String logStatus = success ? "SUCCESS" : "FAILED";
            repo.saveExecutionLog(taskId, workerId, logStatus, result, error, started, finished, duration);

            boolean recurring = ScheduleValidator.RECURRING.equals(scheduleCheck.scheduleType());
            boolean requeueAfterFailure = !success && recurring && task.isRequeueOnFailure();
            LocalDateTime nextRun = null;
            try {
                nextRun = SchedulePlanner.afterRun(task, finished);
            } catch (Exception e) {
                error = e.getMessage();
                log.error("event=task.next_run_failed taskId={} error={}", taskId, e.getMessage());
            }
            String nextStatus;
            if (success) {
                nextStatus = recurring && nextRun != null ? "SCHEDULED" : "COMPLETED";
            } else {
                nextStatus = requeueAfterFailure && nextRun != null ? "SCHEDULED" : "ERROR";
            }
            repo.updateAfterRun(taskId, finished, nextRun, nextStatus);

            span.setAttribute("devhub.batch.status", nextStatus);
            span.setAttribute("devhub.outcome", success ? "success" : "server_error");
            span.addEvent("devhub.batch.completed", Attributes.builder().put("devhub.batch.status", nextStatus).build());
            executionCounter.add(1, Attributes.builder()
                    .put("devhub.batch.code", batchCode)
                    .put("devhub.batch.status", nextStatus)
                    .put("devhub.outcome", success ? "success" : "server_error")
                    .build());
            log.info("event=task.completed taskId={} batch_code={} status={} duration_ms={}",
                    taskId, batchCode, nextStatus, duration);
        } finally {
            span.end();
            MDC.remove("devhub.batch.code");
            MDC.remove("devhub.batch.status");
            if (onFinish != null) onFinish.accept(taskId);
        }
    }

    private Outcome invokeHandler(ScheduledTask task) throws Exception {
        Class<?> clazz = Class.forName(task.getBeanClass().trim());
        Object instance = clazz.getDeclaredConstructor().newInstance();
        if (instance instanceof Runner runner) {
            runner.execute(task.getTaskName());
            boolean ok = runner.getReturnCode() == 0;
            return new Outcome(ok, runner.getLog(), runner.getReturnMessage());
        }
        try {
            clazz.getMethod("execute", String.class).invoke(instance, task.getTaskName());
            return new Outcome(true, "Executed via reflection (execute(String))", null);
        } catch (NoSuchMethodException e) {
            throw new ClassCastException(task.getBeanClass()
                    + " does not extend Runner and has no execute(String) method");
        }
    }

    private record Outcome(boolean success, String log, String error) {}

    private static String safeBatchCode(String code) {
        return code != null && code.matches("^[A-Z]{2}[A-Z0-9_]{3,64}$") ? code : "unknown";
    }
}
