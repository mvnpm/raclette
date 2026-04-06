package io.mvnpm.raclette.types;

import java.net.URI;
import java.nio.file.Path;

import io.quarkiverse.tools.stringpaths.StringPaths;

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
     * Convert a file: URL string to a {@link Path}.
     * Single entry point for all file URI to Path conversion, cross-platform safe.
     * <p>
     * Uses {@code Path.of(URI)} for proper percent-decoding and platform handling.
     * Falls back to string-based parsing for URIs with already-decoded characters
     * (spaces, apostrophes, Unicode) that {@code URI.create()} can't parse.
     *
     * @return the filesystem Path, or null if the URL cannot be converted
     */
    public static Path fileUrlToPath(String url) {
        if (!url.startsWith("file:")) {
            return null;
        }
        String normalized = normalizeFileUrl(url);
        try {
            return Path.of(URI.create(normalized));
        } catch (Exception e) {
            try {
                return Path.of(fileUrlToPathString(url));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Convert a file: URL to a filesystem path string.
     * Tries URI parsing first for proper percent-decoding, falls back to manual
     * prefix stripping when the URL contains already-decoded characters.
     * Strips leading / before Windows drive letters (e.g., /C:/ to C:/).
     */
    public static String fileUrlToPathString(String url) {
        if (url.startsWith("file:")) {
            try {
                String path = URI.create(url).getPath();
                return stripDriveLetterSlash(path);
            } catch (IllegalArgumentException e) {
                String path = url.substring("file:".length());
                while (path.startsWith("//")) {
                    path = path.substring(1);
                }
                return stripDriveLetterSlash(path);
            }
        }
        return url;
    }

    /**
     * Strip leading / before a Windows drive letter (e.g., /C:/path to C:/path).
     * Safe to call on Unix paths (no-op since they don't match the pattern).
     */
    private static String stripDriveLetterSlash(String path) {
        if (path.length() >= 3 && path.charAt(0) == '/' && path.charAt(2) == ':') {
            return path.substring(1);
        }
        return path;
    }

    /**
     * Convert a filesystem path to a file: URL.
     * Normalizes backslashes to forward slashes for Windows compatibility.
     */
    public static String pathToFileUrl(String path) {
        path = StringPaths.toUnixPath(path);
        return path.startsWith("/") ? "file://" + path : "file:///" + path;
    }

    /**
     * Normalize file:/ URIs to file:/// form.
     */
    public static String normalizeFileUrl(String url) {
        if (url.startsWith("file:/") && !url.startsWith("file:///")) {
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

        Path filePath = fileUrlToPath(stripped);
        Path rootPath = fileUrlToPath(base);
        Path safe = resolveWithinRoot(filePath.normalize(), rootPath.normalize());
        return pathToFileUrl(safe.toString()) + suffix;
    }
}
