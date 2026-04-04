package io.mvnpm.raclette.types;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The result of parsing a string that could be either a fully-qualified URI
 * or a relative path/fragment.
 * Mirrors lychee's types/uri/parsed.rs.
 *
 * Two variants:
 * - Absolute: a fully-qualified URI with a scheme
 * - Relative: a relative URI that requires a base for resolution
 */
public sealed interface ParsedUri {

    record Absolute(Uri uri) implements ParsedUri {
    }

    record Relative(RelativeUri rel) implements ParsedUri {
    }

    /**
     * Parse text as either an absolute or relative link.
     *
     * Uses java.net.URI for three-way classification:
     * - scheme present -> Absolute
     * - scheme absent -> Relative (Root, Scheme, or Local)
     * - parse failure -> throws (genuinely malformed)
     *
     * @return parsed URI, or null if text is null/blank
     * @throws IllegalArgumentException if the text is genuinely malformed
     */
    static ParsedUri parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.stripLeading();
        try {
            URI javaUri = new URI(trimmed);
            if (javaUri.isAbsolute()) {
                Uri uri = Uri.tryFrom(trimmed);
                return uri != null ? new Absolute(uri) : null;
            }
            return new Relative(RelativeUri.parse(trimmed));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URI: " + trimmed, e);
        }
    }
}
