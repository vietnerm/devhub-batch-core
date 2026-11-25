package com.devhub.batch.api.config;

import com.devhub.batch.core.config.BatchConfig;
import com.devhub.batch.core.migrate.DatabaseMigrator;
import com.devhub.batch.core.repository.JdbcTaskRepository;
import com.devhub.batch.core.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Bridges the Spring world with the pure-JDBC core:
 *
 *  - one shared Hikari {@link DataSource} (Spring-managed) is used by BOTH
 *    the API read model (JdbcTemplate) and the core engine (JdbcTaskRepository);
 *  - a core {@link BatchConfig} is built from batch.* properties, while env
 *    vars (DB_URL, BATCH_*) still override everything (BatchConfig checks env first);
 *  - {@link SchedulerLifecycle} boots the real scheduler inside the API JVM
 *    (disable with BATCH_SCHEDULER_ENABLED=false for api-only nodes).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ApiProperties.class)
public class BatchBeansConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchBeansConfig.class);

    @Bean
    public BatchConfig batchConfig(Environment env) {
        Properties p = new Properties();
        put(p, env, "db.url", "spring.datasource.url", null);
        put(p, env, "db.username", "spring.datasource.username", null);
        put(p, env, "db.password", "spring.datasource.password", null);
        put(p, env, "batch.poll-interval-ms", "batch.poll-interval-ms", null);
        put(p, env, "batch.heartbeat-interval-ms", "batch.heartbeat-interval-ms", null);
        put(p, env, "batch.reconcile-interval-ms", "batch.reconcile-interval-ms", null);
        put(p, env, "batch.stale-worker-threshold-ms", "batch.stale-worker-threshold-ms", null);
        put(p, env, "batch.misfire-grace-ms", "batch.misfire-grace-ms", null);
        put(p, env, "batch.log-retention-days", "batch.log-retention-days", null);
        put(p, env, "batch.cleanup-on-startup", "batch.cleanup-on-startup", null);
        put(p, env, "batch.thread-pool-size", "batch.thread-pool-size", null);
        put(p, env, "batch.leader-lock-name", "batch.leader-lock-name", null);
        put(p, env, "batch.migrate-on-startup", "batch.migrate-on-startup", null);
        put(p, env, "batch.scheduler.enabled", "batch.scheduler-enabled", null);
        put(p, env, "worker.id", "batch.worker-id", null);
        put(p, env, "worker.owner-id", "batch.worker-owner-id", null);
        put(p, env, "worker.owner-name", "batch.worker-owner-name", null);
        put(p, env, "worker.owner-email", "batch.worker-owner-email", null);
        put(p, env, "worker.environment", "batch.worker-environment", null);
        put(p, env, "worker.location", "batch.worker-location", null);
        put(p, env, "worker.app-version", "batch.worker-app-version", null);
        return new BatchConfig(p, System.getenv());
    }

    @Bean
    public TaskRepository taskRepository(DataSource dataSource) {
        return new JdbcTaskRepository(dataSource);
    }

    @Bean
    public InitializingBean databaseMigrationInitializer(DataSource dataSource, BatchConfig batchConfig) {
        return () -> {
            if (batchConfig.isMigrateOnStartup()) {
                new DatabaseMigrator(dataSource).migrate();
            }
        };
    }

    @Bean
    public SchedulerLifecycle schedulerLifecycle(DataSource dataSource, BatchConfig batchConfig) {
        return new SchedulerLifecycle(dataSource, batchConfig);
    }

    private static void put(Properties p, Environment env, String key, String springKey, String def) {
        String value = env.getProperty(springKey, def);
        if (value != null) p.setProperty(key, value);
    }
}
