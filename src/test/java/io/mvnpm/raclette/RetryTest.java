package io.mvnpm.raclette;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.mvnpm.raclette.types.RetryExt;

/**
 * Tests translated from lychee's retry.rs
 */
class RetryTest {

    @Test
    void testShouldRetry() {
        // 408 Request Timeout
        assertThat(RetryExt.shouldRetry(408)).isTrue();
        // 429 Too Many Requests
        assertThat(RetryExt.shouldRetry(429)).isTrue();
        // 403 Forbidden — should NOT retry
        assertThat(RetryExt.shouldRetry(403)).isFalse();
        // 500 Internal Server Error
        assertThat(RetryExt.shouldRetry(500)).isTrue();
    }
}
