package com.devhub.batch.api.ws;

import com.devhub.batch.api.dao.LogQueryDao;
import com.devhub.batch.api.dto.MonitorDtos.LogDto;
import com.devhub.batch.api.dto.MonitorDtos.WorkerDto;
import com.devhub.batch.api.dao.WorkerQueryDao;
import com.devhub.batch.api.dto.TaskDtos.TaskDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Database → WebSocket bridge (no hooks inside the core engine):
 *  - new rows in SYS_TASK_EXECUTION_LOGS are tailed by monotonic ID;
 *  - SYS_SCHEDULED_TASKS / SYS_SCHEDULER_WORKERS are diffed by fingerprint
 *    (tiny tables; at production scale add a version column or triggers).
 * Everything works on both H2 (demo) and MySQL 8 (production).
 */
@Component
@DependsOn("databaseMigrationInitializer")
public class BatchEventPoller {

    private static final Logger log = LoggerFactory.getLogger(BatchEventPoller.class);

    private final JdbcTemplate jdbc;
    private final LogQueryDao logQueryDao;
    private final WorkerQueryDao workerQueryDao;
    private final BatchEventSocketHandler handler;

    private long lastLogId;
    private final Map<Long, String> taskFingerprints = new HashMap<>();
    private String workerSnapshot = "";

    public BatchEventPoller(JdbcTemplate jdbc,
                            LogQueryDao logQueryDao,
                            WorkerQueryDao workerQueryDao,
                            BatchEventSocketHandler handler) {
        this.jdbc = jdbc;
        this.logQueryDao = logQueryDao;
        this.workerQueryDao = workerQueryDao;
        this.handler = handler;
    }

    @PostConstruct
    void init() {
        this.lastLogId = logQueryDao.maxId();
        log.info("event=poller.started lastLogId={}", lastLogId);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void poll() {
        try {
            tailLogs();
            diffTasks();
            diffWorkers();
        } catch (Exception e) {
            log.warn("event=poller.failed error={}", e.getMessage());
        }
    }

    private void tailLogs() {
        List<LogDto> newLogs = logQueryDao.tailAfter(lastLogId, 100);
        for (LogDto logDto : newLogs) {
            lastLogId = Math.max(lastLogId, logDto.id());
            handler.broadcast("log", logDto);
        }
    }

    private void diffTasks() {
        List<TaskDto> tasks = jdbc.query(
                "SELECT * FROM SYS_SCHEDULED_TASKS", (rs, i) -> TaskDto.fromRow(rs));
        Map<Long, String> current = new HashMap<>();
        for (TaskDto task : tasks) {
            String fingerprint = String.join("|",
                    String.valueOf(task.status()),
                    String.valueOf(task.nextRunAt()),
                    String.valueOf(task.lastRunAt()),
                    String.valueOf(task.workerId()),
                    String.valueOf(task.claimedAt()),
                    String.valueOf(task.updatedAt()));
            current.put(task.id(), fingerprint);
            String previous = taskFingerprints.get(task.id());
            if (previous == null) continue; // skip first snapshot burst
            if (!previous.equals(fingerprint)) {
                handler.broadcast("task", task);
            }
        }
        for (Long removedId : taskFingerprints.keySet()) {
            if (!current.containsKey(removedId)) {
                handler.broadcast("task_removed", Map.of("id", removedId));
            }
        }
        taskFingerprints.clear();
        taskFingerprints.putAll(current);
    }

    private void diffWorkers() {
        List<WorkerDto> workers = workerQueryDao.listAll();
        StringBuilder snapshot = new StringBuilder();
        for (WorkerDto worker : workers) {
            snapshot.append(worker.workerId()).append(':')
                    .append(worker.status()).append(':')
                    .append(worker.lastHeartbeatAt()).append(':')
                    .append(worker.currentTaskName()).append('\n');
        }
        String value = snapshot.toString();
        if (!value.equals(workerSnapshot)) {
            workerSnapshot = value;
            handler.broadcast("workers", workers);
        }
    }
}
