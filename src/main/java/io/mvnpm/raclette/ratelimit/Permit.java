package io.mvnpm.raclette.ratelimit;

/**
 * A rate-limit permit that must be held during the HTTP request
 * and released when the request completes.
 * Translated from lychee's SemaphorePermit (held across request execution).
 */
public interface Permit extends AutoCloseable {

    /**
     * Release this permit, allowing another concurrent request to the same host.
     */
    void release();

    @Override
    default void close() {
        release();
    }
}
