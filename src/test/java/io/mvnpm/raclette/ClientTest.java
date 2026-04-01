package io.mvnpm.raclette;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

/**
 * Tests translated from lychee's client.rs.
 * Uses WireMock for HTTP mocking (equivalent to lychee's wiremock-rs).
 */
@WireMockTest
class ClientTest {

    @Test
    void testNonexistent(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get("/").willReturn(aResponse().withStatus(404)));

        try (Client client = Client.builder().maxRetries(0).build()) {
            Status status = client.check(wmInfo.getHttpBaseUrl());
            assertThat(status.isError()).isTrue();
        }
    }

    @Test
    void testNonGithub(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get("/").willReturn(aResponse().withStatus(200)));

        try (Client client = Client.builder().build()) {
            Status status = client.check(wmInfo.getHttpBaseUrl());
            assertThat(status.isSuccess()).isTrue();
        }
    }

    @Test
    void testFile() throws Exception {
        File tempFile = File.createTempFile("raclette-test", ".html");
        tempFile.deleteOnExit();
        String uri = "file://" + tempFile.getAbsolutePath();

        try (Client client = Client.builder().build()) {
            Status status = client.check(uri);
            assertThat(status.isSuccess()).isTrue();
        }
    }

    @Test
    void testTimeout(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get("/slow")
                .willReturn(aResponse().withStatus(200).withFixedDelay(500)));

        try (Client client = Client.builder()
                .timeout(Duration.ofMillis(100))
                .maxRetries(0)
                .build()) {
            Status status = client.check(wmInfo.getHttpBaseUrl() + "/slow");
            assertThat(status.isTimeout()).isTrue();
        }
    }

    @Test
    void testExponentialBackoff(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get("/slow")
                .willReturn(aResponse().withStatus(200).withFixedDelay(200)));

        try (Client client = Client.builder()
                .timeout(Duration.ofMillis(50))
                .maxRetries(3)
                .retryWaitTime(Duration.ofMillis(50))
                .build()) {
            // 1st request: timeout after 50ms
            // Wait 50ms, 2nd request: timeout after 50ms
            // Wait 100ms, 3rd request: timeout after 50ms
            // Wait 200ms, 4th request: timeout after 50ms
            // Total backoff: ~50+100+200 = 350ms + 4*50ms request time = ~550ms
            long start = System.currentTimeMillis();
            Status status = client.check(wmInfo.getHttpBaseUrl() + "/slow");
            long elapsed = System.currentTimeMillis() - start;

            assertThat(status.isError() || status.isTimeout()).isTrue();
            // Should take at least 300ms due to backoff waits
            assertThat(elapsed).isGreaterThan(300);
        }
    }

    @Test
    void testExcludeMailByDefault() {
        try (Client client = Client.builder().build()) {
            assertThat(client.isExcluded(Uri.mail("test@example.com"))).isTrue();
        }
    }

    @Test
    void testIncludeMail() {
        try (Client client = Client.builder().includeMail(true).build()) {
            assertThat(client.isExcluded(Uri.mail("test@example.com"))).isFalse();
        }
    }

    @Test
    void testIncludeTel() {
        // tel: is always excluded (lychee client.rs:548)
        try (Client client = Client.builder().build()) {
            assertThat(client.isExcluded(Uri.tryFrom("tel:1234567890"))).isTrue();
        }
    }

    @Test
    void testUnsupportedScheme() {
        String[] examples = { "ftp://example.com", "gopher://example.com", "slack://example.com" };

        try (Client client = Client.builder().build()) {
            for (String example : examples) {
                Status status = client.check(example);
                assertThat(status.isUnsupported()).as("scheme: %s", example).isTrue();
            }
        }
    }

    @Test
    void testCustomHeaders(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get("/").willReturn(aResponse().withStatus(200)));

        try (Client client = Client.builder()
                .customHeaders(java.util.Map.of("X-Custom", "test-value"))
                .build()) {
            Status status = client.check(wmInfo.getHttpBaseUrl());
            assertThat(status.isSuccess()).isTrue();
        }
    }

    @Test
    void testMaxRedirects(WireMockRuntimeInfo wmInfo) {
        // Set up an infinite redirect loop
        wmInfo.getWireMock().register(get("/redirect")
                .willReturn(aResponse().withStatus(308)
                        .withHeader("Location", wmInfo.getHttpBaseUrl() + "/redirect")));

        try (Client client = Client.builder()
                .maxRedirects(5)
                .maxRetries(0)
                .build()) {
            Status status = client.check(wmInfo.getHttpBaseUrl() + "/redirect");
            // Should fail after max redirects exhausted
            assertThat(status.isError()).isTrue();
        }
    }

    @Test
    void testRedirects(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get("/redirect")
                .willReturn(aResponse().withStatus(308)
                        .withHeader("Location", wmInfo.getHttpBaseUrl() + "/ok")));
        wmInfo.getWireMock().register(get("/ok")
                .willReturn(aResponse().withStatus(200)));

        try (Client client = Client.builder().maxRedirects(5).build()) {
            Status status = client.check(wmInfo.getHttpBaseUrl() + "/redirect");
            assertThat(status.isSuccess()).isTrue();
        }
    }
}
