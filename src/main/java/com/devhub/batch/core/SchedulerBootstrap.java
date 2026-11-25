package com.devhub.batch.core;

import com.devhub.batch.core.config.BatchConfig;
import com.devhub.batch.core.migrate.DatabaseMigrator;
import com.devhub.batch.core.model.WorkerInfo;
import com.devhub.batch.core.repository.JdbcTaskRepository;
import com.devhub.batch.core.repository.TaskRepository;
import com.devhub.batch.core.worker.AlwaysLeaderElector;
import com.devhub.batch.core.worker.LeaderElector;
import com.devhub.batch.core.worker.MysqlGetLockLeaderElector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Entry point — singleton managing the {@link Scheduler} instance.
 *
 * Usage (pure Java, no Spring):
 *   HikariDataSource ds = DataSourceFactory.create(config);
 *   Scheduler scheduler = SchedulerBootstrap.start(ds, config);
 *   SchedulerBootstrap.shutdown();
 *
 * Leader election: MySQL deployments use GET_LOCK; other URLs (H2, tests,
 * single node) fall back to {@link AlwaysLeaderElector}.
 */
public final class SchedulerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SchedulerBootstrap.class);

    private static volatile Scheduler INSTANCE;
    private static volatile TaskRepository REPOSITORY;
    private static final Object LOCK = new Object();

    private SchedulerBootstrap() {}

    public static Scheduler start(DataSource dataSource, BatchConfig config) {
        return start(dataSource, config, null);
    }

    public static Scheduler start(DataSource dataSource, BatchConfig config, WorkerInfo worker) {
        if (INSTANCE != null) return INSTANCE;
        synchronized (LOCK) {
            if (INSTANCE != null) return INSTANCE;
            if (config.isMigrateOnStartup()) {
                new DatabaseMigrator(dataSource).migrate();
            }
            WorkerInfo info = worker != null ? worker : config.getWorkerInfo();
            TaskRepository repo = new JdbcTaskRepository(dataSource);
            LeaderElector leader = electorFor(dataSource, config);
            Scheduler scheduler = new Scheduler(config, repo, info, leader);
            scheduler.start();
            REPOSITORY = repo;
            INSTANCE = scheduler;
            return scheduler;
        }
    }

    private static LeaderElector electorFor(DataSource dataSource, BatchConfig config) {
        String url = config.getDbUrl();
        if (url != null && url.startsWith("jdbc:mysql")) {
            return new MysqlGetLockLeaderElector(dataSource, config.getLeaderLockName());
        }
        log.info("event=leader.single_node (non-MySQL URL, always-leader mode)");
        return new AlwaysLeaderElector();
    }

    public static Scheduler getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Scheduler not started. Call start(dataSource, config) first.");
        }
        return INSTANCE;
    }

    public static TaskRepository getRepository() {
        if (INSTANCE == null || REPOSITORY == null) {
            throw new IllegalStateException("Scheduler not started.");
        }
        return REPOSITORY;
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (INSTANCE != null) {
                INSTANCE.close();
                INSTANCE = null;
                REPOSITORY = null;
            }
        }
    }
}
