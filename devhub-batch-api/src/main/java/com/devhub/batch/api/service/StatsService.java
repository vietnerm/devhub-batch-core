package com.devhub.batch.api.service;

import com.devhub.batch.api.dao.LogQueryDao;
import com.devhub.batch.api.dao.StatsDao;
import com.devhub.batch.api.dao.TaskQueryDao;
import com.devhub.batch.api.dao.WorkerQueryDao;
import com.devhub.batch.api.dto.MonitorDtos.FailureTop;
import com.devhub.batch.api.dto.MonitorDtos.StatsSummary;
import com.devhub.batch.api.dto.MonitorDtos.TimelinePoint;
import org.springframework.stereotype.Service;

import com.devhub.batch.core.support.Times;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard KPI aggregation (H2-safe: buckets built in Java). */
@Service
public class StatsService {

    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:00");

    private final TaskQueryDao tasks;
    private final StatsDao stats;
    private final WorkerQueryDao workers;
    private final LogQueryDao logs;

    public StatsService(TaskQueryDao tasks, StatsDao stats, WorkerQueryDao workers, LogQueryDao logs) {
        this.tasks = tasks;
        this.stats = stats;
        this.workers = workers;
        this.logs = logs;
    }

    public StatsSummary summary() {
        LocalDateTime since = Times.nowUtc().minusHours(24);

        Map<String, Long> tasksByStatus = tasks.countByStatus();
        Map<String, Long> workersByStatus = stats.workersByStatus();
        Map<String, Long> executions = stats.executionsByStatus(since);

        long runningNow = tasksByStatus.getOrDefault("RUNNING", 0L);
        long workersOnline = workersByStatus.getOrDefault("ONLINE", 0L);
        long success = executions.getOrDefault("SUCCESS", 0L);
        long failed = executions.getOrDefault("FAILED", 0L);
        long timeout = executions.getOrDefault("TIMEOUT", 0L);
        long total = success + failed + timeout + executions.getOrDefault("COMPLETED", 0L);
        double successRate = success + failed + timeout > 0
                ? Math.round(1000.0 * success / (success + failed + timeout)) / 10.0
                : 100.0;
        long avgDuration = stats.avgDurationSince(since);
        List<FailureTop> topFailures = stats.topFailures(since, 5);

        return new StatsSummary(tasksByStatus, runningNow, workersByStatus, workersOnline,
                total, success, failed, timeout, successRate, avgDuration, topFailures);
    }

    public List<TimelinePoint> timeline(int hours) {
        int window = Math.max(1, Math.min(72, hours));
        LocalDateTime since = Times.nowUtc().truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .minusHours(window - 1L);

        // Hour buckets: label -> mutable counters.
        Map<String, long[]> buckets = new LinkedHashMap<>();
        LocalDateTime cursor = since;
        for (int i = 0; i < window; i++) {
            buckets.put(HOUR_LABEL.format(cursor), new long[4]); // success, failed, timeout, total
            cursor = cursor.plusHours(1);
        }

        for (Object[] row : stats.executionRowsSince(since)) {
            LocalDateTime executedAt = (LocalDateTime) row[0];
            String status = (String) row[1];
            long[] bucket = buckets.get(HOUR_LABEL.format(executedAt));
            if (bucket == null) continue;
            int slot = switch (status) {
                case "SUCCESS", "COMPLETED" -> 0;
                case "FAILED" -> 1;
                case "TIMEOUT" -> 2;
                default -> -1;
            };
            if (slot >= 0) bucket[slot]++;
            bucket[3]++;
        }

        List<TimelinePoint> points = new ArrayList<>();
        buckets.forEach((label, b) -> points.add(new TimelinePoint(label, b[0], b[1], b[2], b[3])));
        return points;
    }

    public com.devhub.batch.api.dto.MonitorDtos.LogDto latestFailure() {
        List<com.devhub.batch.api.dto.MonitorDtos.LogDto> failures = logs.recentFailures(1);
        return failures.isEmpty() ? null : failures.get(0);
    }
}
