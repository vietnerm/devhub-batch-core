package com.devhub.batch.api.dao;

import com.devhub.batch.api.dto.MonitorDtos.WorkerDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Read model over SYS_SCHEDULER_WORKERS (worker registry, heartbeat-based). */
@Repository
public class WorkerQueryDao {

    private final JdbcTemplate jdbc;

    public WorkerQueryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WorkerDto> listAll() {
        String sql = """
                SELECT WORKER_ID, OWNER_ID, OWNER_NAME, MACHINE_NAME, HOSTNAME, PRIVATE_IP, PUBLIC_IP,
                       OS_NAME, OS_ARCH, JAVA_VERSION, APP_VERSION, ENVIRONMENT, LOCATION, STATUS,
                       CURRENT_TASK_ID, CURRENT_TASK_NAME, STARTED_AT, LAST_HEARTBEAT_AT, LAST_ERROR
                FROM SYS_SCHEDULER_WORKERS
                ORDER BY CASE STATUS WHEN 'ONLINE' THEN 0 WHEN 'STALE' THEN 1 ELSE 2 END,
                         LAST_HEARTBEAT_AT DESC
                """;
        return jdbc.query(sql, this::map);
    }

    private WorkerDto map(ResultSet rs, int i) throws SQLException {
        return new WorkerDto(
                rs.getString("WORKER_ID"),
                rs.getString("OWNER_ID"),
                rs.getString("OWNER_NAME"),
                rs.getString("MACHINE_NAME"),
                rs.getString("HOSTNAME"),
                rs.getString("PRIVATE_IP"),
                rs.getString("PUBLIC_IP"),
                rs.getString("OS_NAME"),
                rs.getString("OS_ARCH"),
                rs.getString("JAVA_VERSION"),
                rs.getString("APP_VERSION"),
                rs.getString("ENVIRONMENT"),
                rs.getString("LOCATION"),
                rs.getString("STATUS"),
                rs.getObject("CURRENT_TASK_ID") == null ? null : rs.getLong("CURRENT_TASK_ID"),
                rs.getString("CURRENT_TASK_NAME"),
                toLdt(rs.getTimestamp("STARTED_AT")),
                toLdt(rs.getTimestamp("LAST_HEARTBEAT_AT")),
                rs.getString("LAST_ERROR"));
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
