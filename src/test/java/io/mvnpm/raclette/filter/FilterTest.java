package io.mvnpm.raclette.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.mvnpm.raclette.types.Uri;

/**
 * Tests translated from lychee's filter/mod.rs
 */
class FilterTest {

    @Test
    void testIncludesAndExcludesEmpty() {
        Filter filter = Filter.defaults();
        assertThat(filter.isExcluded(Uri.website("https://example.com"))).isFalse();
    }

    @Test
    void testFalsePositives() {
        Filter filter = Filter.defaults();

        // W3C schema URLs should be excluded as false positives
        assertThat(filter.isExcluded(Uri.website("http://www.w3.org/1999/xhtml"))).isTrue();
        assertThat(filter.isExcluded(
                Uri.website("http://schemas.openxmlformats.org/markup-compatibility/2006")))
                .isTrue();
        assertThat(filter.isExcluded(Uri.website("https://example.com"))).isFalse();
    }

    @Test
    void testOverwriteFalsePositives() {
        // Includes should override false positives
        Filter filter = Filter.builder()
                .includes("http://www.w3.org/1999/xhtml")
                .build();

        assertThat(filter.isExcluded(Uri.website("http://www.w3.org/1999/xhtml"))).isFalse();
    }

    @Test
    void testIncludeRegex() {
        Filter filter = Filter.builder()
                .includes("foo.example.com")
                .build();

        // Only requests matching the include set will be checked
        assertThat(filter.isExcluded(Uri.website("https://foo.example.com"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("https://bar.example.com"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("https://example.com"))).isTrue();
    }

    @Test
    void testExcludeMailByDefault() {
        Filter filter = Filter.defaults();

        assertThat(filter.isExcluded(Uri.mail("mail@example.com"))).isTrue();
        assertThat(filter.isExcluded(Uri.mail("foo@bar.dev"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("http://bar.dev"))).isFalse();
    }

    @Test
    void testIncludeMail() {
        Filter filter = Filter.builder()
                .includeMail(true)
                .build();

        assertThat(filter.isExcluded(Uri.mail("mail@example.com"))).isFalse();
        assertThat(filter.isExcluded(Uri.mail("foo@bar.dev"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://bar.dev"))).isFalse();
    }

    @Test
    void testExcludeRegex() {
        Filter filter = Filter.builder()
                .excludes("github.com", "[a-z]+\\.(org|net)", "@example.com")
                .build();

        assertThat(filter.isExcluded(Uri.website("https://github.com"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("http://exclude.org"))).isTrue();
        assertThat(filter.isExcluded(Uri.mail("mail@example.com"))).isTrue();

        assertThat(filter.isExcluded(Uri.website("http://bar.dev"))).isFalse();
        // mail is excluded by default
        assertThat(filter.isExcluded(Uri.mail("foo@bar.dev"))).isTrue();
    }

    @Test
    void testExcludeIncludeRegex() {
        // Includes take precedence over excludes
        Filter filter = Filter.builder()
                .includes("foo.example.com")
                .excludes("example.com")
                .build();

        assertThat(filter.isExcluded(Uri.website("https://foo.example.com"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("https://example.com"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("https://bar.example.com"))).isTrue();
    }

    @Test
    void testExcludesNoPrivateIpsByDefault() {
        Filter filter = Filter.defaults();

        assertThat(filter.isExcluded(Uri.website("http://10.0.0.1"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://172.16.0.1"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://192.168.0.1"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://169.254.0.1"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://169.254.10.1:8080"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://127.0.0.1"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://[::1]"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://localhost"))).isFalse();
    }

    @Test
    void testExcludePrivateIps() {
        Filter filter = Filter.builder()
                .excludePrivateIps(true)
                .build();

        assertThat(filter.isExcluded(Uri.website("http://10.0.0.1"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("http://172.16.0.1"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("http://192.168.0.1"))).isTrue();
    }

    @Test
    void testExcludeLinkLocal() {
        Filter filter = Filter.builder()
                .excludeLinkLocalIps(true)
                .build();

        assertThat(filter.isExcluded(Uri.website("http://169.254.0.1"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("http://169.254.10.1:8080"))).isTrue();
    }

    @Test
    void testExcludeLoopback() {
        Filter filter = Filter.builder()
                .excludeLoopbackIps(true)
                .build();

        assertThat(filter.isExcluded(Uri.website("http://127.0.0.1"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("http://[::1]"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("http://localhost"))).isTrue();
    }

    @Test
    void testExcludeLoopbackIps() {
        Filter filter = Filter.builder()
                .excludeLoopbackIps(true)
                .build();

        assertThat(filter.isExcluded(Uri.website("https://[::1]"))).isTrue();
        assertThat(filter.isExcluded(Uri.website("https://127.0.0.1/8"))).isTrue();
    }

    @Test
    void testExcludeIpV4MappedIpV6NotSupported() {
        // IPv4-mapped IPv6 addresses are NOT excluded (same as lychee)
        Filter filter = Filter.builder()
                .excludePrivateIps(true)
                .excludeLinkLocalIps(true)
                .build();

        assertThat(filter.isExcluded(Uri.website("http://[::ffff:10.0.0.1]"))).isFalse();
        assertThat(filter.isExcluded(Uri.website("http://[::ffff:169.254.0.1]"))).isFalse();
    }
}
