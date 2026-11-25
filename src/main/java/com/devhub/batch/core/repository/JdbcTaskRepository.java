package com.devhub.batch.core.repository;

import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.model.TaskExecutionLog;
import com.devhub.batch.core.model.WorkerInfo;
import com.devhub.batch.core.schedule.SchedulePlanner;
import com.devhub.batch.core.schedule.ScheduleValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure JDBC implementation — no Spring/JPA, only {@link DataSource}.
 *
 * The claim is a single conditional UPDATE (atomic in MySQL and H2):
 * exactly one concurrent worker can move a SCHEDULED task to RUNNING.
 */
public class JdbcTaskRepository implements TaskRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTaskRepository.class);

    private final DataSource dataSource;

    public JdbcTaskRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /* ---------- Query ---------- */

    @Override
    public List<ScheduledTask> findAll() {
        return queryList("SELECT * FROM SYS_SCHEDULED_TASKS ORDER BY ID");
    }

    @Override
    public List<ScheduledTask> findByStatus(String status) {
        return queryList("SELECT * FROM SYS_SCHEDULED_TASKS WHERE STATUS = ? ORDER BY PRIORITY DESC, ID", status);
    }

    @Override
    public List<ScheduledTask> findRunningTasks() {
        return queryList("SELECT * FROM SYS_SCHEDULED_TASKS WHERE STATUS = 'RUNNING' ORDER BY CLAIMED_AT ASC");
    }

    @Override
    public ScheduledTask findById(Long id) {
        return queryOne("SELECT * FROM SYS_SCHEDULED_TASKS WHERE ID = ?", id);
    }

    @Override
    public List<ScheduledTask> findDueTasks(LocalDateTime dueBefore, int limit) {
        return queryList("SELECT * FROM SYS_SCHEDULED_TASKS WHERE STATUS = 'SCHEDULED' "
                + "AND NEXT_RUN_AT IS NOT NULL AND NEXT_RUN_AT <= ? "
                + "ORDER BY PRIORITY DESC, NEXT_RUN_AT ASC LIMIT ?", toTs(dueBefore), limit);
    }

    @Override
    public List<ScheduledTask> findOverdueUnclaimed(LocalDateTime before, int limit) {
        return queryList("SELECT * FROM SYS_SCHEDULED_TASKS WHERE STATUS = 'SCHEDULED' "
                + "AND NEXT_RUN_AT IS NOT NULL AND NEXT_RUN_AT < ? "
                + "AND (WORKER_ID IS NULL OR WORKER_ID = '') LIMIT ?", toTs(before), limit);
    }

    /* ---------- Mutation ---------- */

    @Override
    public ScheduledTask save(ScheduledTask task) {
        if (task.getId() == null) return insert(task);
        return update(task);
    }

    private ScheduledTask insert(ScheduledTask task) {
        validateSchedule(task);
        String sql = "INSERT INTO SYS_SCHEDULED_TASKS "
                + "(FUNCTION_CODE,FUNCTION_NAME,TASK_NAME,BEAN_CLASS,SCHEDULE_TYPE,CRON_EXPRESSION,SCHEDULED_TIME,"
                + "START_AT,END_AT,TIMEZONE,MISFIRE_POLICY,STATUS,PRIORITY,DESCRIPTION,NEXT_RUN_AT,REQUEUE_ON_FAILURE,TIMEOUT_SECONDS) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql, StatementKeys.RETURN_GENERATED_KEYS)) {
            LocalDateTime nextRun = SchedulePlanner.initialNextRun(task, LocalDateTime.now());
            ps.setString(1, task.getFunctionCode());
            ps.setString(2, task.getFunctionName());
            ps.setString(3, task.getTaskName());
            ps.setString(4, task.getBeanClass());
            ps.setString(5, task.getScheduleType());
            ps.setString(6, task.getCronExpression());
            ps.setTimestamp(7, toTs(task.getScheduledTime()));
            ps.setTimestamp(8, toTs(task.getStartAt()));
            ps.setTimestamp(9, toTs(task.getEndAt()));
            ps.setString(10, defaultTimezone(task));
            ps.setString(11, defaultMisfire(task));
            ps.setString(12, task.getStatus() != null ? task.getStatus() : "SCHEDULED");
            ps.setInt(13, task.getPriority() != null ? task.getPriority() : 0);
            ps.setString(14, task.getDescription());
            ps.setTimestamp(15, toTs(nextRun));
            ps.setBoolean(16, task.isRequeueOnFailure());
            ps.setInt(17, task.effectiveTimeoutSeconds());
            task.setNextRunAt(nextRun);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) task.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Insert task failed: " + e.getMessage(), e);
        }
        return task;
    }

    private ScheduledTask update(ScheduledTask task) {
        validateSchedule(task);
        String sql = "UPDATE SYS_SCHEDULED_TASKS SET "
                + "FUNCTION_CODE=?,FUNCTION_NAME=?,TASK_NAME=?,BEAN_CLASS=?,SCHEDULE_TYPE=?,CRON_EXPRESSION=?,SCHEDULED_TIME=?,"
                + "START_AT=?,END_AT=?,TIMEZONE=?,MISFIRE_POLICY=?,STATUS=?,PRIORITY=?,DESCRIPTION=?,REQUEUE_ON_FAILURE=?,TIMEOUT_SECONDS=? "
                + "WHERE ID=?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, task.getFunctionCode());
            ps.setString(2, task.getFunctionName());
            ps.setString(3, task.getTaskName());
            ps.setString(4, task.getBeanClass());
            ps.setString(5, task.getScheduleType());
            ps.setString(6, task.getCronExpression());
            ps.setTimestamp(7, toTs(task.getScheduledTime()));
            ps.setTimestamp(8, toTs(task.getStartAt()));
            ps.setTimestamp(9, toTs(task.getEndAt()));
            ps.setString(10, defaultTimezone(task));
            ps.setString(11, defaultMisfire(task));
            ps.setString(12, task.getStatus() != null ? task.getStatus() : "SCHEDULED");
            ps.setInt(13, task.getPriority() != null ? task.getPriority() : 0);
            ps.setString(14, task.getDescription());
            ps.setBoolean(15, task.isRequeueOnFailure());
            ps.setInt(16, task.effectiveTimeoutSeconds());
            ps.setLong(17, task.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Update task failed: " + e.getMessage(), e);
        }
        return task;
    }

    @Override
    public void updateStatus(Long taskId, String status) {
        String sql = "UPDATE SYS_SCHEDULED_TASKS SET STATUS = ?, "
                + "WORKER_ID = CASE WHEN ? IN ('SCHEDULED','PAUSED','STOPPED') THEN NULL ELSE WORKER_ID END, "
                + "CLAIMED_AT = CASE WHEN ? IN ('SCHEDULED','PAUSED','STOPPED') THEN NULL ELSE CLAIMED_AT END "
                + "WHERE ID = ?";
        executeUpdate(sql, status, status, status, taskId);
    }

    @Override
    public void updateAfterRun(Long taskId, LocalDateTime lastRun, LocalDateTime nextRun, String status) {
        String sql = "UPDATE SYS_SCHEDULED_TASKS SET STATUS=?, LAST_RUN_AT=?, NEXT_RUN_AT=?, WORKER_ID=NULL, CLAIMED_AT=NULL WHERE ID=?";
        executeUpdate(sql, status, toTs(lastRun), toTs(nextRun), taskId);
    }

    @Override
    public void updateNextRunAt(Long taskId, LocalDateTime nextRunAt) {
        String sql = "UPDATE SYS_SCHEDULED_TASKS SET NEXT_RUN_AT=?, STATUS='SCHEDULED' WHERE ID=? AND STATUS='SCHEDULED'";
        executeUpdate(sql, toTs(nextRunAt), taskId);
    }

    @Override
    public boolean claimTask(Long taskId, String workerId, LocalDateTime staleClaimBefore) {
        String sql = "UPDATE SYS_SCHEDULED_TASKS SET STATUS='RUNNING', WORKER_ID=?, CLAIMED_AT=NOW() "
                + "WHERE ID=? AND STATUS='SCHEDULED' "
                + "AND (WORKER_ID IS NULL OR WORKER_ID='' OR CLAIMED_AT < ?)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, workerId);
            ps.setLong(2, taskId);
            ps.setTimestamp(3, toTs(staleClaimBefore));
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RepositoryException("claimTask failed", e);
        }
    }

    @Override
    public void clearTaskClaim(Long taskId) {
        executeUpdate("UPDATE SYS_SCHEDULED_TASKS SET WORKER_ID=NULL, CLAIMED_AT=NULL WHERE ID=?", taskId);
    }

    @Override
    public void markTaskTimeout(Long taskId, String byWorkerId, String message, LocalDateTime requeueNextRun) {
        if (requeueNextRun != null) {
            executeUpdate("UPDATE SYS_SCHEDULED_TASKS SET STATUS='SCHEDULED', WORKER_ID=NULL, CLAIMED_AT=NULL, NEXT_RUN_AT=? "
                    + "WHERE ID=? AND STATUS='RUNNING'", toTs(requeueNextRun), taskId);
        } else {
            executeUpdate("UPDATE SYS_SCHEDULED_TASKS SET STATUS='TIMEOUT', WORKER_ID=?, CLAIMED_AT=NULL "
                    + "WHERE ID=? AND STATUS='RUNNING'", byWorkerId, taskId);
        }
        saveExecutionLog(taskId, byWorkerId, "TIMEOUT", null, message, LocalDateTime.now(), LocalDateTime.now(), 0);
    }

    @Override
    public void delete(Long taskId) {
        executeUpdate("DELETE FROM SYS_SCHEDULED_TASKS WHERE ID = ?", taskId);
    }

    /* ---------- Logs ---------- */

    @Override
    public void saveLog(Long taskId, String status, String result, String error) {
        saveExecutionLog(taskId, null, status, result, error, LocalDateTime.now(), LocalDateTime.now(), 0);
    }

    @Override
    public void saveExecutionLog(Long taskId, String workerId, String status, String result, String error,
                                 LocalDateTime startedAt, LocalDateTime finishedAt, long durationMs) {
        String sql = "INSERT INTO SYS_TASK_EXECUTION_LOGS "
                + "(TASK_ID,WORKER_ID,STATUS,RESULT,ERROR_MESSAGE,EXECUTED_AT,STARTED_AT,FINISHED_AT,DURATION_MS) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.setString(2, workerId);
            ps.setString(3, status);
            ps.setString(4, result);
            ps.setString(5, error);
            ps.setTimestamp(6, toTs(finishedAt == null ? LocalDateTime.now() : finishedAt));
            ps.setTimestamp(7, toTs(startedAt));
            ps.setTimestamp(8, toTs(finishedAt));
            ps.setLong(9, durationMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveExecutionLog failed: {}", e.getMessage());
        }
    }

    @Override
    public List<TaskExecutionLog> findLogsByTaskId(Long taskId, int limit) {
        String sql = "SELECT * FROM SYS_TASK_EXECUTION_LOGS WHERE TASK_ID = ? ORDER BY EXECUTED_AT DESC, ID DESC LIMIT ?";
        List<TaskExecutionLog> list = new ArrayList<>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapLog(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("findLogsByTaskId failed", e);
        }
        return list;
    }

    @Override
    public void purgeLogsBefore(LocalDateTime before) {
        executeUpdate("DELETE FROM SYS_TASK_EXECUTION_LOGS WHERE EXECUTED_AT < ?", toTs(before));
    }

    /* ---------- Worker registry ---------- */

    @Override
    public void heartbeat(WorkerInfo worker, Long currentTaskId, String currentTaskName, String status, String error) {
        // Portable upsert: update first, insert when the worker row is new.
        // (No MySQL-only ON DUPLICATE KEY syntax, so H2 unit tests behave the same.)
        String update = "UPDATE SYS_SCHEDULER_WORKERS SET "
                + "OWNER_ID=?,OWNER_NAME=?,OWNER_EMAIL=?,MACHINE_NAME=?,HOSTNAME=?,PRIVATE_IP=?,PUBLIC_IP=?,MAC_ADDRESS=?,"
                + "OS_NAME=?,OS_VERSION=?,OS_ARCH=?,JAVA_VERSION=?,JAVA_VENDOR=?,APP_VERSION=?,ENVIRONMENT=?,LOCATION=?,"
                + "CONTAINER_ID=?,PID=?,RUNTIME_ID=?,STATUS=?,CURRENT_TASK_ID=?,CURRENT_TASK_NAME=?,LAST_ERROR=?,"
                + "LAST_HEARTBEAT_AT=NOW() WHERE WORKER_ID=?";
        String insert = "INSERT INTO SYS_SCHEDULER_WORKERS "
                + "(WORKER_ID,OWNER_ID,OWNER_NAME,OWNER_EMAIL,MACHINE_NAME,HOSTNAME,PRIVATE_IP,PUBLIC_IP,MAC_ADDRESS,"
                + "OS_NAME,OS_VERSION,OS_ARCH,JAVA_VERSION,JAVA_VENDOR,APP_VERSION,ENVIRONMENT,LOCATION,CONTAINER_ID,"
                + "PID,RUNTIME_ID,STATUS,CURRENT_TASK_ID,CURRENT_TASK_NAME,LAST_HEARTBEAT_AT,LAST_ERROR) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),?)";
        try (Connection c = getConnection()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement(update)) {
                bindWorker(ps, worker, currentTaskId, currentTaskName, status, error);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = c.prepareStatement(insert)) {
                    bindWorkerInsert(ps, worker, currentTaskId, currentTaskName, status, error);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            log.error("heartbeat failed: {}", e.getMessage());
        }
    }

    @Override
    public void markWorkerOffline(String workerId) {
        String sql = "UPDATE SYS_SCHEDULER_WORKERS SET STATUS='OFFLINE', CURRENT_TASK_ID=NULL, CURRENT_TASK_NAME=NULL, "
                + "LAST_HEARTBEAT_AT=NOW() WHERE WORKER_ID=?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, workerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("markWorkerOffline failed: {}", e.getMessage());
        }
    }

    @Override
    public void markStaleWorkers(LocalDateTime heartbeatOlderThan) {
        String sql = "UPDATE SYS_SCHEDULER_WORKERS SET STATUS='STALE' "
                + "WHERE STATUS='ONLINE' AND LAST_HEARTBEAT_AT < ?";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, toTs(heartbeatOlderThan));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("markStaleWorkers failed: {}", e.getMessage());
        }
    }

    /* ---------- Helpers ---------- */

    private void bindWorker(PreparedStatement ps, WorkerInfo w, Long taskId, String taskName, String status, String error) throws SQLException {
        int i = 1;
        ps.setString(i++, w.getOwnerId());
        ps.setString(i++, w.getOwnerName());
        ps.setString(i++, w.getOwnerEmail());
        ps.setString(i++, w.getMachineName());
        ps.setString(i++, w.getMachineName());
        ps.setString(i++, w.getPrivateIp());
        ps.setString(i++, w.getPublicIp());
        ps.setString(i++, w.getMacAddress());
        ps.setString(i++, w.getOsName());
        ps.setString(i++, w.getOsVersion());
        ps.setString(i++, w.getOsArch());
        ps.setString(i++, w.getJavaVersion());
        ps.setString(i++, w.getJavaVendor());
        ps.setString(i++, w.getAppVersion());
        ps.setString(i++, w.getEnvironment());
        ps.setString(i++, w.getLocation());
        ps.setString(i++, w.getContainerId());
        ps.setString(i++, w.getPid());
        ps.setString(i++, w.getRuntimeId());
        ps.setString(i++, status);
        if (taskId == null) ps.setNull(i++, Types.BIGINT); else ps.setLong(i++, taskId);
        ps.setString(i++, taskName);
        ps.setString(i++, error);
        ps.setString(i++, w.getWorkerId());
    }

    private void bindWorkerInsert(PreparedStatement ps, WorkerInfo w, Long taskId, String taskName, String status, String error) throws SQLException {
        int i = 1;
        ps.setString(i++, w.getWorkerId());
        ps.setString(i++, w.getOwnerId());
        ps.setString(i++, w.getOwnerName());
        ps.setString(i++, w.getOwnerEmail());
        ps.setString(i++, w.getMachineName());
        ps.setString(i++, w.getMachineName());
        ps.setString(i++, w.getPrivateIp());
        ps.setString(i++, w.getPublicIp());
        ps.setString(i++, w.getMacAddress());
        ps.setString(i++, w.getOsName());
        ps.setString(i++, w.getOsVersion());
        ps.setString(i++, w.getOsArch());
        ps.setString(i++, w.getJavaVersion());
        ps.setString(i++, w.getJavaVendor());
        ps.setString(i++, w.getAppVersion());
        ps.setString(i++, w.getEnvironment());
        ps.setString(i++, w.getLocation());
        ps.setString(i++, w.getContainerId());
        ps.setString(i++, w.getPid());
        ps.setString(i++, w.getRuntimeId());
        ps.setString(i++, status);
        if (taskId == null) ps.setNull(i++, Types.BIGINT); else ps.setLong(i++, taskId);
        ps.setString(i++, taskName);
        ps.setString(i++, error);
    }

    private void executeUpdate(String sql, Object... args) {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Update failed: " + sql, e);
        }
    }

    private List<ScheduledTask> queryList(String sql, Object... args) {
        List<ScheduledTask> list = new ArrayList<>();
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapTask(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("queryList failed: " + sql, e);
        }
        return list;
    }

    private ScheduledTask queryOne(String sql, Object... args) {
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapTask(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("queryOne failed: " + sql, e);
        }
    }

    private ScheduledTask mapTask(ResultSet rs) throws SQLException {
        ScheduledTask t = new ScheduledTask();
        t.setId(rs.getLong("ID"));
        t.setFunctionCode(rs.getString("FUNCTION_CODE"));
        t.setFunctionName(rs.getString("FUNCTION_NAME"));
        t.setTaskName(rs.getString("TASK_NAME"));
        t.setBeanClass(rs.getString("BEAN_CLASS"));
        t.setScheduleType(rs.getString("SCHEDULE_TYPE"));
        t.setCronExpression(rs.getString("CRON_EXPRESSION"));
        t.setScheduledTime(fromTs(rs.getTimestamp("SCHEDULED_TIME")));
        t.setStartAt(fromTs(rs.getTimestamp("START_AT")));
        t.setEndAt(fromTs(rs.getTimestamp("END_AT")));
        t.setTimezone(rs.getString("TIMEZONE"));
        t.setMisfirePolicy(rs.getString("MISFIRE_POLICY"));
        t.setStatus(rs.getString("STATUS"));
        t.setPriority(rs.getInt("PRIORITY"));
        t.setDescription(rs.getString("DESCRIPTION"));
        t.setCreatedAt(fromTs(rs.getTimestamp("CREATED_AT")));
        t.setUpdatedAt(fromTs(rs.getTimestamp("UPDATED_AT")));
        t.setLastRunAt(fromTs(rs.getTimestamp("LAST_RUN_AT")));
        t.setNextRunAt(fromTs(rs.getTimestamp("NEXT_RUN_AT")));
        t.setRequeueOnFailure(rs.getBoolean("REQUEUE_ON_FAILURE"));
        t.setTimeoutSeconds(rs.getInt("TIMEOUT_SECONDS"));
        t.setWorkerId(rs.getString("WORKER_ID"));
        t.setClaimedAt(fromTs(rs.getTimestamp("CLAIMED_AT")));
        return t;
    }

    private TaskExecutionLog mapLog(ResultSet rs) throws SQLException {
        TaskExecutionLog l = new TaskExecutionLog();
        l.setId(rs.getLong("ID"));
        l.setTaskId(rs.getLong("TASK_ID"));
        l.setWorkerId(rs.getString("WORKER_ID"));
        l.setStatus(rs.getString("STATUS"));
        l.setResult(rs.getString("RESULT"));
        l.setErrorMessage(rs.getString("ERROR_MESSAGE"));
        l.setExecutedAt(fromTs(rs.getTimestamp("EXECUTED_AT")));
        l.setStartedAt(fromTs(rs.getTimestamp("STARTED_AT")));
        l.setFinishedAt(fromTs(rs.getTimestamp("FINISHED_AT")));
        l.setDurationMs(rs.getLong("DURATION_MS"));
        return l;
    }

    private void validateSchedule(ScheduledTask task) {
        String type = ScheduleValidator.normalizeType(task.getScheduleType(), task.getCronExpression(), task.getScheduledTime());
        ScheduleValidator.Result result = ScheduleValidator.validate(type, task.getCronExpression(), task.getScheduledTime(),
                task.getStartAt(), task.getEndAt(), task.getDescription());
        if (!result.valid()) {
            throw new IllegalArgumentException("Invalid schedule for task '" + task.getTaskName() + "': " + result.message());
        }
        String tzError = ScheduleValidator.validateTimezone(task.getTimezone());
        if (tzError != null) {
            throw new IllegalArgumentException("Invalid schedule for task '" + task.getTaskName() + "': " + tzError);
        }
        String misfireError = ScheduleValidator.validateMisfirePolicy(task.getMisfirePolicy());
        if (misfireError != null) {
            throw new IllegalArgumentException("Invalid schedule for task '" + task.getTaskName() + "': " + misfireError);
        }
        task.setScheduleType(result.scheduleType());
        if (ScheduleValidator.ONCE.equals(result.scheduleType())) task.setCronExpression(null);
        else task.setScheduledTime(null);
    }

    private String defaultTimezone(ScheduledTask task) {
        return task.getTimezone() == null || task.getTimezone().isBlank() ? "Asia/Ho_Chi_Minh" : task.getTimezone();
    }

    private String defaultMisfire(ScheduledTask task) {
        return task.getMisfirePolicy() == null || task.getMisfirePolicy().isBlank() ? "FIRE_NOW" : task.getMisfirePolicy();
    }

    private static Timestamp toTs(LocalDateTime dt) {
        return dt != null ? Timestamp.valueOf(dt) : null;
    }

    private static LocalDateTime fromTs(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    /** Constant kept explicit so imports stay readable. */
    private static final class StatementKeys {
        static final int RETURN_GENERATED_KEYS = java.sql.Statement.RETURN_GENERATED_KEYS;
    }

    /** Unchecked wrapper so repository signatures stay clean. */
    public static class RepositoryException extends RuntimeException {
        public RepositoryException(String message, Throwable cause) { super(message, cause); }
    }
}
