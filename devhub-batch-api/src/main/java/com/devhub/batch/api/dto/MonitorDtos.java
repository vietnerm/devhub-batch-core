package com.devhub.batch.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MonitorDtos {

    private MonitorDtos() {}

    public record LogDto(
            Long id, Long taskId, String taskName, String workerId, String status,
            String result, String errorMessage, LocalDateTime executedAt,
            LocalDateTime startedAt, LocalDateTime finishedAt, Long durationMs) {}

    public record WorkerDto(
            String workerId, String ownerId, String ownerName, String machineName,
            String hostname, String privateIp, String publicIp, String osName, String osArch,
            String javaVersion, String appVersion, String environment, String location,
            String status, Long currentTaskId, String currentTaskName,
            LocalDateTime startedAt, LocalDateTime lastHeartbeatAt, String lastError) {}

    public record FailureTop(String taskName, long count) {}

    public record StatsSummary(
            Map<String, Long> tasksByStatus,
            long runningNow,
            Map<String, Long> workersByStatus,
            long workersOnline,
            long executions24h,
            long success24h,
            long failed24h,
            long timeout24h,
            double successRatePct,
            long avgDurationMs,
            List<FailureTop> topFailures) {}

    public record TimelinePoint(String hour, long success, long failed, long timeout, long total) {}

    public record CronCheckRequest(
            String scheduleType, String cronExpression, String timezone, LocalDateTime scheduledTime) {}

    public record CronCheckResponse(
            boolean valid, String message, String scheduleType, List<String> nextRuns) {}

    public record WsEvent(String type, Object payload, String occurredAt) {}
}
