package io.mvnpm.raclette.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.mvnpm.raclette.types.Uri;

/**
 * Tests translated from lychee's extract/mod.rs — HTML fixture tests.
 * These test full extraction pipeline: HTML parsing -> URI parsing -> deduplication.
 */
class ExtractorTest {

    private final Extractor extractor = new Extractor();

    private String loadFixture(String name) {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testExtractHtml5NotValidXml() {
        String input = loadFixture("test-html5.html");
        Set<Uri> links = extractor.extractHtmlUris(input);

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/head/home"),
                Uri.website("https://example.com/css/style_full_url.css"),
                // body links wouldn't be present if parsed strictly as XML
                Uri.website("https://example.com/body/a"),
                Uri.website("https://example.com/body/div_empty_a"));
    }

    @Test
    void testExtractHtml5LowercaseDoctype() {
        String input = loadFixture("test-html5-lowercase-doctype.html");
        Set<Uri> links = extractor.extractHtmlUris(input);

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/body/a"));
    }

    @Test
    void testExtractHtml5Minified() {
        // Minified HTML with quirky elements like href without quotes
        String input = loadFixture("test-html5-minified.html");
        Set<Uri> links = extractor.extractHtmlUris(input);

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/"),
                Uri.website("https://example.com/favicon.ico"),
                // preconnect links should be excluded
                Uri.website("https://example.com/docs/"),
                Uri.website("https://example.com/forum"));
    }

    @Test
    void testExtractHtml5Malformed() {
        // Malformed links shouldn't stop the parser
        String input = loadFixture("test-html5-malformed-links.html");
        Set<Uri> links = extractor.extractHtmlUris(input);

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/valid"));
    }

    @Test
    void testExtractHtml5CustomElements() {
        // Element name shouldn't matter for href, src, cite attributes
        String input = loadFixture("test-html5-custom-elements.html");
        Set<Uri> links = extractor.extractHtmlUris(input);

        assertThat(links).containsExactlyInAnyOrder(
                Uri.website("https://example.com/some-weird-element"),
                Uri.website("https://example.com/even-weirder-src"),
                Uri.website("https://example.com/even-weirder-href"),
                Uri.website("https://example.com/citations"));
    }

    @Test
    void testExtractRelativeUrl() {
        String contents = """
                <html>
                    <div class="row">
                        <a href="https://github.com/lycheeverse/lychee/">GitHub</a>
                        <a href="/about">About</a>
                    </div>
                </html>""";

        // Extract raw URIs and check the text values
        var rawUris = extractor.extractHtmlRaw(contents);
        Set<String> urls = new java.util.HashSet<>();
        for (var uri : rawUris) {
            urls.add(uri.text());
        }

        assertThat(urls).containsExactlyInAnyOrder(
                "https://github.com/lycheeverse/lychee/",
                "/about");
    }

    @Test
    void testVerbatimElem() {
        String input = "<pre>https://example.com</pre>";
        Set<Uri> uris = extractor.extractHtmlUris(input);
        assertThat(uris).isEmpty();
    }
}
