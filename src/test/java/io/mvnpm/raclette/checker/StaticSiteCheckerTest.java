package io.mvnpm.raclette.checker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

class StaticSiteCheckerTest {

    @Nested
    class RewriteLocalhostUriTest {

        @Test
        void basicRewrite(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("http://localhost:8080/about"));
            assertThat(result.kind()).isEqualTo(Uri.UriKind.FILE);
            assertThat(result.url()).isEqualTo(siteRoot.resolve("about").toUri().toString());
        }

        @Test
        void withPortAndNestedPath(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("http://localhost:8081/foo/bar"));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("foo/bar").toUri().toString());
        }

        @Test
        void ipVariant(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("http://127.0.0.1:3000/page"));
            assertThat(result.kind()).isEqualTo(Uri.UriKind.FILE);
            assertThat(result.url()).isEqualTo(siteRoot.resolve("page").toUri().toString());
        }

        @Test
        void noPort(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("http://localhost/root"));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("root").toUri().toString());
        }

        @Test
        void httpsVariant(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("https://localhost:8443/secure"));
            assertThat(result.kind()).isEqualTo(Uri.UriKind.FILE);
            assertThat(result.url()).isEqualTo(siteRoot.resolve("secure").toUri().toString());
        }

        @Test
        void noPathResolvesToSiteRoot(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("http://localhost:8080"));
            assertThat(result.kind()).isEqualTo(Uri.UriKind.FILE);
            assertThat(result.url()).isEqualTo(siteRoot.toUri().toString());
        }

        @Test
        void queryStringStripped(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("http://localhost:8080/page?v=1"));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("page").toUri().toString());
        }

        @Test
        void fragmentPreserved(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("http://localhost:8080/page#section"));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("page").toUri() + "#section");
        }

        @Test
        void queryStrippedFragmentPreserved(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri result = checker.rewriteLocalUrlsToFileUri(Uri.website("http://localhost:8080/page?v=1#section"));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("page").toUri() + "#section");
        }

        @Test
        void nonLocalhostUnchanged(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri input = Uri.website("http://example.com/page");
            Uri result = checker.rewriteLocalUrlsToFileUri(input);
            assertThat(result).isSameAs(input);
        }

        @Test
        void fileUriUnchanged(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri input = Uri.file("file:///some/path");
            Uri result = checker.rewriteLocalUrlsToFileUri(input);
            assertThat(result).isSameAs(input);
        }

        @Test
        void mailUriUnchanged(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/");
            Uri input = Uri.tryFrom("mailto:user@localhost");
            Uri result = checker.rewriteLocalUrlsToFileUri(input);
            assertThat(result).isSameAs(input);
        }
    }

    @Nested
    class RewriteUriWithBasePathTest {

        @Test
        void localhostWithBasePath(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            Uri result = checker.rewriteUri(Uri.website("http://localhost:8080/raclette/about"));
            assertThat(result.kind()).isEqualTo(Uri.UriKind.FILE);
            assertThat(result.url()).isEqualTo(siteRoot.resolve("about").toUri().toString());
        }

        @Test
        void localhostWithBasePathNoTrailingSlash(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette");
            Uri result = checker.rewriteUri(Uri.website("http://localhost:8080/raclette/docs/config"));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("docs/config").toUri().toString());
        }

        @Test
        void localhostWithoutBasePathPrefix(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            Uri result = checker.rewriteUri(Uri.website("http://localhost:8080/about"));
            // Still resolves as localhost, just no basePath to strip
            assertThat(result.kind()).isEqualTo(Uri.UriKind.FILE);
            assertThat(result.url()).isEqualTo(siteRoot.resolve("about").toUri().toString());
        }

        @Test
        void localhostWithBasePathFragmentPreserved(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            Uri result = checker.rewriteUri(Uri.website("http://localhost:8080/raclette/page#section"));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("page").toUri() + "#section");
        }

        @Test
        void fileUriBasePathStripped(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            String fileUrl = siteRoot.toUri() + "raclette/docs/getting-started";
            Uri result = checker.rewriteUri(Uri.file(fileUrl));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("docs/getting-started").toUri().toString());
        }

        @Test
        void fileUriBasePathRootStripped(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            String fileUrl = siteRoot.toUri() + "raclette/";
            Uri result = checker.rewriteUri(Uri.file(fileUrl));
            assertThat(result.url()).isEqualTo(siteRoot.toUri().toString());
        }

        @Test
        void fileUriAssetStripped(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            String fileUrl = siteRoot.toUri() + "raclette/static/bundle/app.css";
            Uri result = checker.rewriteUri(Uri.file(fileUrl));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("static/bundle/app.css").toUri().toString());
        }

        @Test
        void fileUriLogoStripped(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            String fileUrl = siteRoot.toUri() + "raclette/raclette-logo.svg";
            Uri result = checker.rewriteUri(Uri.file(fileUrl));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("raclette-logo.svg").toUri().toString());
        }

        @Test
        void fileUriFragmentPreserved(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            String fileUrl = siteRoot.toUri() + "raclette/page#section";
            Uri result = checker.rewriteUri(Uri.file(fileUrl));
            assertThat(result.url()).isEqualTo(siteRoot.resolve("page").toUri() + "#section");
        }

        @Test
        void fileUriNoBasePathPrefixUnchanged(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            String fileUrl = siteRoot.toUri() + "docs/getting-started";
            Uri input = Uri.file(fileUrl);
            Uri result = checker.rewriteUri(input);
            assertThat(result).isSameAs(input);
        }

        @Test
        void fileUriNotUnderSiteRootUnchanged(@TempDir Path siteRoot) {
            StaticSiteChecker checker = buildChecker(siteRoot, "/raclette/");
            Uri input = Uri.file("file:///other/path/raclette/foo");
            Uri result = checker.rewriteUri(input);
            assertThat(result).isSameAs(input);
        }
    }

    @Nested
    @WireMockTest
    class RemoteLinkIntegrationTest {

        @Test
        void remoteLinksCheckedWhenEnabled(@TempDir Path siteRoot, WireMockRuntimeInfo wmInfo)
                throws IOException {
            wmInfo.getWireMock().register(get(urlEqualTo("/remote")).willReturn(
                    aResponse().withStatus(200)));

            String base = wmInfo.getHttpBaseUrl();
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"" + base + "/remote\">Remote</a><a href=\"about\">About</a></html>",
                    "about.html", "<html>About</html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .checkRemoteLinks(true)
                    .rewriteLocalUrlsToFile(false)
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void remoteLinksIgnoredWhenDisabled(@TempDir Path siteRoot, WireMockRuntimeInfo wmInfo)
                throws IOException {
            String base = wmInfo.getHttpBaseUrl();
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"" + base + "/whatever\">Remote</a><a href=\"about\">About</a></html>",
                    "about.html", "<html>About</html>");

            try (StaticSiteChecker checker = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .checkRemoteLinks(false)
                    .rewriteLocalUrlsToFile(false)
                    .sequential(true)
                    .build()) {
                Map<Uri, Status> all = checker.checkAll();

                // Only file links should be in results (HTTP links filtered out)
                assertThat(all.keySet()).noneMatch(uri -> uri.kind() == Uri.UriKind.HTTP);
            }
        }
    }

    @Nested
    class IntegrationTest {

        @Test
        void validFileLinks(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html", "<html><a href=\"about\">About</a></html>",
                    "about.html", "<html><body>About page</body></html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void brokenFileLinks(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html", "<html><a href=\"missing\">Missing</a></html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).hasSize(1);
            assertThat(broken.values()).allMatch(Status::isError);
        }

        @Test
        void localhostUrlCheckedOnDisk(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"http://localhost:8080/about\">About</a></html>",
                    "about.html", "<html>About</html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void localhostUrlWithIndexFile(@TempDir Path siteRoot) throws IOException {
            Files.createDirectories(siteRoot.resolve("docs"));
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"http://localhost:8080/docs/\">Docs</a></html>",
                    "docs/index.html", "<html>Docs</html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void fragmentChecking(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"http://localhost:8080/page#existing\">Link</a></html>",
                    "page.html", "<html><h2 id=\"existing\">Section</h2></html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .includeFragments(true)
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void excludesFilter(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"http://localhost:8080/missing\">Missing</a></html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .excludes(".*missing.*")
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void checkAllReturnsBothSuccessesAndFailures(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"about\">About</a><a href=\"missing\">Missing</a></html>",
                    "about.html", "<html>About</html>");

            try (StaticSiteChecker checker = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .sequential(true)
                    .build()) {
                Map<Uri, Status> all = checker.checkAll();
                assertThat(all.values().stream().filter(Status::isSuccess).count()).isGreaterThan(0);
                assertThat(all.values().stream().filter(Status::isError).count()).isGreaterThan(0);
            }
        }

        @Test
        void emptySiteDirectory(@TempDir Path siteRoot) {
            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void staticConvenience(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html", "<html><a href=\"about\">About</a></html>",
                    "about.html", "<html>About</html>");

            Map<Uri, Status> broken = StaticSiteChecker.check(siteRoot);
            assertThat(broken).isEmpty();
        }

        @Test
        void parallelAndSequentialProduceSameResults(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"about\">A</a><a href=\"missing\">M</a></html>",
                    "about.html", "<html>About</html>");

            Map<Uri, Status> sequential;
            try (StaticSiteChecker checker = StaticSiteChecker.builder()
                    .path(siteRoot).sequential(true).build()) {
                sequential = checker.checkAll();
            }

            Map<Uri, Status> parallel;
            try (StaticSiteChecker checker = StaticSiteChecker.builder()
                    .path(siteRoot).sequential(false).build()) {
                parallel = checker.checkAll();
            }

            assertThat(parallel).containsExactlyInAnyOrderEntriesOf(sequential);
        }

        @Test
        void basePathWithLocalhostUrl(@TempDir Path siteRoot) throws IOException {
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"http://localhost:8080/raclette/about\">About</a></html>",
                    "about.html", "<html>About</html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .basePath("/raclette/")
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void basePathWithFileLinks(@TempDir Path siteRoot) throws IOException {
            // Simulate what the Collector produces with root-path links:
            // HTML has <a href="/raclette/docs/config">
            // Collector resolves against site root → file:///site/raclette/docs/config
            // StaticSiteChecker strips basePath → file:///site/docs/config
            Files.createDirectories(siteRoot.resolve("docs"));
            createSite(siteRoot,
                    "index.html",
                    "<html><a href=\"/raclette/docs/config\">Config</a></html>",
                    "docs/config.html", "<html>Config</html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .basePath("/raclette/")
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        // --- integration ---

        @Test
        void relativeLinkWithinSiteRootIsOk(@TempDir Path siteRoot) throws IOException {
            // ../sibling from docs/page.html stays within site root
            Files.createDirectories(siteRoot.resolve("docs"));
            createSite(siteRoot,
                    "docs/page.html",
                    "<html><a href=\"../sibling\">Sibling</a></html>",
                    "sibling.html", "<html>Sibling</html>");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }

        @Test
        void basePathWithAssetLinks(@TempDir Path siteRoot) throws IOException {
            Files.createDirectories(siteRoot.resolve("static/bundle"));
            createSite(siteRoot,
                    "index.html",
                    "<html><link href=\"/raclette/static/bundle/app.css\" rel=\"stylesheet\"></html>",
                    "static/bundle/app.css", "body { color: red; }");

            Map<Uri, Status> broken = StaticSiteChecker.builder()
                    .path(siteRoot)
                    .basePath("/raclette/")
                    .sequential(true)
                    .build()
                    .check();

            assertThat(broken).isEmpty();
        }
    }

    // --- Dogfooding: check the real Raclette site ---

    @Nested
    class DogfoodTest {

        static final Path SITE_ROOT = Path.of("site/target/roq").toAbsolutePath();

        static boolean siteExists() {
            return Files.isDirectory(SITE_ROOT) && Files.exists(SITE_ROOT.resolve("index.html"));
        }

        @Test
        @EnabledIf("siteExists")
        void detectsDeliberatelyBrokenLinkOnHomepage() {
            try (StaticSiteChecker checker = StaticSiteChecker.builder()
                    .path(SITE_ROOT)
                    .basePath("/raclette/")
                    .sequential(true)
                    .build()) {
                Map<Uri, Status> broken = checker.check();

                // The homepage contains a deliberately broken link: "this-page-does-not-exist"
                assertThat(broken).isNotEmpty();
                assertThat(broken.keySet().stream().map(Uri::url))
                        .anyMatch(url -> url.contains("this-page-does-not-exist"));
            }
        }
    }

    // --- Helpers ---

    private static StaticSiteChecker buildChecker(Path siteRoot, String basePath) {
        return StaticSiteChecker.builder()
                .path(siteRoot)
                .basePath(basePath)
                .sequential(true)
                .build();
    }

    private static void createSite(Path root, String... nameContentPairs) throws IOException {
        for (int i = 0; i < nameContentPairs.length; i += 2) {
            Path file = root.resolve(nameContentPairs[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, nameContentPairs[i + 1]);
        }
    }
}
