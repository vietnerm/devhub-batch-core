package com.devhub.batch.core.worker;

/**
 * Single-node leader elector: always wins. Used for tests, H2 and
 * single-instance deployments where distributed locking is unnecessary.
 */
public class AlwaysLeaderElector implements LeaderElector {

    @Override
    public boolean tryAcquire() { return true; }

    @Override
    public void release() { /* no-op */ }
}
