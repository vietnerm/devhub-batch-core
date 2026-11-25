package com.devhub.batch.core.support;

import com.devhub.batch.core.Runner;

/** Trivial batch used by tests and integration tests. */
public class NoopRunner extends Runner {

    @Override
    public void run() {
        log("noop ok");
    }
}
