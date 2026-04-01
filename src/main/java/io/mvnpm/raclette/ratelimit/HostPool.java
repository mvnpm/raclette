package io.mvnpm.raclette.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages per-host rate limiting.
 * Creates host entries lazily on demand, each with:
 * - A concurrency semaphore (maxConcurrentPerHost permits, held during requests)
 * - A rate-limiter semaphore (1 permit, refilled by timer after each use)
 *
 * No Thread.sleep() or synchronized blocks — all waits are on Semaphore.acquire(),
 * which is virtual-thread-friendly (no carrier thread pinning).
 *
 * Translated from lychee's ratelimit module (governor-based token bucket + semaphore).
 */
public class HostPool implements AutoCloseable {

    private final RateLimitConfig config;
    private final ConcurrentHashMap<String, HostState> hosts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public HostPool(RateLimitConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "raclette-rate-limiter");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Acquire a permit for the given host, blocking until both the rate limit
     * and concurrency limit allow.
     *
     * The returned Permit must be released when the request completes (try-with-resources).
     * Matches lychee's Host::execute_request flow:
     * 1. semaphore.acquire().await — acquire concurrency slot
     * 2. rate_limiter.until_ready().await — wait for interval
     * 3. execute request (caller holds permit)
     * 4. permit dropped (semaphore released)
     */
    public Permit acquire(String host) {
        HostState state = hosts.computeIfAbsent(host, k -> new HostState(config));

        // 1. Wait for concurrency slot (matches lychee: concurrency first, then rate limit)
        try {
            state.concurrency.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Rate limit acquire interrupted", e);
        }

        // 2. Wait for rate limit (interval between requests)
        try {
            state.rateLimiter.acquire();
        } catch (InterruptedException e) {
            state.concurrency.release();
            Thread.currentThread().interrupt();
            throw new RuntimeException("Rate limit acquire interrupted", e);
        }

        // Schedule the rate limiter permit refill after the configured interval
        long intervalMs = config.requestInterval().toMillis();
        scheduler.schedule(() -> state.rateLimiter.release(), intervalMs, TimeUnit.MILLISECONDS);

        // 3. Return idempotent permit — caller holds it during the request
        AtomicBoolean released = new AtomicBoolean(false);
        return () -> {
            if (released.compareAndSet(false, true)) {
                state.concurrency.release();
            }
        };
    }

    /**
     * Return the number of tracked hosts.
     */
    public int hostCount() {
        return hosts.size();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    /**
     * Per-host state: rate limiter (1 permit, timer-refilled) + concurrency semaphore.
     */
    private static class HostState {

        /** Controls minimum interval between requests — 1 permit, refilled by timer. */
        final Semaphore rateLimiter;

        /** Controls max concurrent in-flight requests. */
        final Semaphore concurrency;

        HostState(RateLimitConfig config) {
            this.rateLimiter = new Semaphore(1);
            this.concurrency = new Semaphore(config.maxConcurrentPerHost());
        }
    }
}
