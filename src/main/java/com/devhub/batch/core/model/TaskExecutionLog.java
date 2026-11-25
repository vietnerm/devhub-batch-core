package com.devhub.batch.core.model;

import java.time.LocalDateTime;

/** POJO mapped onto SYS_TASK_EXECUTION_LOGS. */
public class TaskExecutionLog {
    private Long id;
    private Long taskId;
    private String workerId;
    private String status;        // SUCCESS / FAILED / TIMEOUT
    private String result;
    private String errorMessage;
    private LocalDateTime executedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long v) { taskId = v; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String v) { workerId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getResult() { return result; }
    public void setResult(String v) { result = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime v) { executedAt = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { startedAt = v; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime v) { finishedAt = v; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long v) { durationMs = v; }
}
