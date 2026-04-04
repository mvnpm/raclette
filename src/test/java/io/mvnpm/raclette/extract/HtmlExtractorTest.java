package io.mvnpm.raclette.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.mvnpm.raclette.types.RawUri;

/**
 * Tests translated from lychee's extract/html/html5gum.rs.
 *
 * Note: line/column positions are not asserted because JSoup (DOM parser)
 * does not track source positions like html5gum (streaming tokenizer).
 * We verify text, element, and attribute which cover the extraction logic.
 */
class HtmlExtractorTest {

    static final String HTML_INPUT = """
            <html>
                <body id="content">
                    <p>This is a paragraph with some inline <code id="inline-code">https://example.com</code> and a normal <a href="https://example.org">example</a></p>
                    <pre>
                    Some random text
                    https://foo.com and http://bar.com/some/path
                    Something else
                    <a href="https://baz.org">example link inside pre</a>
                    </pre>
                    <p id="emphasis"><b>bold</b></p>
                </body>
            </html>""";

    private final HtmlExtractor extractor = new HtmlExtractor();

    // --- Fragment extraction tests ---

    @Test
    void testExtractFragments() {
        Set<String> expected = Set.of("content", "inline-code", "emphasis");
        Set<String> actual = extractor.extractFragments(HTML_INPUT);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testExtractFragmentsWithNameAttributes() {
        String input = """
                <html>
                <body>
                    <h1 id="title">Title</h1>
                    <a name="skip.navbar.top"></a>
                    <a name="method.summary"></a>
                    <div>
                        <a name="clear--"></a>
                        <h2 id="section">Section</h2>
                        <a name="method.detail"></a>
                    </div>
                    <a name="skip.navbar.bottom"></a>
                </body>
                </html>
                """;

        Set<String> expected = Set.of(
                "title", "section",
                "skip.navbar.top", "method.summary",
                "clear--", "method.detail", "skip.navbar.bottom");
        Set<String> actual = extractor.extractFragments(input);
        assertThat(actual).isEqualTo(expected);
    }

    // --- Link extraction tests ---

    @Test
    void testSkipVerbatim() {
        List<RawUri> uris = extractor.extractLinks(HTML_INPUT, false);

        assertThat(uris)
                .extracting(RawUri::text, RawUri::element, RawUri::attribute)
                .containsExactly(tuple("https://example.org", "a", "href"));
    }

    @Test
    void testIncludeVerbatim() {
        List<RawUri> uris = extractor.extractLinks(HTML_INPUT, true);

        assertThat(uris)
                .extracting(RawUri::text)
                .containsExactly(
                        "https://example.com",
                        "https://example.org",
                        "https://foo.com",
                        "http://bar.com/some/path",
                        "https://baz.org");
    }

    @Test
    void testIncludeVerbatimNested() {
        String input = """
                <a href="https://example.com/">valid link</a>
                <code>
                    <pre>
                        <span>https://example.org</span>
                    </pre>
                </code>
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris)
                .extracting(RawUri::text, RawUri::element, RawUri::attribute)
                .containsExactly(tuple("https://example.com/", "a", "href"));
    }

    @Test
    void testIncludeVerbatimNestedIdentical() {
        String input = """
                <pre>
                    <pre>
                    </pre>
                    <a href="https://example.org">invalid link</a>
                </pre>
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testExcludeNofollow() {
        String input = """
                <a rel="nofollow" href="https://foo.com">do not follow me</a>
                <a rel="canonical,nofollow,dns-prefetch" href="https://example.com">do not follow me</a>
                <a href="https://example.org">i'm fine</a>
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).hasSize(1);
        assertThat(uris.getFirst().text()).isEqualTo("https://example.org");
    }

    @Test
    void testExcludeNofollowChangeOrder() {
        String input = """
                <a href="https://foo.com" rel="nofollow">do not follow me</a>
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testExcludeScriptTags() {
        String input = """
                <script>
                var foo = "https://example.com";
                </script>
                <a href="https://example.org">i'm fine</a>
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).hasSize(1);
        assertThat(uris.getFirst().text()).isEqualTo("https://example.org");
    }

    @Test
    void testExcludeDisabledStylesheet() {
        String input = """
                <link rel="stylesheet" href="https://disabled.com" disabled>
                <link rel="stylesheet" href="https://disabled.com" disabled="disabled">
                <a href="https://example.org">i'm fine</a>
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).hasSize(1);
        assertThat(uris.getFirst().text()).isEqualTo("https://example.org");
    }

    @Test
    void testValidTel() {
        String input = """
                <!DOCTYPE html>
                <html lang="en-US">
                  <head>
                    <meta charset="utf-8">
                    <title>Test</title>
                  </head>
                  <body>
                    <a href="tel:1234567890">
                  </body>
                </html>""";

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).hasSize(1);
        assertThat(uris.getFirst().text()).isEqualTo("tel:1234567890");
        assertThat(uris.getFirst().element()).isEqualTo("a");
        assertThat(uris.getFirst().attribute()).isEqualTo("href");
    }

    @Test
    void testValidEmail() {
        String input = """
                <!DOCTYPE html>
                <html lang="en-US">
                  <head>
                    <meta charset="utf-8">
                    <title>Test</title>
                  </head>
                  <body>
                    <a href="mailto:foo@bar.com">
                  </body>
                </html>""";

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).hasSize(1);
        assertThat(uris.getFirst().text()).isEqualTo("mailto:foo@bar.com");
    }

