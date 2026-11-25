package com.devhub.batch.api.demo;

import com.devhub.batch.core.Runner;

import java.util.List;

/** Demo batch: deterministic happy-path job. */
public class DemoHelloJob extends Runner {

    @Override
    public void run() {
        log("Loading demo configuration ... ok");
        log("Processing 42 hello records ... ok");
        log("Result summary: 42 processed, 0 skipped, 0 failed");
    }
}
