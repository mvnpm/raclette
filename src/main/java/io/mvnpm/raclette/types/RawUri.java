package io.mvnpm.raclette.types;

import java.util.Objects;

/**
 * A raw URI extracted from content, before parsing/validation.
 * Contains the text, the source element and attribute, and position info.
 */
public record RawUri(
        String text,
        String element,
        String attribute,
        int line,
        int column) {

    public RawUri {
        Objects.requireNonNull(text, "text must not be null");
    }

    /**
     * Create a RawUri with no element/attribute info (e.g. from text content).
     */
    public static RawUri ofText(String text, int line, int column) {
        return new RawUri(text, null, null, line, column);
    }
}
