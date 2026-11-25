package com.devhub.batch.api.config;

import com.devhub.batch.core.Scheduler;
import com.devhub.batch.core.SchedulerBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import javax.sql.DataSource;

/**
 * Starts/stops the core scheduler engine with the Spring application context.
 * Uses {@link SchedulerBootstrap} singleton semantics (migrate -> elector ->
 * dispatch/heartbeat/leader loops).
 */
public class SchedulerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLifecycle.class);

    private final DataSource dataSource;
    private final com.devhub.batch.core.config.BatchConfig config;
    private volatile boolean running;
    private volatile Scheduler scheduler;

    public SchedulerLifecycle(DataSource dataSource, com.devhub.batch.core.config.BatchConfig config) {
        this.dataSource = dataSource;
        this.config = config;
    }

    @Override
    public void start() {
        boolean enabled = config.getBool("BATCH_SCHEDULER_ENABLED", "batch.scheduler.enabled", "true");
        if (!enabled) {
            log.info("event=scheduler.disabled api-only mode");
            return;
        }
        config.printSummary();
        this.scheduler = SchedulerBootstrap.start(dataSource, config);
        this.running = true;
        log.info("event=scheduler.lifecycle_started worker={}", scheduler.getWorkerId());
    }

    @Override
    public void stop() {
        SchedulerBootstrap.shutdown();
        this.running = false;
        log.info("event=scheduler.lifecycle_stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Start late so demo seeding (ApplicationRunner) is NOT expected first —
        // an empty SYS_SCHEDULED_TASKS is safe for the dispatch loop; tasks created
        // by the seeder right after are picked up within one poll interval.
        return Integer.MAX_VALUE - 100;
    }
}
