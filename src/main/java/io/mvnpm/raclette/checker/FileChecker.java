package io.mvnpm.raclette.checker;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.mvnpm.raclette.extract.HtmlExtractor;
import io.mvnpm.raclette.types.ErrorKind;
import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

/**
 * Checks local file URIs for existence and validity.
 * Supports fallback extensions, index file resolution, and fragment checking.
 *
 * Translated from lychee's checker/file.rs.
 */
public class FileChecker {

    private final List<String> fallbackExtensions;
    private final List<String> indexFiles;
    private final boolean includeFragments;
    private final HtmlExtractor htmlExtractor;
    // Cache fragment sets per file to avoid re-parsing (matches lychee's FragmentChecker cache)
    private final Map<Path, Set<String>> fragmentCache = new ConcurrentHashMap<>();

    /**
     * @param fallbackExtensions extensions to try if file not found (e.g. "html")
     * @param indexFiles index file names for directories (null = accept dirs as-is via "." convention)
     * @param includeFragments whether to validate #fragment references
     */
    public FileChecker(List<String> fallbackExtensions, List<String> indexFiles, boolean includeFragments) {
        this.fallbackExtensions = fallbackExtensions;
        this.indexFiles = indexFiles;
        this.includeFragments = includeFragments;
        this.htmlExtractor = new HtmlExtractor();
    }

    /**
     * Check a file URI.
     */
    public Status check(Uri uri) {
        Path path = uriToPath(uri);
        if (path == null) {
            return Status.error(new ErrorKind.InvalidFilePath(uri));
        }

        Path resolved = resolveLocalPath(path, uri);
        if (resolved == null) {
            return Status.error(new ErrorKind.InvalidFilePath(uri));
        }

        if (includeFragments) {
            return checkFragment(resolved, uri);
        }
        return Status.ok();
    }

    /**
     * Resolve a local path, applying fallback extensions and index files.
     * Returns the resolved path or null if not resolvable.
     */
    public Path resolveLocalPath(Path path, Uri uri) {
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                // Apply index files
                Path indexResolved = applyIndexFiles(path);
                if (indexResolved != null) {
                    // If index resolved to a directory, try fallback extensions
                    if (Files.isDirectory(indexResolved)) {
                        Path fallback = applyFallbackExtensions(indexResolved, uri);
                        return fallback != null ? fallback : indexResolved;
                    }
                    return indexResolved;
                }
                return null;
            }
            // Existing file
            return path;
        }

        // Path doesn't exist — try fallback extensions
        return applyFallbackExtensions(path, uri);
    }

    /**
     * Apply fallback extensions to find a file.
     * Returns the first existing file with a fallback extension, or null.
     */
    private Path applyFallbackExtensions(Path path, Uri uri) {
        if (Files.isRegularFile(path)) {
            return path;
        }
        for (String ext : fallbackExtensions) {
            Path withExt = path.resolveSibling(path.getFileName().toString() + "." + ext);
            if (Files.isRegularFile(withExt)) {
                return withExt;
            }
        }
        return null;
    }

    /**
     * Apply index file resolution for directories.
     *
     * If indexFiles is null, treat as disabled (accept dir as-is, via "." convention).
     * If indexFiles is an empty list, reject all directories.
     * Otherwise, try each index file name.
     */
    private Path applyIndexFiles(Path dirPath) {
        if (indexFiles == null) {
            // Disabled: accept directory as-is (the "." convention)
            return dirPath;
        }

        if (indexFiles.isEmpty()) {
            return null;
        }

        for (String filename : indexFiles) {
            if (filename.isEmpty()) {
                continue;
            }
            // Special "." means accept directory itself
            if (".".equals(filename)) {
                return dirPath;
            }

            Path candidate = dirPath.resolve(filename);
            // Only accept regular files, not directories
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Check if a fragment exists in the resolved file.
     */
    private Status checkFragment(Path path, Uri uri) {
        String fragment = extractFragment(uri);

        // No fragment or empty fragment — always OK
        if (fragment == null || fragment.isEmpty()) {
            return Status.ok();
        }

        // Directories have no fragments
        if (Files.isDirectory(path)) {
            return Status.error(new ErrorKind.InvalidFragment(uri));
        }

        // Read the file and extract fragments (cached per file, matches lychee FragmentChecker)
        try {
            Set<String> fragments = fragmentCache.computeIfAbsent(path, p -> {
                try {
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    return htmlExtractor.extractFragments(content);
                } catch (IOException e) {
                    return null;
                }
            });
            if (fragments == null) {
                // Lychee warns and returns Ok when file can't be read for fragment checking
                return Status.ok();
            }

            if (fragments.contains(fragment)) {
                return Status.ok();
            }
            // Also check with GitHub's user-content- prefix
            if (fragments.contains("user-content-" + fragment)) {
                return Status.ok();
            }
            return Status.error(new ErrorKind.InvalidFragment(uri));
        } catch (Exception e) {
            // Lychee warns and returns Ok when file can't be read for fragment checking
            // (file.rs:297-300). Do not return an error here.
            return Status.ok();
        }
    }

    /**
     * Extract the fragment from a URI string.
     */
    private static String extractFragment(Uri uri) {
        String url = uri.url();
        int hashIdx = url.indexOf('#');
        if (hashIdx < 0) {
            return null;
        }
        return url.substring(hashIdx + 1);
    }

    /**
     * Convert a file:// URI to a Path.
     */
    private static Path uriToPath(Uri uri) {
        try {
            URI javaUri = URI.create(uri.url());
            // Strip fragment for path resolution (Path.of doesn't handle fragments)
            if (javaUri.getFragment() != null) {
                javaUri = new URI(javaUri.getScheme(), javaUri.getAuthority(),
                        javaUri.getPath(), javaUri.getQuery(), null);
            }
            return Path.of(javaUri);
        } catch (Exception e) {
            return null;
        }
    }
}
