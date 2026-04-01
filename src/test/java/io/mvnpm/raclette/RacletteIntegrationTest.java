package io.mvnpm.raclette;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.mvnpm.raclette.collector.Collector;
import io.mvnpm.raclette.collector.Input;
import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

/**
 * End-to-end integration tests: Collector extracts links, Client checks them.
 * Translated from lychee's integration tests and lib.rs tests.
 */
@WireMockTest
class RacletteIntegrationTest {

    /**
     * Collect links from HTML and check them — valid links return OK.
     */
    @Test
    void testCollectAndCheckValidLinks(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get(urlEqualTo("/page1")).willReturn(
                aResponse().withStatus(200)));
        wmInfo.getWireMock().register(get(urlEqualTo("/page2")).willReturn(
                aResponse().withStatus(200)));

        String base = wmInfo.getHttpBaseUrl();
        String html = """
                <html>
                    <a href="%s/page1">Page 1</a>
                    <a href="%s/page2">Page 2</a>
                </html>""".formatted(base, base);

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        try (Raclette raclette = Raclette.builder().build()) {
            Map<Uri, Status> results = new LinkedHashMap<>();
            for (Uri uri : links) {
                results.put(uri, raclette.check(uri));
            }

            assertThat(results).hasSize(2);
            assertThat(results.values()).allMatch(Status::isSuccess);
        }
    }

    /**
     * Collect links from HTML and check them — broken links return error status.
     */
    @Test
    void testCollectAndCheckBrokenLinks(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get(urlEqualTo("/ok")).willReturn(
                aResponse().withStatus(200)));
        wmInfo.getWireMock().register(get(urlEqualTo("/broken")).willReturn(
                aResponse().withStatus(404)));

        String base = wmInfo.getHttpBaseUrl();
        String html = """
                <html>
                    <a href="%s/ok">OK</a>
                    <a href="%s/broken">Broken</a>
                </html>""".formatted(base, base);

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        try (Raclette raclette = Raclette.builder()
                .maxRetries(0)
                .build()) {
            Map<Uri, Status> results = new LinkedHashMap<>();
            for (Uri uri : links) {
                results.put(uri, raclette.check(uri));
            }

            assertThat(results).hasSize(2);

            Uri okUri = Uri.website(base + "/ok");
            Uri brokenUri = Uri.website(base + "/broken");

            assertThat(results.get(okUri).isSuccess()).isTrue();
            assertThat(results.get(brokenUri).isError()).isTrue();
        }
    }

    /**
     * Collect links from local files and check them.
     */
    @Test
    void testCollectFromFilesAndCheckLinks(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) throws IOException {
        wmInfo.getWireMock().register(get(urlEqualTo("/target")).willReturn(
                aResponse().withStatus(200)));

        String base = wmInfo.getHttpBaseUrl();
        Files.writeString(tempDir.resolve("page.html"),
                "<a href=\"" + base + "/target\">Link</a>");

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.FsPath(tempDir)));

        try (Raclette raclette = Raclette.builder().build()) {
            Map<Uri, Status> results = new LinkedHashMap<>();
            for (Uri uri : links) {
                results.put(uri, raclette.check(uri));
            }

            assertThat(results).hasSize(1);
            assertThat(results.values()).allMatch(Status::isSuccess);
        }
    }

    /**
     * Excluded links are filtered out by the client.
     */
    @Test
    void testExcludedLinksAreFiltered(WireMockRuntimeInfo wmInfo) {
        wmInfo.getWireMock().register(get(urlEqualTo("/included")).willReturn(
                aResponse().withStatus(200)));

        String base = wmInfo.getHttpBaseUrl();
        String html = """
                <html>
                    <a href="%s/included">Included</a>
                    <a href="%s/excluded">Excluded</a>
                </html>""".formatted(base, base);

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        try (Raclette raclette = Raclette.builder()
                .excludes(".*excluded.*")
                .build()) {
            Map<Uri, Status> results = new LinkedHashMap<>();
            for (Uri uri : links) {
                results.put(uri, raclette.check(uri));
            }

            Uri includedUri = Uri.website(base + "/included");
            Uri excludedUri = Uri.website(base + "/excluded");

            assertThat(results.get(includedUri).isSuccess()).isTrue();
            assertThat(results.get(excludedUri).isExcluded()).isTrue();
        }
    }

    /**
     * End-to-end: collect from remote URL, resolve relative links, check all.
     */
    @Test
    void testCollectFromRemoteAndCheck(WireMockRuntimeInfo wmInfo) {
        String base = wmInfo.getHttpBaseUrl();

        wmInfo.getWireMock().register(get(urlEqualTo("/")).willReturn(
                aResponse().withStatus(200)
                        .withBody("<a href=\"/about\">About</a><a href=\"/contact\">Contact</a>")));
        wmInfo.getWireMock().register(get(urlEqualTo("/about")).willReturn(
                aResponse().withStatus(200)));
        wmInfo.getWireMock().register(get(urlEqualTo("/contact")).willReturn(
                aResponse().withStatus(200)));

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.RemoteUrl(base)));

        try (Raclette raclette = Raclette.builder().build()) {
            Map<Uri, Status> results = new LinkedHashMap<>();
            for (Uri uri : links) {
                results.put(uri, raclette.check(uri));
            }

            assertThat(results).hasSize(2);
            assertThat(results.values()).allMatch(Status::isSuccess);
        }
    }

    /**
     * File links collected from HTML are checked by FileChecker.
     */
    @Test
    void testCollectAndCheckFileLinks(@TempDir Path tempDir) throws IOException {
        // Create the target files
        Files.writeString(tempDir.resolve("target.html"), "<html><body>Hello</body></html>");

        String html = "<a href=\"file://" + tempDir.resolve("target.html") + "\">Target</a>";

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        try (Raclette raclette = Raclette.builder().build()) {
            Map<Uri, Status> results = new LinkedHashMap<>();
            for (Uri uri : links) {
                results.put(uri, raclette.check(uri));
            }

            assertThat(results).hasSize(1);
            assertThat(results.values()).allMatch(Status::isSuccess);
        }
    }

    /**
     * Missing file links return error status.
     */
    @Test
    void testCollectAndCheckMissingFileLinks(@TempDir Path tempDir) {
        String html = "<a href=\"file://" + tempDir.resolve("nonexistent.html") + "\">Missing</a>";

        Set<Uri> links = Collector.builder()
                .build()
                .collectLinks(Set.of(new Input.StringContent(html)));

        try (Raclette raclette = Raclette.builder().build()) {
            Map<Uri, Status> results = new LinkedHashMap<>();
            for (Uri uri : links) {
                results.put(uri, raclette.check(uri));
            }

            assertThat(results).hasSize(1);
            assertThat(results.values()).allMatch(Status::isError);
        }
    }
}
