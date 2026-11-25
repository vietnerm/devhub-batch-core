package com.devhub.batch.core.cli;

import com.devhub.batch.core.config.BatchConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** HikariCP factory for the standalone CLI and daemon. */
public final class DataSourceFactory {

    private DataSourceFactory() {}

    public static HikariDataSource create(BatchConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.getDbUrl());
        hc.setUsername(config.getDbUsername());
        hc.setPassword(config.getDbPassword());
        hc.setMaximumPoolSize(Math.max(2, config.getDbPoolSize()));
        hc.setMinimumIdle(1);
        hc.setPoolName("devhub-batch-core");
        hc.setAutoCommit(true);
        return new HikariDataSource(hc);
    }
}
