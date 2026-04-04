package io.mvnpm.raclette.types;

/**
 * A relative link that requires a base for resolution.
 * Mirrors lychee's types/uri/relative.rs.
 *
 * Three variants:
 * - Root: starts with / but not // (e.g. "/docs/index.html")
 * - Scheme: starts with // (e.g. "//example.com/path")
 * - Local: everything else (e.g. "other.html", "../parent", "#fragment")
 */
public sealed interface RelativeUri {

    record Root(String text) implements RelativeUri {
    }

    record Scheme(String text) implements RelativeUri {
    }

    record Local(String text) implements RelativeUri {
    }

    /**
     * Classify a relative link text into Root, Scheme, or Local.
     * Leading whitespace is trimmed (matches lychee's trim_ascii_start).
     */
    static RelativeUri parse(String text) {
        String trimmed = text.stripLeading();
        if (trimmed.startsWith("//")) {
            return new Scheme(trimmed);
        }
        if (trimmed.startsWith("/")) {
            return new Root(trimmed);
        }
        return new Local(trimmed);
    }

    /**
     * Returns the link text for this relative URI.
     */
    default String linkText() {
        return switch (this) {
            case Root r -> r.text();
            case Scheme s -> s.text();
            case Local l -> l.text();
        };
    }
}
