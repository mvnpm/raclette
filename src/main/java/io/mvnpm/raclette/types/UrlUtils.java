package io.mvnpm.raclette.types;

import java.net.URI;

/**
 * Shared URL string manipulation utilities.
 * Operates on URL strings without constructing URI objects except where
 * URI.create().getPath() is needed for percent-decoding.
 */
public final class UrlUtils {

    private UrlUtils() {
    }

    /**
     * Split a URL into path and fragment.
     *
     * @return [path, fragment] where fragment may be null
     */
    public static String[] splitFragment(String url) {
        int h = url.indexOf('#');
        if (h < 0) {
            return new String[] { url, null };
        }
        return new String[] { url.substring(0, h), url.substring(h + 1) };
    }

    /**
     * Strip query and fragment from a URL, returning just the path portion.
     * Finds # first, then ? only before # (a ? inside a fragment is legal).
     */
    public static String stripQueryAndFragment(String url) {
        int h = url.indexOf('#');
        int q = url.indexOf('?');
        if (q >= 0 && (h < 0 || q < h)) {
            return url.substring(0, q);
        }
        if (h >= 0) {
            return url.substring(0, h);
        }
        return url;
    }

    /**
     * Extract the fragment from a URL, or null if none.
     */
    public static String extractFragment(String url) {
        int h = url.indexOf('#');
        return h >= 0 ? url.substring(h + 1) : null;
    }

    /**
     * Convert a file: URL to a filesystem path string.
     * Uses URI.create().getPath() for proper percent-decoding and authority handling.
     */
    public static String fileUrlToPath(String url) {
        if (url.startsWith("file:")) {
            return URI.create(url).getPath();
        }
        return url;
    }

    /**
     * Convert a filesystem path to a file: URL.
     */
    public static String pathToFileUrl(String path) {
        return path.startsWith("/") ? "file://" + path : "file:///" + path;
    }

    /**
     * Normalize a path prefix to have leading / and trailing /.
     */
    public static String normalizePathPrefix(String prefix) {
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix;
    }

    /**
     * Normalize file:/ URIs to file:/// form.
     */
    public static String normalizeFileUrl(String url) {
        if (url.startsWith("file:/") && !url.startsWith("file:///")) {
            // Strip file:/ or file:// prefix, then re-add file:///
            String rest = url.substring("file:".length());
            while (rest.startsWith("/")) {
                rest = rest.substring(1);
            }
            return "file:///" + rest;
        }
        return url;
    }

    /**
     * Return the parent directory of a path string, with trailing slash.
     * Returns null if no slash is found.
     */
    public static String parentDir(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(0, lastSlash + 1) : null;
    }
}
