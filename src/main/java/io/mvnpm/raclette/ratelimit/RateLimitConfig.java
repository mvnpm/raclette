package io.mvnpm.raclette.ratelimit;

import java.time.Duration;

/**
 * Configuration for per-host rate limiting.
 * Translated from lychee's ratelimit module.
 */
public record RateLimitConfig(int maxConcurrentPerHost, Duration requestInterval) {

    public RateLimitConfig {
        if (maxConcurrentPerHost <= 0) {
            throw new IllegalArgumentException("maxConcurrentPerHost must be positive");
        }
        if (requestInterval.isNegative()) {
            throw new IllegalArgumentException("requestInterval must not be negative");
        }
    }

    /**
     * Default configuration: 10 concurrent per host, 50ms interval.
     */
    public static RateLimitConfig defaults() {
        return new RateLimitConfig(10, Duration.ofMillis(50));
    }
}
