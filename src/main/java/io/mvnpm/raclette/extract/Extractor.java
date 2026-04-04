package io.mvnpm.raclette.extract;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.mvnpm.raclette.types.RawUri;
import io.mvnpm.raclette.types.Uri;

/**
 * Dispatcher that extracts links from different content types.
 * Mirrors lychee's extract/mod.rs Extractor.
 */
public class Extractor {

    private final boolean includeVerbatim;
    private final HtmlExtractor htmlExtractor;

    public Extractor() {
        this(false);
    }

    public Extractor(boolean includeVerbatim) {
        this.includeVerbatim = includeVerbatim;
        this.htmlExtractor = new HtmlExtractor();
    }

    /**
     * Extract URIs from HTML content, parsing into Uri objects and collecting unique results.
     */
    public Set<Uri> extractHtmlUris(String html) {
        List<RawUri> rawUris = htmlExtractor.extractLinks(html, includeVerbatim);
        Set<Uri> uris = new HashSet<>();
        for (RawUri raw : rawUris) {
            Uri uri = Uri.tryFrom(raw.text());
            if (uri != null) {
                uris.add(uri);
            }
        }
        return uris;
    }

    /**
     * Extract raw URIs from HTML content.
     */
    public List<RawUri> extractHtmlRaw(String html) {
        return htmlExtractor.extractLinks(html, includeVerbatim);
    }

    /**
     * Extract the base href from the first {@code <base href="...">} in the HTML.
     */
    public String extractBaseHref(String html) {
        return htmlExtractor.extractBaseHref(html);
    }
}
