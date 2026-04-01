package io.mvnpm.raclette.collector;

import java.nio.file.Path;

/**
 * Represents an input source for the Collector.
 * Translated from lychee's types/input.rs InputSource enum.
 */
public sealed interface Input {

    /**
     * A local file or directory path (walked recursively for directories).
     */
    record FsPath(Path path) implements Input {
    }

    /**
     * A glob pattern for matching local files (e.g. "docs/**\/*.html").
     */
    record FsGlob(String pattern) implements Input {
    }

    /**
     * A remote URL to fetch and extract links from.
     */
    record RemoteUrl(String url) implements Input {
    }

    /**
     * Raw string content with an explicit content type.
     */
    record StringContent(String content) implements Input {
    }
}
