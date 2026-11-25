package com.devhub.batch.api.demo;

import com.devhub.batch.core.Runner;

import java.util.concurrent.ThreadLocalRandom;

/** Demo batch: simulates an order sync with a varying record count. */
public class DemoSyncOrdersJob extends Runner {

    @Override
    public void run() {
        int synced = 100 + ThreadLocalRandom.current().nextInt(400);
        log("Connecting to orders API (sandbox) ... ok");
        log("Synced " + synced + " orders into warehouse staging");
        if (synced > 400) {
            log("Warning: large batch size " + synced + " — monitor warehouse lag");
        }
    }
}
