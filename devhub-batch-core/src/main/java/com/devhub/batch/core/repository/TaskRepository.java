package com.devhub.batch.core.repository;

import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.model.TaskExecutionLog;
import com.devhub.batch.core.model.WorkerInfo;

import java.time.LocalDateTime;
import java.util.List;

/** Repository contract for the standalone scheduler (multi-worker safe). */
public interface TaskRepository {

    List<ScheduledTask> findAll();
    List<ScheduledTask> findByStatus(String status);
    List<ScheduledTask> findRunningTasks();
    ScheduledTask findById(Long id);
    ScheduledTask save(ScheduledTask task);
    void updateStatus(Long taskId, String status);
    void updateAfterRun(Long taskId, LocalDateTime lastRun, LocalDateTime nextRun, String status);

    /**
     * Same as above, also persisting the dead-letter counter. attemptCount null
     * keeps the current ATTEMPT_COUNT; 0 resets it after a success.
     */
    default void updateAfterRun(Long taskId, LocalDateTime lastRun, LocalDateTime nextRun, String status, Integer attemptCount) {
        updateAfterRun(taskId, lastRun, nextRun, status);
    }
    /** Leader-only: move an unclaimed, overdue task forward per its misfire policy. */
    void updateNextRunAt(Long taskId, LocalDateTime nextRunAt);
    void delete(Long taskId);

    /**
     * Atomic claim: exactly one concurrent caller wins. A claim is allowed when
     * the task is SCHEDULED and either unclaimed or its claim is stale
     * (CLAIMED_AT older than staleClaimBefore).
     */
    boolean claimTask(Long taskId, String workerId, LocalDateTime staleClaimBefore);
    void clearTaskClaim(Long taskId);

    /**
     * Marks a stale RUNNING run. With requeueNextRun null the task ends as
     * TIMEOUT; otherwise it returns to SCHEDULED at the given time.
     */
    void markTaskTimeout(Long taskId, String byWorkerId, String message, LocalDateTime requeueNextRun);

    void saveLog(Long taskId, String status, String result, String error);
    void saveExecutionLog(Long taskId, String workerId, String status, String result, String error,
                          LocalDateTime startedAt, LocalDateTime finishedAt, long durationMs);
    List<TaskExecutionLog> findLogsByTaskId(Long taskId, int limit);
    void purgeLogsBefore(LocalDateTime before);

    /** Tasks due for dispatch (SCHEDULED with NEXT_RUN_AT <= dueBefore). */
    List<ScheduledTask> findDueTasks(LocalDateTime dueBefore, int limit);
    /** Unclaimed, overdue SCHEDULED tasks for the leader misfire loop. */
    List<ScheduledTask> findOverdueUnclaimed(LocalDateTime before, int limit);

    void heartbeat(WorkerInfo worker, Long currentTaskId, String currentTaskName, String status, String error);
    void markWorkerOffline(String workerId);
    void markStaleWorkers(LocalDateTime heartbeatOlderThan);
}
