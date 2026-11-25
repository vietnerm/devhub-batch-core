package com.devhub.batch.api.dao;

import com.devhub.batch.api.dto.MonitorDtos.FailureTop;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregations for the dashboard KPI + 24h timeline. Buckets are built in
 *  Java to stay portable across H2 (demo) and MySQL 8 (production). */
@Repository
public class StatsDao {

    private final JdbcTemplate jdbc;

    public StatsDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Long> workersByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT STATUS, COUNT(*) AS C FROM SYS_SCHEDULER_WORKERS GROUP BY STATUS", rs -> {
            counts.put(rs.getString("STATUS"), rs.getLong("C"));
        });
        return counts;
    }

    public Map<String, Long> executionsByStatus(LocalDateTime since) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT STATUS, COUNT(*) AS C FROM SYS_TASK_EXECUTION_LOGS WHERE EXECUTED_AT >= ? GROUP BY STATUS",
                rs -> {
                    counts.put(rs.getString("STATUS"), rs.getLong("C"));
                },
                Timestamp.valueOf(since));
        return counts;
    }

    public long avgDurationSince(LocalDateTime since) {
        Double avg = jdbc.queryForObject(
                "SELECT COALESCE(AVG(DURATION_MS), 0) FROM SYS_TASK_EXECUTION_LOGS WHERE EXECUTED_AT >= ?",
                Double.class, Timestamp.valueOf(since));
        return avg == null ? 0 : Math.round(avg);
    }

    /** (executedAt, status) rows since the cutoff for the hourly timeline. */
    public List<Object[]> executionRowsSince(LocalDateTime since) {
        return jdbc.query(
                "SELECT EXECUTED_AT, STATUS FROM SYS_TASK_EXECUTION_LOGS WHERE EXECUTED_AT >= ?",
                (rs, i) -> new Object[]{rs.getTimestamp("EXECUTED_AT").toLocalDateTime(), rs.getString("STATUS")},
                Timestamp.valueOf(since));
    }

    public List<FailureTop> topFailures(LocalDateTime since, int limit) {
        return jdbc.query("""
                SELECT t.TASK_NAME AS TASK_NAME, COUNT(*) AS C
                FROM SYS_TASK_EXECUTION_LOGS l
                JOIN SYS_SCHEDULED_TASKS t ON t.ID = l.TASK_ID
                WHERE l.STATUS IN ('FAILED','TIMEOUT') AND l.EXECUTED_AT >= ?
                GROUP BY t.TASK_NAME ORDER BY C DESC LIMIT ?
                """,
                (rs, i) -> new FailureTop(rs.getString("TASK_NAME"), rs.getLong("C")),
                Timestamp.valueOf(since), limit);
    }
}
