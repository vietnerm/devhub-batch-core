package com.devhub.batch.api.dto;

import com.devhub.batch.core.model.ScheduledTask;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public final class TaskDtos {

    private TaskDtos() {}

    public record TaskDto(
            Long id, String functionCode, String functionName, String taskName, String beanClass,
            String scheduleType, String cronExpression, LocalDateTime scheduledTime,
            LocalDateTime startAt, LocalDateTime endAt, String timezone, String misfirePolicy,
            String status, Integer priority, String description, boolean requeueOnFailure,
            Integer timeoutSeconds, String workerId, LocalDateTime claimedAt,
            LocalDateTime lastRunAt, LocalDateTime nextRunAt, LocalDateTime createdAt, LocalDateTime updatedAt) {

        public static TaskDto fromModel(ScheduledTask t) {
            return new TaskDto(t.getId(), t.getFunctionCode(), t.getFunctionName(), t.getTaskName(), t.getBeanClass(),
                    t.getScheduleType(), t.getCronExpression(), t.getScheduledTime(),
                    t.getStartAt(), t.getEndAt(), t.getTimezone(), t.getMisfirePolicy(),
                    t.getStatus(), t.getPriority(), t.getDescription(), t.isRequeueOnFailure(),
                    t.getTimeoutSeconds(), t.getWorkerId(), t.getClaimedAt(),
                    t.getLastRunAt(), t.getNextRunAt(), t.getCreatedAt(), t.getUpdatedAt());
        }

        public static TaskDto fromRow(ResultSet rs) throws SQLException {
            LocalDateTime sched = ts(rs, "SCHEDULED_TIME");
            LocalDateTime start = ts(rs, "START_AT");
            LocalDateTime end = ts(rs, "END_AT");
            LocalDateTime claimed = ts(rs, "CLAIMED_AT");
            LocalDateTime last = ts(rs, "LAST_RUN_AT");
            LocalDateTime next = ts(rs, "NEXT_RUN_AT");
            LocalDateTime created = ts(rs, "CREATED_AT");
            LocalDateTime updated = ts(rs, "UPDATED_AT");
            int requeue = rs.getInt("REQUEUE_ON_FAILURE");
            int timeout = rs.getInt("TIMEOUT_SECONDS");
            return new TaskDto(
                    rs.getLong("ID"),
                    rs.getString("FUNCTION_CODE"),
                    rs.getString("FUNCTION_NAME"),
                    rs.getString("TASK_NAME"),
                    rs.getString("BEAN_CLASS"),
                    rs.getString("SCHEDULE_TYPE"),
                    rs.getString("CRON_EXPRESSION"),
                    sched, start, end,
                    rs.getString("TIMEZONE"),
                    rs.getString("MISFIRE_POLICY"),
                    rs.getString("STATUS"),
                    rs.getInt("PRIORITY"),
                    rs.getString("DESCRIPTION"),
                    requeue != 0,
                    timeout,
                    rs.getString("WORKER_ID"),
                    claimed, last, next, created, updated);
        }

        private static LocalDateTime ts(ResultSet rs, String column) throws SQLException {
            java.sql.Timestamp value = rs.getTimestamp(column);
            return value != null ? value.toLocalDateTime() : null;
        }
    }

    public record TaskPage(List<TaskDto> items, long total, int page, int size) {}

    public record TaskDetail(TaskDto task, List<MonitorDtos.LogDto> recentLogs) {}

    public record CreateTaskRequest(
            String functionCode, String functionName, String taskName, String beanClass,
            String scheduleType, String cronExpression, LocalDateTime scheduledTime,
            LocalDateTime startAt, LocalDateTime endAt, String timezone, String misfirePolicy,
            Integer priority, String description, Boolean requeueOnFailure, Integer timeoutSeconds) {}

    public record UpdateTaskRequest(
            String functionCode, String functionName, String taskName, String beanClass,
            String scheduleType, String cronExpression, LocalDateTime scheduledTime,
            LocalDateTime startAt, LocalDateTime endAt, String timezone, String misfirePolicy,
            Integer priority, String description, Boolean requeueOnFailure, Integer timeoutSeconds) {}
}
