package com.devhub.batch.api.demo;

import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo profile seeder (H2): 13 tasks across every status vocabulary, ~600
 * execution log rows over the last 24h and 3 fake worker rows (2 stale,
 * 1 offline). The API's own scheduler worker registers itself via the real
 * heartbeat loop, so the dashboard shows live traffic within seconds.
 */
@Profile("demo")
@Component
@Order(1)
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final TaskRepository repo;
    private final JdbcTemplate jdbc;

    public DemoSeeder(TaskRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long taskCount = jdbc.queryForObject("SELECT COUNT(*) FROM SYS_SCHEDULED_TASKS", Long.class);
        if (taskCount != null && taskCount > 0) {
            log.info("event=demo.seed_skipped tasks={}", taskCount);
            return;
        }
        List<ScheduledTask> seeded = seedTasks();
        seedExecutionLogs(seeded);
        seedFakeWorkers();
        log.info("event=demo.seeded tasks={} logs=600 workers=3", seeded.size());
    }

    private List<ScheduledTask> seedTasks() {
        List<ScheduledTask> tasks = new ArrayList<>();
        tasks.add(recurring("DEMO_HELLO_BATCH", "DMO01", "Hello Batch (10s smoke)", DemoHelloJob.class,
                "*/10 * * * * *", "FIRE_NOW", 0, "Chạy smoke mỗi 10 giây cho demo dashboard", false, "SCHEDULED"));
        tasks.add(recurring("DEMO_SYNC_ORDERS", "DMO02", "Order Sync (30s)", DemoSyncOrdersJob.class,
                "0/30 * * * * *", "FIRE_NOW", 0, "Đồng bộ đơn hàng staging mỗi 30 giây", false, "SCHEDULED"));
        tasks.add(recurring("DEMO_HEALTH_CHECK", "DMO03", "Infra Health Check (1m)", DemoHealthCheckJob.class,
                "0 * * * * *", "FIRE_NOW", 3, "Ping mysql-primary, redis-cache, s3-archive mỗi phút", false, "SCHEDULED"));
        tasks.add(recurring("DEMO_FLAKE_PAYMENT", "DMO04", "Payment Reconciliation (flaky)", DemoFlakeJob.class,
                "0/20 * * * * *", "FIRE_NOW", 5, "Job có ~35% lỗi để sinh dữ liệu FAILED + requeue", true, "SCHEDULED"));
        tasks.add(recurring("DEMO_NIGHTLY_REPORT", "DMO05", "Nightly Report 03:00", DemoSyncOrdersJob.class,
                "0 0 3 * * *", "FIRE_NOW", 5, "Báo cáo tổng hợp ban đêm — cron 6 fields, TZ Asia/Ho_Chi_Minh", false, "SCHEDULED"));
        tasks.add(recurring("DEMO_WEEKLY_CLEANUP", "DMO06", "Weekly Cleanup Sunday 06:00", DemoHealthCheckJob.class,
                "0 0 6 * * SUN", "SKIP_TO_NEXT", 2, "Dọn dẹp tuần tự — misfire policy SKIP_TO_NEXT (fix Y3)", false, "SCHEDULED"));
        tasks.add(recurring("DEMO_SKIP_POLICY", "DMO07", "Misfire SKIP_TO_NEXT demo (15s)", DemoHelloJob.class,
                "0/15 * * * * *", "SKIP_TO_NEXT", 0, "Task demo chính sách SKIP_TO_NEXT", false, "SCHEDULED"));

        tasks.add(recurring("DEMO_LEGACY_IMPORT", "DMO08", "Legacy Import (paused)", DemoSyncOrdersJob.class,
                "0 30 1 * * *", "FIRE_NOW", 1, "Task cũ tạm dừng — resume để chạy lại", false, "PAUSED"));
        tasks.add(recurring("DEMO_MIGRATE_ARCHIVE", "DMO09", "Archive Migration (paused)", DemoHealthCheckJob.class,
                "0 0 2 * * *", "FIRE_NOW", 1, "Migration lưu trữ — đang pause chờ cửa sổ bảo trì", false, "PAUSED"));

        ScheduledTask onceMigration = once("DEMO_ONCE_MIGRATION", "DMO10", "One-time Migration 08:00",
                DemoHelloJob.class, LocalDateTime.now().plusHours(8),
                "Chạy một lần lúc 08:00 sáng nay (ONCE)");
        tasks.add(onceMigration);

        ScheduledTask backfill = once("DEMO_ONCE_BACKFILL", "DMO11", "One-time Backfill (done)",
                DemoHelloJob.class, LocalDateTime.now().minusMinutes(1),
                "Backfill một lần — scheduler sẽ chạy rồi COMPLETED");
        tasks.add(backfill);

        ScheduledTask badBean = recurring("DEMO_BAD_BEAN", "DMO12", "Broken Bean (ERROR state)", DemoHelloJob.class,
                "0 0 4 * * *", "FIRE_NOW", 0, "Task hỏng BEAN_CLASS — hiển thị trạng thái ERROR", false, "SCHEDULED");
        tasks.add(badBean);

        for (ScheduledTask task : tasks) {
            repo.save(task);
        }
        // Mark the broken-bean task ERROR (dispatch never touches non-SCHEDULED rows).
        repo.updateStatus(badBean.getId(), "ERROR");
        // One task exhausted its retry budget -> DEAD (fix Y2 dead-letter demo).
        ScheduledTask dead = recurring("DEMO_DEAD_LETTER", "DMO13", "Dead-letter Demo (exhausted retries)",
                DemoSyncOrdersJob.class, "0 0 5 * * *", "FIRE_NOW", 1,
                "Task hết số lần retry — trạng thái DEAD (retry policy mới)", true, "SCHEDULED");
        repo.save(dead);
        repo.updateStatus(dead.getId(), "DEAD");
        tasks.add(dead);
        return tasks;
    }

    private ScheduledTask recurring(String taskName, String functionCode, String functionName,
                                    Class<?> beanClass, String cron, String misfire, int priority,
                                    String description, boolean requeue, String status) {
        ScheduledTask task = new ScheduledTask();
        task.setTaskName(taskName);
        task.setFunctionCode(functionCode);
        task.setFunctionName(functionName);
        task.setBeanClass(beanClass.getName());
        task.setScheduleType("RECURRING");
        task.setCronExpression(cron);
        task.setTimezone("Asia/Ho_Chi_Minh");
        task.setMisfirePolicy(misfire);
        task.setPriority(priority);
        task.setDescription(description);
        task.setRequeueOnFailure(requeue);
        task.setTimeoutSeconds(60);
        task.setStatus(status);
        return task;
    }

    private ScheduledTask once(String taskName, String functionCode, String functionName,
                               Class<?> beanClass, LocalDateTime scheduledTime, String description) {
        ScheduledTask task = new ScheduledTask();
        task.setTaskName(taskName);
        task.setFunctionCode(functionCode);
        task.setFunctionName(functionName);
        task.setBeanClass(beanClass.getName());
        task.setScheduleType("ONCE");
        task.setScheduledTime(scheduledTime);
        task.setTimezone("Asia/Ho_Chi_Minh");
        task.setMisfirePolicy("FIRE_NOW");
        task.setPriority(0);
        task.setDescription(description);
        task.setTimeoutSeconds(60);
        task.setStatus("SCHEDULED");
        return task;
    }

    private void seedExecutionLogs(List<ScheduledTask> tasks) {
        String sql = "INSERT INTO SYS_TASK_EXECUTION_LOGS "
                + "(TASK_ID, WORKER_ID, STATUS, RESULT, ERROR_MESSAGE, EXECUTED_AT, STARTED_AT, FINISHED_AT, DURATION_MS) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String[] workers = {"demo-worker-01", "demo-worker-02"};
        List<Object[]> rows = new ArrayList<>();

        List<ScheduledTask> live = tasks.stream()
                .filter(t -> List.of("DEMO_HELLO_BATCH", "DEMO_SYNC_ORDERS", "DEMO_HEALTH_CHECK", "DEMO_FLAKE_PAYMENT")
                        .contains(t.getTaskName()))
                .toList();

        for (ScheduledTask task : live) {
            for (int i = 0; i < 150; i++) {
                LocalDateTime executedAt = LocalDateTime.now().minusMinutes(rnd.nextLong(1, 1440));
                String worker = workers[rnd.nextInt(workers.length)];
                String status;
                String error = null;
                long duration;
                int roll = rnd.nextInt(100);
                boolean flaky = "DEMO_FLAKE_PAYMENT".equals(task.getTaskName());
                if (flaky && roll < 30) {
                    status = "FAILED";
                    error = "Simulated upstream timeout: payment-gateway did not respond within 2000ms";
                    duration = rnd.nextLong(150, 2000);
                } else if (!flaky && roll >= 96) {
                    status = "TIMEOUT";
                    error = "Claim older than TIMEOUT_SECONDS (60s) — requeued by leader (fix Y1)";
                    duration = rnd.nextLong(60000, 63000);
                } else if (flaky && roll >= 30 && roll < 33) {
                    status = "TIMEOUT";
                    error = "Upstream slow — claim timeout";
                    duration = rnd.nextLong(60000, 65000);
                } else {
                    status = "SUCCESS";
                    duration = rnd.nextLong(200, 3000);
                }
                String result = "JobName: [" + task.getTaskName() + "] is Run\n"
                        + "Simulated execution #" + i + " on " + worker + "\n"
                        + "JobName: [" + task.getTaskName() + "] is "
                        + ("SUCCESS".equals(status) ? "Done" : "ERROR: " + (error == null ? "unknown" : error));
                rows.add(new Object[]{
                        task.getId(), worker, status, result, error,
                        Timestamp.valueOf(executedAt),
                        Timestamp.valueOf(executedAt.minusNanos(duration * 1_000_000L)),
                        Timestamp.valueOf(executedAt),
                        duration});
            }
        }
        jdbc.batchUpdate(sql, rows);
    }

    private void seedFakeWorkers() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO SYS_SCHEDULER_WORKERS
                (WORKER_ID, OWNER_ID, OWNER_NAME, OWNER_EMAIL, MACHINE_NAME, HOSTNAME, PRIVATE_IP, PUBLIC_IP,
                 OS_NAME, OS_ARCH, JAVA_VERSION, APP_VERSION, ENVIRONMENT, LOCATION, STATUS,
                 CURRENT_TASK_ID, CURRENT_TASK_NAME, STARTED_AT, LAST_HEARTBEAT_AT, LAST_ERROR)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                "demo-worker-01", "devhub", "DevHub Batch", "devops@devhub.local",
                "vn-hcm-batch-01", "vn-hcm-batch-01", "10.20.1.15", "unknown",
                "Linux", "amd64", "21.0.13", "devhub-batch-core-2.0.0", "production", "HCM DC-1",
                "STALE", null, "DEMO_LEGACY_IMPORT",
                Timestamp.valueOf(now.minusHours(5)), Timestamp.valueOf(now.minusHours(3)),
                "Worker lost connection mid-run — kept for forensic (claim released by Y1 recovery)");

        jdbc.update("""
                INSERT INTO SYS_SCHEDULER_WORKERS
                (WORKER_ID, OWNER_ID, OWNER_NAME, OWNER_EMAIL, MACHINE_NAME, HOSTNAME, PRIVATE_IP, PUBLIC_IP,
                 OS_NAME, OS_ARCH, JAVA_VERSION, APP_VERSION, ENVIRONMENT, LOCATION, STATUS,
                 CURRENT_TASK_ID, CURRENT_TASK_NAME, STARTED_AT, LAST_HEARTBEAT_AT, LAST_ERROR)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                "demo-worker-02", "devhub", "DevHub Batch", "devops@devhub.local",
                "vn-hcm-batch-02", "vn-hcm-batch-02", "10.20.1.16", "unknown",
                "Linux", "amd64", "21.0.13", "devhub-batch-core-2.0.0", "production", "HCM DC-1",
                "STALE", null, null,
                Timestamp.valueOf(now.minusHours(4)), Timestamp.valueOf(now.minusHours(2)), null);

        jdbc.update("""
                INSERT INTO SYS_SCHEDULER_WORKERS
                (WORKER_ID, OWNER_ID, OWNER_NAME, OWNER_EMAIL, MACHINE_NAME, HOSTNAME, PRIVATE_IP, PUBLIC_IP,
                 OS_NAME, OS_ARCH, JAVA_VERSION, APP_VERSION, ENVIRONMENT, LOCATION, STATUS,
                 CURRENT_TASK_ID, CURRENT_TASK_NAME, STARTED_AT, LAST_HEARTBEAT_AT, LAST_ERROR)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                "demo-worker-03", "devhub", "DevHub Batch", "devops@devhub.local",
                "vn-hn-batch-03", "vn-hn-batch-03", "10.30.8.21", "unknown",
                "Linux", "amd64", "21.0.13", "devhub-batch-core-1.9.0", "production", "HN DC-2",
                "OFFLINE", null, null,
                Timestamp.valueOf(now.minusHours(50)), Timestamp.valueOf(now.minusHours(26)),
                "Decommissioned node (drained)");
    }
}
