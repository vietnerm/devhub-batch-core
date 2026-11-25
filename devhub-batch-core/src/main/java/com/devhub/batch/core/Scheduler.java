package com.devhub.batch.core;

import com.devhub.batch.core.config.BatchConfig;
import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.model.WorkerInfo;
import com.devhub.batch.core.repository.TaskRepository;
import com.devhub.batch.core.support.Times;
import com.devhub.batch.core.worker.LeaderElector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Poll/claim scheduler engine — the database is the single source of truth.
 *
 * Threads:
 *  - dispatch loop (every poll-interval): finds due tasks and submits claims.
 *  - heartbeat loop (every heartbeat-interval): worker registry upsert.
 *  - leader loop (every reconcile-interval): only one node (GET_LOCK) performs
 *    recovery (Y1-safe), misfire advance (Y3) and log retention purge.
 *
 * Running tasks are tracked per task id in a concurrent map (fixes the old
 * single currentTaskId field that was only correct with a pool size of 1),
 * and an in-flight set keeps the dispatch loop from queueing the same due
 * task on consecutive ticks before a worker claims it.
 *
 * There are no in-memory per-task futures (the old drift-check loop, Y5, is
 * gone): NEXT_RUN_AT lives in SYS_SCHEDULED_TASKS and every worker polls it.
 * All clock reads bound for the database are UTC (Times.nowUtc()).
 */
public final class Scheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private final BatchConfig config;
    private final TaskRepository repo;
    private final WorkerInfo worker;
    private final LeaderElector leader;
    private final ExecutorService pool;
    private final ScheduledExecutorService loops;
    private final String workerId;

    private final ConcurrentMap<Long, String> runningTasks = new ConcurrentHashMap<>();
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final RetryPolicy retryPolicy;
    private volatile boolean started;
    private LocalDateTime lastPurgeAt = LocalDateTime.MIN;

    public Scheduler(BatchConfig config, TaskRepository repo, WorkerInfo worker, LeaderElector leader) {
        this.config = config;
        this.repo = repo;
        this.worker = worker;
        this.leader = leader;
        this.workerId = worker.getWorkerId();
        this.retryPolicy = new RetryPolicy(config.getMaxAttempt(), config.getBackoffBaseSeconds());
        this.pool = Executors.newFixedThreadPool(config.getThreadPoolSize(), r -> {
            Thread t = new Thread(r, "batch-worker-" + workerId);
            t.setDaemon(false);
            return t;
        });
        this.loops = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "batch-loops-" + workerId);
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        repo.heartbeat(worker, null, null, "ONLINE", null);

        // Y1-safe startup recovery: only claims that are genuinely stale
        // (CLAIMED_AT older than the task TIMEOUT_SECONDS) are rewritten.
        int recovered = TaskRecovery.recoverTimedOutTasks(repo, workerId, Times.nowUtc());
        if (recovered > 0) log.info("event=startup.recovered staleRuns={}", recovered);

        long heartbeat = config.getHeartbeatIntervalMs();
        long poll = config.getPollIntervalMs();
        long reconcile = config.getReconcileIntervalMs();
        loops.scheduleWithFixedDelay(this::heartbeatTick, heartbeat, heartbeat, TimeUnit.MILLISECONDS);
        loops.scheduleWithFixedDelay(this::dispatchTick, poll, poll, TimeUnit.MILLISECONDS);
        loops.scheduleWithFixedDelay(this::leaderTick, reconcile, reconcile, TimeUnit.MILLISECONDS);
        log.info("event=scheduler.started worker={} pool={} pollMs={} reconcileMs={}",
                workerId, config.getThreadPoolSize(), poll, reconcile);
    }

    private void heartbeatTick() {
        try {
            if (runningTasks.isEmpty()) {
                repo.heartbeat(worker, null, null, "ONLINE", null);
                return;
            }
            // Schema keeps a single CURRENT_TASK_ID/CURRENT_TASK_NAME pair:
            // report the lowest running task id and fold the rest into the name.
            Long firstId = runningTasks.keySet().stream().min(Long::compare).orElse(null);
            String firstName = firstId != null ? runningTasks.get(firstId) : null;
            String name = firstName;
            if (name != null && runningTasks.size() > 1) {
                name = name + " (+" + (runningTasks.size() - 1) + " more)";
            }
            if (name != null && name.length() > 100) name = name.substring(0, 100);
            repo.heartbeat(worker, firstId, name, "ONLINE", null);
        } catch (Exception e) {
            log.warn("event=heartbeat.failed error={}", e.getMessage());
        }
    }

    private void dispatchTick() {
        try {
            int active = pool instanceof ThreadPoolExecutor tpe ? tpe.getActiveCount() : 0;
            int free = config.getThreadPoolSize() - active;
            if (free <= 0) return;
            List<ScheduledTask> due = repo.findDueTasks(Times.nowUtc(), free);
            for (ScheduledTask task : due) {
                // Skip tasks already dispatched but not yet claimed; the atomic
                // claim in TaskJob remains the real guard against double runs.
                if (!inFlight.add(task.getId())) continue;
                pool.submit(new TaskJob(repo, task.getId(), workerId, this::setCurrentTask, this::clearCurrentTask, retryPolicy));
            }
        } catch (Exception e) {
            log.warn("event=dispatch.failed error={}", e.getMessage());
        }
    }

    private void leaderTick() {
        try {
            if (!leader.tryAcquire()) return;
            LocalDateTime now = Times.nowUtc();
            repo.markStaleWorkers(now.minus(config.getStaleWorkerThresholdMs(), ChronoUnit.MILLIS));
            TaskRecovery.recoverTimedOutTasks(repo, workerId, now);
            TaskRecovery.applyMisfire(repo, now, config.getMisfireGraceMs());
            purgeOldLogs(now);
        } catch (Exception e) {
            log.warn("event=leader.tick_failed error={}", e.getMessage());
        }
    }

    /** batch.cleanup-on-startup (previously dead config) drives retention purge. */
    private void purgeOldLogs(LocalDateTime now) {
        int retentionDays = config.getLogRetentionDays();
        if (!config.isCleanupOnStartup() || retentionDays <= 0) return;
        if (Duration.between(lastPurgeAt, now).toHours() < 1) return;
        lastPurgeAt = now;
        repo.purgeLogsBefore(now.minusDays(retentionDays));
        log.debug("event=logs.purged retentionDays={}", retentionDays);
    }

    private void setCurrentTask(Long taskId, String taskName) {
        runningTasks.put(taskId, taskName);
    }

    private void clearCurrentTask(Long taskId) {
        runningTasks.remove(taskId);
        inFlight.remove(taskId);
    }

    public String getWorkerId() { return workerId; }
    public WorkerInfo getWorkerInfo() { return worker; }
    public boolean isStarted() { return started; }

    @Override
    public synchronized void close() {
        if (!started) return;
        started = false;
        loops.shutdownNow();
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        repo.markWorkerOffline(workerId);
        leader.release();
        log.info("event=scheduler.stopped worker={}", workerId);
    }
}
