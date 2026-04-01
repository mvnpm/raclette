package io.mvnpm.raclette.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.mvnpm.raclette.types.Uri;

/**
 * Tests translated from lychee's ratelimit module.
 */
class RateLimitTest {

    /**
     * HostKey extraction from URLs.
     * Translated from lychee's test_host_key_from_url.
     */
    @Test
    void testHostKeyFromUrl() {
        assertThat(HostKey.fromUri(Uri.website("https://example.com/path")))
                .isEqualTo(new HostKey("example.com"));
        assertThat(HostKey.fromUri(Uri.website("https://example.com:8080/path")))
                .isEqualTo(new HostKey("example.com"));
    }

    /**
     * HostKey normalized to lowercase.
     * Translated from lychee's test_host_key_normalization.
     */
    @Test
    void testHostKeyNormalization() {
        assertThat(HostKey.fromUri(Uri.website("https://EXAMPLE.COM/path")))
                .isEqualTo(new HostKey("example.com"));
    }

    /**
     * Subdomains are treated as separate hosts.
     * Translated from lychee's test_host_key_subdomain_separation.
     */
    @Test
    void testHostKeySubdomainSeparation() {
        HostKey api = HostKey.fromUri(Uri.website("https://api.github.com/repos"));
        HostKey www = HostKey.fromUri(Uri.website("https://www.github.com/repos"));
        assertThat(api).isNotEqualTo(www);
    }

    /**
     * file:// URIs have no host key.
     * Translated from lychee's test_host_key_no_host.
     */
    @Test
    void testHostKeyNoHost() {
        assertThat(HostKey.fromUri(Uri.tryFrom("file:///tmp/test.html"))).isNull();
    }

    /**
     * Default rate limit config values.
     * Translated from lychee's test_default_rate_limit_config.
     */
    @Test
    void testDefaultRateLimitConfig() {
        RateLimitConfig config = RateLimitConfig.defaults();
        assertThat(config.maxConcurrentPerHost()).isEqualTo(10);
        assertThat(config.requestInterval()).isEqualTo(Duration.ofMillis(50));
    }

    /**
     * Rate limiter enforces minimum interval between requests to same host.
     */
    @Test
    void testRateLimiterEnforcesInterval() throws InterruptedException {
        RateLimitConfig config = new RateLimitConfig(1, Duration.ofMillis(100));
        try (HostPool pool = new HostPool(config)) {
            long start = System.currentTimeMillis();

            // Three requests to same host — should take at least 200ms (2 intervals)
            pool.acquire("example.com").release();
            pool.acquire("example.com").release();
            pool.acquire("example.com").release();

            long elapsed = System.currentTimeMillis() - start;
            assertThat(elapsed).isGreaterThanOrEqualTo(200);
        }
    }

    /**
     * Different hosts are rate-limited independently.
     */
    @Test
    void testDifferentHostsIndependent() throws InterruptedException {
        RateLimitConfig config = new RateLimitConfig(1, Duration.ofMillis(100));
        try (HostPool pool = new HostPool(config)) {
            long start = System.currentTimeMillis();

            // Requests to different hosts should not wait for each other
            pool.acquire("host-a.com").release();
            pool.acquire("host-b.com").release();
            pool.acquire("host-c.com").release();

            long elapsed = System.currentTimeMillis() - start;
            // Should be fast — no inter-host waiting
            assertThat(elapsed).isLessThan(100);
        }
    }

    /**
     * HostPool creates hosts lazily on demand.
     * Translated from lychee's test_host_creation_on_demand.
     */
    @Test
    void testHostCreationOnDemand() {
        try (HostPool pool = new HostPool(RateLimitConfig.defaults())) {
            assertThat(pool.hostCount()).isEqualTo(0);

            pool.acquire("example.com").release();
            assertThat(pool.hostCount()).isEqualTo(1);

            pool.acquire("other.com").release();
            assertThat(pool.hostCount()).isEqualTo(2);
        }
    }

    /**
     * HostPool reuses existing host instances.
     * Translated from lychee's test_host_reuse.
     */
    @Test
    void testHostReuse() {
        try (HostPool pool = new HostPool(RateLimitConfig.defaults())) {
            pool.acquire("example.com").release();
            pool.acquire("example.com").release();
            assertThat(pool.hostCount()).isEqualTo(1);
        }
    }
}
