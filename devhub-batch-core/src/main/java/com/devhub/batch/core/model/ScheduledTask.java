package com.devhub.batch.core.model;

import java.time.LocalDateTime;

/**
 * POJO mapped onto SYS_SCHEDULED_TASKS. Column names and status vocabulary
 * (SCHEDULED / RUNNING / PAUSED / STOPPED / ERROR / TIMEOUT / COMPLETED) are
 * kept compatible with the devhub-rbac-web scheduler UI.
 */
public class ScheduledTask {
    private Long id;
    private String functionCode;
    private String functionName;
    private String taskName;
    private String beanClass;
    private String scheduleType;      // ONCE / RECURRING
    private String cronExpression;
    private LocalDateTime scheduledTime;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String timezone;
    private String misfirePolicy;     // FIRE_NOW / SKIP_TO_NEXT (+ legacy aliases)
    private String status;
    private Integer priority;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private boolean requeueOnFailure;
    private Integer attemptCount;     // failures since last success (dead-letter counter)
    private Integer maxAttempt;       // requeue attempts allowed before STATUS='DEAD'
    private Integer timeoutSeconds;
    private String workerId;          // claim state
    private LocalDateTime claimedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getFunctionCode() { return functionCode; }
    public void setFunctionCode(String v) { functionCode = v; }
    public String getFunctionName() { return functionName; }
    public void setFunctionName(String v) { functionName = v; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String v) { taskName = v; }
    public String getBeanClass() { return beanClass; }
    public void setBeanClass(String v) { beanClass = v; }
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String v) { scheduleType = v; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String v) { cronExpression = v; }
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime v) { scheduledTime = v; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime v) { startAt = v; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime v) { endAt = v; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String v) { timezone = v; }
    public String getMisfirePolicy() { return misfirePolicy; }
    public void setMisfirePolicy(String v) { misfirePolicy = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer v) { priority = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime v) { lastRunAt = v; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime v) { nextRunAt = v; }
    public boolean isRequeueOnFailure() { return requeueOnFailure; }
    public void setRequeueOnFailure(boolean v) { requeueOnFailure = v; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer v) { attemptCount = v; }
    public Integer getMaxAttempt() { return maxAttempt; }
    public void setMaxAttempt(Integer v) { maxAttempt = v; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer v) { timeoutSeconds = v; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String v) { workerId = v; }
    public LocalDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(LocalDateTime v) { claimedAt = v; }

    public int effectiveTimeoutSeconds() {
        return timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : 3600;
    }

    @Override public String toString() {
        return "ScheduledTask{id=" + id + ", taskName='" + taskName + "'"
                + ", type='" + scheduleType + "'"
                + ", status='" + status + "'"
                + ", nextRunAt=" + nextRunAt
                + ", requeueOnFailure=" + requeueOnFailure + '}';
    }
}
