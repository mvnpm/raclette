package io.mvnpm.raclette.collector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.mvnpm.raclette.extract.Extractor;
import io.mvnpm.raclette.types.RawUri;
import io.mvnpm.raclette.types.Uri;

/**
 * Collects links from various input sources (files, globs, remote URLs, strings).
 * Extracts links using HtmlExtractor and resolves relative URLs against a base.
 *
 * Translated from lychee's collector.rs.
 */
public class Collector implements AutoCloseable {

    private final String base;
    private final boolean includeVerbatim;
    private final String userAgent;
    private final boolean skipHidden;
    private final HttpClient httpClient;
    private final Extractor extractor;

    private Collector(String base, boolean includeVerbatim, String userAgent, boolean skipHidden) {
        this.base = base;
        this.includeVerbatim = includeVerbatim;
        this.userAgent = userAgent;
        this.skipHidden = skipHidden;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.extractor = new Extractor(includeVerbatim);
    }

    /**
     * Collect all links from the given inputs.
     * Extracts links from each input source, resolves relative URLs, and returns unique URIs.
     */
    public Set<Uri> collectLinks(Set<Input> inputs) {
        Set<Uri> result = new HashSet<>();
        for (Input input : inputs) {
            result.addAll(collectFromInput(input));
        }
        return result;
    }

    private Set<Uri> collectFromInput(Input input) {
        return switch (input) {
            case Input.StringContent s -> collectFromString(s.content(), base);
            case Input.RemoteUrl r -> collectFromRemoteUrl(r.url());
            case Input.FsPath f -> collectFromFsPath(f.path());
            case Input.FsGlob g -> collectFromGlob(g.pattern());
        };
    }

    /**
     * Extract links from a string, resolve relative URLs against the given base.
     */
    private Set<Uri> collectFromString(String content, String resolveBase) {
        List<RawUri> rawUris = extractor.extractHtmlRaw(content);
        Set<Uri> uris = new HashSet<>();
        // Pre-compute base URI once for all links on this page
        URI baseUri = null;
        if (resolveBase != null && !resolveBase.isBlank()) {
            try {
                baseUri = toBaseUri(resolveBase);
            } catch (Exception e) {
                // Invalid base — relative links won't resolve
            }
        }
        for (RawUri raw : rawUris) {
            Uri uri = resolveUri(raw.text(), baseUri);
            if (uri != null) {
                uris.add(uri);
            }
        }
        return uris;
    }

