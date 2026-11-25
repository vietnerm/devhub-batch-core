package com.devhub.batch.api.dao;

import com.devhub.batch.api.dto.MonitorDtos.LogDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Read model over SYS_TASK_EXECUTION_LOGS (execution history + realtime tail). */
@Repository
public class LogQueryDao {

    private final JdbcTemplate jdbc;

    public LogQueryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT = """
            SELECT l.ID, l.TASK_ID, l.WORKER_ID, l.STATUS, l.RESULT, l.ERROR_MESSAGE,
                   l.EXECUTED_AT, l.STARTED_AT, l.FINISHED_AT, l.DURATION_MS, t.TASK_NAME
            FROM SYS_TASK_EXECUTION_LOGS l
            LEFT JOIN SYS_SCHEDULED_TASKS t ON t.ID = l.TASK_ID
            """;

    public List<LogDto> findByTask(Long taskId, int limit) {
        return jdbc.query(SELECT + " WHERE l.TASK_ID = ? ORDER BY l.EXECUTED_AT DESC, l.ID DESC LIMIT ?",
                this::map, taskId, limit);
    }

    /** Realtime tail: log rows created after the given id, oldest first. */
    public List<LogDto> tailAfter(long afterId, int limit) {
        return jdbc.query(SELECT + " WHERE l.ID > ? ORDER BY l.ID ASC LIMIT ?", this::map, afterId, limit);
    }

    public long maxId() {
        Long value = jdbc.queryForObject("SELECT COALESCE(MAX(ID), 0) FROM SYS_TASK_EXECUTION_LOGS", Long.class);
        return value == null ? 0 : value;
    }

    public List<LogDto> recentFailures(int limit) {
        return jdbc.query(SELECT + " WHERE l.STATUS IN ('FAILED','TIMEOUT') ORDER BY l.EXECUTED_AT DESC LIMIT ?",
                this::map, limit);
    }

    private LogDto map(ResultSet rs, int i) throws SQLException {
        return new LogDto(
                rs.getLong("ID"),
                rs.getLong("TASK_ID"),
                rs.getString("TASK_NAME"),
                rs.getString("WORKER_ID"),
                rs.getString("STATUS"),
                rs.getString("RESULT"),
                rs.getString("ERROR_MESSAGE"),
                toLdt(rs.getTimestamp("EXECUTED_AT")),
                toLdt(rs.getTimestamp("STARTED_AT")),
                toLdt(rs.getTimestamp("FINISHED_AT")),
                rs.getObject("DURATION_MS") == null ? null : rs.getLong("DURATION_MS"));
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
