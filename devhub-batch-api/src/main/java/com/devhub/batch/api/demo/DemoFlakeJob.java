package com.devhub.batch.api.demo;

import com.devhub.batch.core.Runner;

import java.util.concurrent.ThreadLocalRandom;

/** Demo batch: ~35% simulated failures so the UI has real FAILED history. */
public class DemoFlakeJob extends Runner {

    @Override
    public void run() throws Exception {
        log("Calling payment-gateway with 2000ms timeout ...");
        if (ThreadLocalRandom.current().nextInt(100) < 35) {
            throw new Exception("Simulated upstream timeout: payment-gateway did not respond within 2000ms");
        }
        log("Payment reconciliation finished OK");
    }
}