    /**
     * Fetch a remote URL, extract links, using the remote URL as base for relative links.
     * Matches lychee's behavior: remote URL auto-becomes base for its own relative links.
     */
    private Set<Uri> collectFromRemoteUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Use the remote URL as base for relative link resolution
                return collectFromString(response.body(), url);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return Set.of();
    }

    /**
     * Walk a directory recursively and extract links from HTML files.
     */
    private Set<Uri> collectFromFsPath(Path path) {
        Set<Uri> uris = new HashSet<>();
        if (Files.isRegularFile(path)) {
            uris.addAll(collectFromFile(path));
        } else if (Files.isDirectory(path)) {
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (skipHidden && isHidden(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        // Process HTML files always; non-HTML files only when includeVerbatim
                        // (matches lychee: all supported file types are walked)
                        if (isHtmlFile(file) || includeVerbatim) {
                            uris.addAll(collectFromFile(file));
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (skipHidden && isHidden(dir) && !dir.equals(path)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                // Skip unreadable directories
            }
        }
        return uris;
    }

    /**
     * Match files by glob pattern and extract links from HTML files.
     */
    private Set<Uri> collectFromGlob(String pattern) {
        Set<Uri> uris = new HashSet<>();

        // Determine the base directory from the pattern (everything before the first glob char)
        Path patternPath = Path.of(pattern);
        Path baseDir = findGlobBaseDir(patternPath);

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        if (Files.isDirectory(baseDir)) {
            try {
                Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (matcher.matches(file)) {
                            uris.addAll(collectFromFile(file));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                // Skip unreadable directories
            }
        }
        return uris;
    }

    /**
     * Extract links from a single file.
     */
    private Set<Uri> collectFromFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            // HTML files: parse as HTML to extract links from attributes
            // Non-HTML files: parse as HTML too (JSoup treats plaintext as text nodes,
            // and extractPlainTextUrls finds URLs/emails when includeVerbatim=true)
            return collectFromString(content, base);
        } catch (IOException e) {
            return Set.of();
        }
    }

    /**
     * Resolve a raw URI string against a pre-parsed base URI.
     * Matches lychee's BaseInfo.parse_url_text / resolve_relative_link logic.
     */
    private Uri resolveUri(String text, URI baseUri) {
        if (text == null || text.isBlank()) {
            return null;
        }

        // Already absolute?
        if (isAbsoluteUri(text)) {
            return Uri.tryFrom(text);
        }

        // Relative URI — need a base to resolve
        if (baseUri == null) {
            return null;
        }

        try {
            URI resolved = resolveRelative(baseUri, text);
            return Uri.tryFrom(normalizeFileUri(resolved.toString()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert a base string to a URI. Handles both URL bases and file path bases.
     * Matches lychee's BaseInfo::try_from logic.
     */
    private static URI toBaseUri(String base) {
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return URI.create(base);
        }
        if (base.startsWith("file://")) {
            return URI.create(base);
        }
        // Treat as a file path — convert to file:// URI
        // Ensure it ends with / so URI.resolve works for directory bases
        String filePath = base.startsWith("/") ? base : "/" + base;
        if (!filePath.endsWith("/")) {
            filePath += "/";
        }
        return URI.create("file://" + filePath);
    }

    /**
     * Resolve a relative URI against a base URI.
     * Handles root-relative, locally-relative, and parent traversal.
     * Matches lychee's BaseInfo::resolve_relative_link.
     */
    private static URI resolveRelative(URI baseUri, String relative) {
        // Ensure base path ends with / for proper resolution of locally-relative links
        // (URI.resolve replaces the last segment if base doesn't end with /)
        String basePath = baseUri.getPath();
        URI effectiveBase = baseUri;

        if (basePath != null && !basePath.endsWith("/") && !relative.startsWith("/")) {
            // For locally-relative links, we need the "directory" of the base
            int lastSlash = basePath.lastIndexOf('/');
            if (lastSlash >= 0) {
                String dirPath = basePath.substring(0, lastSlash + 1);
                try {
                    effectiveBase = new URI(baseUri.getScheme(), baseUri.getAuthority(),
                            dirPath, null, null);
                } catch (Exception e) {
                    effectiveBase = baseUri;
                }
            }
        }

        // For file:// bases with root-relative links, resolve relative to the base origin
        // (matches lychee: file:// origin.join("./something") for root-relative)
        if ("file".equals(baseUri.getScheme()) && relative.startsWith("/")) {
            try {
                // Resolve root-relative against the file base (not filesystem root)
                return effectiveBase.resolve("." + relative);
            } catch (Exception e) {
                // Fall through to normal resolution
            }
        }

        return effectiveBase.resolve(relative);
    }

    /**
     * Normalize file:/ URIs to file:/// form.
     */
    private static String normalizeFileUri(String uri) {
        if (uri.startsWith("file:/") && !uri.startsWith("file:///")) {
            return "file:///" + uri.substring("file:/".length());
        }
        return uri;
    }

    /**
     * Check if a URI string is absolute (has a scheme).
     */
    private static boolean isAbsoluteUri(String text) {
        // Common schemes
        if (text.startsWith("http://") || text.startsWith("https://")
                || text.startsWith("file://") || text.startsWith("mailto:")
                || text.startsWith("tel:") || text.startsWith("ftp://")) {
            return true;
        }
        // Generic scheme check: word followed by ://
        int colon = text.indexOf(':');
        if (colon > 0 && colon < 10) {
            for (int i = 0; i < colon; i++) {
                char c = text.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '+' && c != '-' && c != '.') {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Find the base directory for a glob pattern (the static prefix before any glob chars).
     */
    private static Path findGlobBaseDir(Path patternPath) {
        List<Path> parts = new ArrayList<>();
        for (Path part : patternPath) {
            String s = part.toString();
            if (s.contains("*") || s.contains("?") || s.contains("[") || s.contains("{")) {
                break;
            }
            parts.add(part);
        }
        if (parts.isEmpty()) {
            return Path.of(".");
        }
        Path result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = result.resolve(parts.get(i));
        }
        // If the original pattern is absolute, keep it absolute
        if (patternPath.isAbsolute()) {
            return patternPath.getRoot().resolve(result);
        }
        return result;
    }

    private static boolean isHtmlFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    private static boolean isHidden(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(".");
    }

    @Override
    public void close() {
        httpClient.close();
    }

    public static CollectorBuilder builder() {
        return new CollectorBuilder();
    }

    public static class CollectorBuilder {

        private String base;
        private boolean includeVerbatim = false;
        private String userAgent = "raclette/0.1";
        private boolean skipHidden = true;

        public CollectorBuilder base(String base) {
            this.base = base;
            return this;
        }

        public CollectorBuilder includeVerbatim(boolean includeVerbatim) {
            this.includeVerbatim = includeVerbatim;
            return this;
        }

        public CollectorBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public CollectorBuilder skipHidden(boolean skipHidden) {
            this.skipHidden = skipHidden;
            return this;
        }

        public Collector build() {
            return new Collector(base, includeVerbatim, userAgent, skipHidden);
        }
    }
}
