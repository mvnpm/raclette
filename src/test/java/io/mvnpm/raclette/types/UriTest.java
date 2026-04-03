package io.mvnpm.raclette.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests translated from lychee's types/uri/valid.rs.
 */
class UriTest {

    // --- test_ipv4_uri_is_loopback ---

    @Test
    void testIpv4IsLoopback() {
        Uri uri = Uri.website("http://127.0.0.0");
        assertThat(uri.isLoopback()).isTrue();
    }

    // --- test_ipv6_uri_is_loopback ---

    @Test
    void testIpv6IsLoopback() {
        Uri uri = Uri.website("https://[::1]");
        assertThat(uri.isLoopback()).isTrue();
    }

    // --- test_uri_from_url ---

    @Test
    void testTryFromUrl() {
        // Empty string → null
        assertThat(Uri.tryFrom("")).isNull();
        assertThat(Uri.tryFrom("   ")).isNull();

        // Valid URL
        Uri uri = Uri.tryFrom("https://example.com");
        assertThat(uri).isNotNull();
        assertThat(uri.url()).isEqualTo("https://example.com");
        assertThat(uri.kind()).isEqualTo(Uri.UriKind.HTTP);

        // URL with @ in path
        Uri uriAt = Uri.tryFrom("https://example.com/@test/testing");
        assertThat(uriAt).isNotNull();
        assertThat(uriAt.url()).isEqualTo("https://example.com/@test/testing");
    }

    // --- test_uri_from_email_str ---

    @Test
    void testTryFromEmail() {
        // mailto: prefix
        Uri mailtoUri = Uri.tryFrom("mailto:mail@example.com");
        assertThat(mailtoUri).isNotNull();
        assertThat(mailtoUri.isMail()).isTrue();

        // Mail factory
        Uri mailUri = Uri.mail("mail@example.com");
        assertThat(mailUri.isMail()).isTrue();
        assertThat(mailUri.url()).isEqualTo("mailto:mail@example.com");

        // mailto: prefix preserved, not doubled
        Uri mailUri2 = Uri.mail("mailto:mail@example.com");
        assertThat(mailUri2.url()).isEqualTo("mailto:mail@example.com");
    }

    // --- test_uri_tel ---

    @Test
    void testTryFromTel() {
        Uri uri = Uri.tryFrom("tel:1234567890");
        assertThat(uri).isNotNull();
        assertThat(uri.kind()).isEqualTo(Uri.UriKind.TEL);
        assertThat(uri.isTel()).isTrue();
    }

    // --- test_uri_host_ip_v4 ---

    @Test
    void testHostIpV4() {
        Uri uri = Uri.website("http://127.0.0.1");
        assertThat(uri.domain()).isEqualTo("127.0.0.1");
    }

    // --- test_uri_host_ip_v6 ---

    @Test
    void testHostIpV6() {
        Uri uri = Uri.website("https://[2020::0010]");
        assertThat(uri.domain()).isNotNull();
        // Java's URI normalizes IPv6, so just check it's an IPv6 address
        assertThat(uri.domain()).containsIgnoringCase("2020");
    }

    // --- test_uri_host_ip_no_ip ---

    @Test
    void testHostIpNoIp() {
        Uri uri = Uri.website("https://some.cryptic/url");
        assertThat(uri.domain()).isEqualTo("some.cryptic");
        assertThat(uri.isLoopback()).isFalse();
    }

    // --- test_file_uri ---

    @Test
    void testFileUri() {
        Uri uri = Uri.tryFrom("file:///path/to/file");
        assertThat(uri).isNotNull();
        assertThat(uri.kind()).isEqualTo(Uri.UriKind.FILE);
    }
}
