package io.mvnpm.raclette.collector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.mvnpm.raclette.types.Uri;

/**
 * Tests translated from lychee's collector.rs.
 * Uses WireMock for HTTP mocking.
 */
@WireMockTest
class CollectorTest {

    private static final String TEST_STRING = "http://test-string.com";
    private static final String TEST_URL = "https://test-url.org";
    private static final String TEST_FILE = "https://test-file.io";
    private static final String TEST_GLOB_1 = "https://test-glob-1.io";
    private static final String TEST_GLOB_2_MAIL = "test@glob-2.io";

    /**
     * Collect links from mixed input types: string, remote URL, file, glob.
     * Translated from lychee's test_collect_links.
     */
    @Test
    void testCollectLinks(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) throws IOException {
        // Create temp files with URLs
        Files.writeString(tempDir.resolve("f"), TEST_FILE + "\n");
        Files.writeString(tempDir.resolve("glob-1"), TEST_GLOB_1 + "\n");
        Files.writeString(tempDir.resolve("glob-2"), TEST_GLOB_2_MAIL + "\n");

        // Mock server returns TEST_URL in body
        wmInfo.getWireMock().register(get("/").willReturn(
                aResponse().withStatus(200).withBody(TEST_URL)));

        Set<Uri> links = Collector.builder()
                .includeVerbatim(true)
                .build()
                .collectLinks(Set.of(
                        new Input.StringContent(TEST_STRING),
                        new Input.RemoteUrl(wmInfo.getHttpBaseUrl()),
                        new Input.FsPath(tempDir.resolve("f")),
                        new Input.FsGlob(tempDir.resolve("glob*").toString())));

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website(TEST_STRING),
                Uri.website(TEST_URL),
                Uri.website(TEST_FILE),
                Uri.website(TEST_GLOB_1),
                Uri.mail(TEST_GLOB_2_MAIL));
    }

    /**
     * Collect links from HTML with a base URL, resolving relative links.
     * Translated from lychee's test_collect_html_links.
     */
    @Test
    void testCollectHtmlLinks() {
        String html = """
                <html>
                    <div class="row">
                        <a href="https://github.com/lycheeverse/lychee/">
                        <a href="blob/master/README.md">README</a>
                    </div>
                </html>""";

        Set<Uri> links = Collector.builder()
                .base("https://github.com/lycheeverse/")
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://github.com/lycheeverse/lychee/"),
                Uri.website("https://github.com/lycheeverse/blob/master/README.md"));
    }

    /**
     * Collect srcset links from HTML with a base URL.
     * Translated from lychee's test_collect_html_srcset.
     */
    @Test
    void testCollectHtmlSrcset() {
        String html = """
                <img
                    src="/static/image.png"
                    srcset="
                    /static/image300.png  300w,
                    /static/image600.png  600w,
                    "
                />""";

        Set<Uri> links = Collector.builder()
                .base("https://example.com/")
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/static/image.png"),
                Uri.website("https://example.com/static/image300.png"),
                Uri.website("https://example.com/static/image600.png"));
    }

    /**
     * Extract links from HTML5 content (not valid XML) with relative links.
     * Translated from lychee's test_extract_html5_not_valid_xml_relative_links.
     */
    @Test
    void testExtractHtml5NotValidXmlRelativeLinks() throws IOException, URISyntaxException {
        String html = Files.readString(
                Path.of(getClass().getClassLoader().getResource("fixtures/testHtml5RelativeLinks.html").toURI()));

        Set<Uri> links = Collector.builder()
                .base("https://example.com")
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        // The body links wouldn't be present if the file was parsed strictly as XML
        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/body/a"),
                Uri.website("https://example.com/body/div_empty_a"),
                Uri.website("https://example.com/css/style_full_url.css"),
                Uri.website("https://example.com/css/style_relative_url.css"),
                Uri.website("https://example.com/head/home"),
                Uri.website("https://example.com/images/icon.png"));
    }

    /**
     * Relative URLs are resolved using the remote input URL as base.
     * Translated from lychee's test_relative_url_with_base_extracted_from_input.
     */
    @Test
    void testRelativeUrlWithBaseExtractedFromInput(WireMockRuntimeInfo wmInfo) {
        String contents = """
                <html>
                    <div class="row">
                        <a href="https://github.com/lycheeverse/lychee/">GitHub</a>
                        <a href="/about">About</a>
                    </div>
                </html>""";

        wmInfo.getWireMock().register(get("/").willReturn(
                aResponse().withStatus(200).withBody(contents)));

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.RemoteUrl(wmInfo.getHttpBaseUrl())));

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://github.com/lycheeverse/lychee/"),
                Uri.website(wmInfo.getHttpBaseUrl() + "/about"));
    }

    /**
     * Email with query params is extracted as a mail URI.
     * Translated from lychee's test_email_with_query_params.
     */
    @Test
    void testEmailWithQueryParams() {
        String input = "This is a mailto:user@example.com?subject=Hello link";

        Set<Uri> links = Collector.builder()
                .includeVerbatim(true)
                .build()
                .collectLinks(Set.of(new Input.StringContent(input)));

        // Lychee's linkify crate strips query params from mailto URIs
        assertThat(links).containsExactlyInAnyOrder(
                Uri.mail("user@example.com"));
    }

    /**
     * Custom user-agent is sent when fetching remote input URLs.
     * Translated from lychee's test_user_agent_is_sent_for_remote_input_url.
     */
    @Test
    void testUserAgentIsSentForRemoteInputUrl(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get("/").willReturn(
                aResponse().withStatus(200)
                        .withBody("<a href=\"https://example.com\">Link</a>")));

        Set<Uri> links = Collector.builder()
                .userAgent("test-agent/1.0")
                .build()
                .collectLinks(Set.of(new Input.RemoteUrl(wmInfo.getHttpBaseUrl())));

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com"));

        wmInfo.getWireMock().verifyThat(
                getRequestedFor(urlEqualTo("/"))
                        .withHeader("User-Agent", matching("test-agent/1.0")));
    }

    /**
     * Multiple remote URLs each use their own path as base for relative links.
     * Translated from lychee's test_multiple_remote_urls.
     */
    @Test
    void testMultipleRemoteUrls(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get("/foo/index.html").willReturn(
                aResponse().withStatus(200)
                        .withBody("<a href=\"relative.html\">Link</a>")));
        wmInfo.getWireMock().register(get("/bar/index.html").willReturn(
                aResponse().withStatus(200)
                        .withBody("<a href=\"relative.html\">Link</a>")));

        String base = wmInfo.getHttpBaseUrl();

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(
                        new Input.RemoteUrl(base + "/foo/index.html"),
                        new Input.RemoteUrl(base + "/bar/index.html")));

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website(base + "/foo/relative.html"),
                Uri.website(base + "/bar/relative.html"));
    }

    /**
     * File path base resolves relative links to file:// URIs.
     * Translated from lychee's test_file_path_with_base.
     */
    @Test
    void testFilePathWithBase() {
        String html = """
                <a href="index.html">Index</a>
                <a href="about.html">About</a>
                <a href="../up.html">Up</a>
                <a href="/another.html">Another</a>
                """;

        Set<Uri> links = Collector.builder()
                .base("/path/to/root")
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        Set<String> linkUrls = links.stream().map(Uri::url).collect(Collectors.toSet());

        assertThat(linkUrls).containsExactlyInAnyOrder(
                "file:///path/to/root/index.html",
                "file:///path/to/root/about.html",
                "file:///path/to/up.html",
                "file:///path/to/root/another.html");
    }

    /**
     * Collect links from files in a directory (recursive walking).
     * Translated from lychee's FsPath directory walking behavior.
     */
    @Test
    void testCollectFromDirectory(@TempDir Path tempDir) throws IOException {
        Path subDir = tempDir.resolve("sub");
        Files.createDirectory(subDir);

        Files.writeString(tempDir.resolve("page1.html"),
                "<a href=\"https://example.com/1\">Link</a>");
        Files.writeString(subDir.resolve("page2.html"),
                "<a href=\"https://example.com/2\">Link</a>");

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.FsPath(tempDir)));

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/1"),
                Uri.website("https://example.com/2"));
    }

    /**
     * Collect links from files matching a glob pattern.
     */
    @Test
    void testCollectFromGlob(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("page.html"),
                "<a href=\"https://example.com/html\">Link</a>");
        Files.writeString(tempDir.resolve("data.txt"),
                "https://example.com/txt");

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(
                        new Input.FsGlob(tempDir.resolve("*.html").toString())));

        // Only HTML files matched by glob
        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/html"));
    }
}
