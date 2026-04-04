package io.mvnpm.raclette.types;

import java.net.URI;
import java.nio.file.Path;

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
     * Tries URI.create() first for proper percent-decoding, falls back to manual
     * prefix stripping when the URL contains already-decoded characters (spaces,
     * apostrophes, Unicode) that URI.create() can't parse.
     */
    public static String fileUrlToPath(String url) {
        if (url.startsWith("file:")) {
            try {
                return URI.create(url).getPath();
            } catch (IllegalArgumentException e) {
                String path = url.substring("file:".length());
                while (path.startsWith("//")) {
                    path = path.substring(1);
                }
                return path;
            }
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

    /**
     * Resolve a path within a root directory, like a web server document root.
     * If the path escapes the root via ../, the non-.. suffix is resolved back within root.
     */
    public static Path resolveWithinRoot(Path filePath, Path root) {
        if (filePath.startsWith(root)) {
            return filePath;
        }
        // Find the first non-".." segment in the relative path
        Path relative = root.relativize(filePath);
        int firstReal = 0;
        while (firstReal < relative.getNameCount()
                && relative.getName(firstReal).toString().equals("..")) {
            firstReal++;
        }
        if (firstReal >= relative.getNameCount()) {
            return root;
        }
        Path safe = root.resolve(relative.subpath(firstReal, relative.getNameCount())).normalize();
        return safe.startsWith(root) ? safe : root;
    }

    /**
     * Clamp a file URL within a root directory, preserving query and fragment.
     */
    public static String clampFileUrl(String url, String base) {
        String stripped = stripQueryAndFragment(url);
        String suffix = url.substring(stripped.length());

        String pathStr = fileUrlToPath(stripped);
        String rootStr = fileUrlToPath(base);
        Path safe = resolveWithinRoot(Path.of(pathStr).normalize(), Path.of(rootStr).normalize());
        return pathToFileUrl(safe.toString()) + suffix;
    }
}