    @Test
    void testExcludeEmailWithoutMailto() {
        String input = """
                <!DOCTYPE html>
                <html lang="en-US">
                  <head>
                    <meta charset="utf-8">
                    <title>Test</title>
                  </head>
                  <body>
                    <a href="foo@bar.com">
                  </body>
                </html>""";

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testEmailFalsePositive() {
        String input = """
                <img srcset="v2@1.5x.png" alt="Wikipedia" width="200" height="183">""";

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testExtractSrcset() {
        String input = """
                <img srcset="/cdn-cgi/image/format=webp,width=640/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg 640w, /cdn-cgi/image/format=webp,width=750/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg 750w" src="/cdn-cgi/image/format=webp,width=3840/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg">
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).extracting(RawUri::text).containsExactly(
                "/cdn-cgi/image/format=webp,width=640/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg",
                "/cdn-cgi/image/format=webp,width=750/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg",
                "/cdn-cgi/image/format=webp,width=3840/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg");
    }

    @Test
    void testSkipPreconnect() {
        String input = """
                <link rel="preconnect" href="https://example.com">
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testSkipPreconnectReverseOrder() {
        String input = """
                <link href="https://example.com" rel="preconnect">
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testSkipPrefix() {
        String input = """
                <html lang="en-EN" prefix="og: https://ogp.me/ns#">
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testIgnoreTextContentLinks() {
        String input = """
                <a href="https://example.com">https://ignoreme.com</a>
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).hasSize(1);
        assertThat(uris.getFirst().text()).isEqualTo("https://example.com");
    }

    @Test
    void testSkipDnsPrefetch() {
        String input = """
                <link rel="dns-prefetch" href="https://example.com">
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testSkipDnsPrefetchReverseOrder() {
        String input = """
                <link href="https://example.com" rel="dns-prefetch">
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testSkipEmailsInStylesheets() {
        String input = """
                <link href="/@global/global.css" rel="stylesheet">
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).isEmpty();
    }

    @Test
    void testPositionTracking() {
        // Simple HTML with known positions to verify line/column tracking
        String input = "<a href=\"https://example.com\">link</a>\n<a href=\"https://example.org\">link2</a>";

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).hasSize(2);
        // First link: line 1, href value starts at column 10 (after <a href=")
        assertThat(uris.get(0)).isEqualTo(new RawUri("https://example.com", "a", "href", 1, 10));
        // Second link: line 2, href value starts at column 10
        assertThat(uris.get(1)).isEqualTo(new RawUri("https://example.org", "a", "href", 2, 10));
    }

    @Test
    void testPositionTrackingSrcset() {
        String input = "<img srcset=\"/image1.jpg 640w, /image2.jpg 750w\" src=\"/main.jpg\">";

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).hasSize(3);
        // srcset URLs get the position of the srcset attribute value start
        assertThat(uris.get(0).text()).isEqualTo("/image1.jpg");
        assertThat(uris.get(0).line()).isEqualTo(1);
        assertThat(uris.get(0).attribute()).isEqualTo("srcset");
        // src URL
        assertThat(uris.get(2).text()).isEqualTo("/main.jpg");
        assertThat(uris.get(2).attribute()).isEqualTo("src");
    }

    @Test
    void testPositionTrackingTextNodes() {
        String input = "<pre>https://example.com\nhttps://example.org</pre>";

        List<RawUri> uris = extractor.extractLinks(input, true);
        assertThat(uris).hasSize(2);
        assertThat(uris.get(0).text()).isEqualTo("https://example.com");
        assertThat(uris.get(0).line()).isEqualTo(1);
        assertThat(uris.get(1).text()).isEqualTo("https://example.org");
        assertThat(uris.get(1).line()).isEqualTo(2);
    }

    // --- <base href> extraction ---

    @Test
    void testExtractBaseHref() {
        String html = """
                <html>
                <head><base href="https://example.com/docs/"></head>
                <body><a href="page.html">Link</a></body>
                </html>
                """;
        assertThat(extractor.extractBaseHref(html)).isEqualTo("https://example.com/docs/");
    }

    @Test
    void testExtractBaseHrefAbsent() {
        String html = """
                <html>
                <head><title>No base</title></head>
                <body><a href="page.html">Link</a></body>
                </html>
                """;
        assertThat(extractor.extractBaseHref(html)).isNull();
    }

    @Test
    void testExtractBaseHrefFirstWins() {
        // Per HTML spec, only the first <base href> is used
        String html = """
                <html>
                <head>
                    <base href="https://first.com/">
                    <base href="https://second.com/">
                </head>
                <body><a href="page.html">Link</a></body>
                </html>
                """;
        assertThat(extractor.extractBaseHref(html)).isEqualTo("https://first.com/");
    }

    @Test
    void testExtractBaseHrefEmptyIgnored() {
        String html = """
                <html>
                <head><base href=""></head>
                <body><a href="page.html">Link</a></body>
                </html>
                """;
        assertThat(extractor.extractBaseHref(html)).isNull();
    }

    @Test
    void testExtractBaseHrefNoHrefAttribute() {
        // <base> can have target only, no href
        String html = """
                <html>
                <head><base target="_blank"></head>
                <body><a href="page.html">Link</a></body>
                </html>
                """;
        assertThat(extractor.extractBaseHref(html)).isNull();
    }

    @Test
    void testBaseHrefNotExtractedAsLink() {
        // The <base href> itself should not appear in the extracted links
        String html = """
                <html>
                <head><base href="https://example.com/docs/"></head>
                <body><a href="page.html">Link</a></body>
                </html>
                """;
        List<RawUri> links = extractor.extractLinks(html, false);
        assertThat(links).extracting(RawUri::text)
                .containsExactly("page.html")
                .doesNotContain("https://example.com/docs/");
    }

    @Test
    void testExtractLinksAfterEmptyVerbatimBlock() {
        String input = """
                <body>
                    <div>
                        See <a href="https://example.com/1">First</a>
                    </div>
                    <pre>
                        <code></code>
                    </pre>
                    <div>
                        See <a href="https://example.com/2">Second</a>
                    </div>
                </body>
                """;

        List<RawUri> uris = extractor.extractLinks(input, false);
        assertThat(uris).extracting(RawUri::text).containsExactly(
                "https://example.com/1",
                "https://example.com/2");
    }
}
