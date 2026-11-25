package com.devhub.batch.api.demo;

import com.devhub.batch.core.Runner;

/** Demo batch: infrastructure health check. */
public class DemoHealthCheckJob extends Runner {

    @Override
    public void run() {
        log("PING mysql-primary ... 12ms OK");
        log("PING redis-cache ... 2ms OK");
        log("PING s3-archive ... 210ms OK");
    }
}
