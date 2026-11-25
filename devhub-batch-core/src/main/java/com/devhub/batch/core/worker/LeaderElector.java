package com.devhub.batch.core.worker;

/** Distributed leader election used by the reconcile loop. */
public interface LeaderElector extends AutoCloseable {

    /** Try to become (or stay) the leader. Non-blocking. */
    boolean tryAcquire();

    /** Release leadership (idempotent, safe on shutdown). */
    void release();

    @Override
    default void close() { release(); }
}
