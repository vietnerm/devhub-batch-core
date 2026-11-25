package com.devhub.batch.core.it;

import com.devhub.batch.core.TaskJob;
import com.devhub.batch.core.migrate.DatabaseMigrator;
import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.model.TaskExecutionLog;
import com.devhub.batch.core.repository.JdbcTaskRepository;
import com.devhub.batch.core.repository.TaskRepository;
import com.devhub.batch.core.schedule.ScheduleValidator;
import com.devhub.batch.core.support.NoopRunner;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test against a real MySQL 8: runs the production migration
 * scripts, then executes a task end-to-end through TaskJob.
 *
 * Skipped automatically when no Docker daemon is available (e.g. local dev);
 * the GitHub Actions ubuntu runners always provide Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationsIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    private static HikariDataSource dataSource() {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(MYSQL.getJdbcUrl());
        hc.setUsername(MYSQL.getUsername());
        hc.setPassword(MYSQL.getPassword());
        hc.setMaximumPoolSize(4);
        return new HikariDataSource(hc);
    }

    @Test
    void migrationsAreIdempotentAndSchedulerExecutesEndToEnd() {
        try (HikariDataSource ds = dataSource()) {
            DatabaseMigrator migrator = new DatabaseMigrator(ds);

            List<String> firstRun = migrator.migrate();
            assertTrue(firstRun.contains("V20260815__batch_core_schema.sql"));

            // Second run must apply nothing (idempotent).
            List<String> secondRun = migrator.migrate();
            assertEquals(0, secondRun.size(), "migrations must not reapply");

            TaskRepository repo = new JdbcTaskRepository(ds);

            ScheduledTask task = new ScheduledTask();
            task.setTaskName("it_noop_once");
            task.setBeanClass(NoopRunner.class.getName());
            task.setScheduleType(ScheduleValidator.ONCE);
            task.setScheduledTime(LocalDateTime.now().plusSeconds(60));
            task.setStatus("SCHEDULED");
            task.setDescription("IT end-to-end");
            task = repo.save(task);
            assertNotNull(task.getId());
            assertNotNull(task.getNextRunAt());

            // Execute synchronously through the real engine path.
            new TaskJob(repo, task.getId(), "it-worker", null, null).run();

            assertEquals("COMPLETED",
                    repo.findById(task.getId()).getStatus(),
                    "ONCE task must end COMPLETED after a successful run");
            List<TaskExecutionLog> logs = repo.findLogsByTaskId(task.getId(), 1);
            assertEquals(1, logs.size());
            assertEquals("SUCCESS", logs.get(0).getStatus());
            assertEquals("it-worker", logs.get(0).getWorkerId());
        }
    }

    @Test
    void claimIsAtomicOnRealMySQL() throws Exception {
        try (HikariDataSource ds = dataSource()) {
            new DatabaseMigrator(ds).migrate();
            TaskRepository repo = new JdbcTaskRepository(ds);

            ScheduledTask task = new ScheduledTask();
            task.setTaskName("it_claim_race");
            task.setBeanClass(NoopRunner.class.getName());
            task.setScheduleType(ScheduleValidator.RECURRING);
            task.setCronExpression("0 */2 * * * *");
            task.setStatus("SCHEDULED");
            task.setDescription("IT claim race");
            task = repo.save(task);

            long claimedTaskId = task.getId();

            int threads = 4;
            java.util.concurrent.atomic.AtomicInteger winners = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(threads);
            try (java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads)) {
                for (int i = 0; i < threads; i++) {
                    final String worker = "it-w" + i;
                    pool.submit(() -> {
                        ready.countDown();
                        try { ready.await(); } catch (InterruptedException ignored) { }
                        if (repo.claimTask(claimedTaskId, worker, LocalDateTime.now().plusSeconds(3600))) {
                            winners.incrementAndGet();
                        }
                    });
                }
                pool.shutdown();
                assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(1, winners.get(), "exactly one concurrent claimer must win on MySQL");
        }
    }
}
