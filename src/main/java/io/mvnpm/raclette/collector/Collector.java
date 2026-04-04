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
import io.mvnpm.raclette.types.BaseInfo;
import io.mvnpm.raclette.types.LinkResolutionException;
import io.mvnpm.raclette.types.RawUri;
import io.mvnpm.raclette.types.Uri;

/**
 * Collects links from various input sources (files, globs, remote URLs, strings).
 * Extracts raw links and pairs each with a BaseInfo for later resolution.
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
     * Collect raw links paired with their resolution context.
     * Callers resolve via {@code link.baseInfo().parseUrlText(link.rawUri().text())}.
     */
    public List<CollectedLink> collectRawLinks(Set<Input> inputs) {
        List<CollectedLink> result = new ArrayList<>();
        for (Input input : inputs) {
            result.addAll(collectRawFromInput(input));
        }
        return result;
    }

    /**
     * Convenience: collect, resolve, and deduplicate in one step.
     * No clamping is applied; callers needing clamping should use {@link #collectRawLinks}.
     */
    public Set<Uri> collectLinks(Set<Input> inputs) {
        Set<Uri> result = new HashSet<>();
        for (CollectedLink link : collectRawLinks(inputs)) {
            try {
                Uri uri = link.baseInfo().parseUrlText(link.rawUri().text());
                if (uri != null) {
                    result.add(uri);
                }
            } catch (LinkResolutionException | IllegalArgumentException e) {
                // Skip unresolvable or malformed links
            }
        }
        return result;
    }

    private List<CollectedLink> collectRawFromInput(Input input) {
        return switch (input) {
            case Input.StringContent s -> collectRawFromString(s.content(), baseInfoFromBase());
            case Input.RemoteUrl r -> collectRawFromRemoteUrl(r.url());
            case Input.FsPath f -> collectRawFromFsPath(f.path());
            case Input.FsGlob g -> collectRawFromGlob(g.pattern());
        };
    }

    private List<CollectedLink> collectRawFromString(String content, BaseInfo baseInfo) {
        List<RawUri> rawUris = extractor.extractHtmlRaw(content);
        return rawUris.stream()
                .map(raw -> new CollectedLink(raw, baseInfo))
                .toList();
    }

    private List<CollectedLink> collectRawFromRemoteUrl(String url) {
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
                return collectRawFromString(response.body(), BaseInfo.fromSourceUrl(url));
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return List.of();
    }

    private List<CollectedLink> collectRawFromFsPath(Path path) {
        List<CollectedLink> links = new ArrayList<>();
        if (Files.isRegularFile(path)) {
            links.addAll(collectRawFromFile(path));
        } else if (Files.isDirectory(path)) {
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (skipHidden && isHidden(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (isHtmlFile(file) || includeVerbatim) {
                            links.addAll(collectRawFromFile(file));
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
        return links;
    }

    private List<CollectedLink> collectRawFromGlob(String pattern) {
        List<CollectedLink> links = new ArrayList<>();
        Path patternPath = Path.of(pattern);
        Path baseDir = findGlobBaseDir(patternPath);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        if (Files.isDirectory(baseDir)) {
            try {
                Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (matcher.matches(file)) {
                            links.addAll(collectRawFromFile(file));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                // Skip unreadable directories
            }
        }
        return links;
    }

    private List<CollectedLink> collectRawFromFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            BaseInfo baseInfo = baseInfoForFile(file);

            // If the page has <base href>, use it as the resolution base
            String baseHref = extractor.extractBaseHref(content);
            if (baseHref != null) {
                baseInfo = baseInfoFromBaseHref(baseHref, baseInfo);
            }

            return collectRawFromString(content, baseInfo);
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Create a BaseInfo from a {@code <base href>} value.
     * If the href is absolute, use it directly.
     * If relative, resolve it against the file's own base first (per HTML spec).
     */
    private BaseInfo baseInfoFromBaseHref(String baseHref, BaseInfo fileBase) {
        // Resolve the base href itself (it may be relative to the document URL)
        Uri resolved;
        try {
            resolved = fileBase.parseUrlText(baseHref);
        } catch (LinkResolutionException | IllegalArgumentException e) {
            return fileBase;
        }
        if (resolved == null) {
            return fileBase;
        }
        String url = resolved.url();
        if (url.startsWith("file:///")) {
            // For file:// base hrefs, use Full with file:/// as origin.
            // Root-relative links resolve against filesystem root (SSC clamps later).
            String path = url.substring("file:///".length());
            return new BaseInfo.Full("file:///", path);
        }
        return BaseInfo.fromSourceUrl(url);
    }

    /**
     * Create BaseInfo for a file input.
     * With a global base: forFileWithRoot so both local-relative and root-relative links work.
     * Without: NoRoot from the file's parent dir (root-relative links will correctly error).
     */
    private BaseInfo baseInfoForFile(Path file) {
        if (base != null) {
            Path rootPath = rootPathFromBase();
            return BaseInfo.forFileWithRoot(file, rootPath);
        }
        return BaseInfo.fromSourceUrl(file.getParent().toUri().toString());
    }

    /**
     * Create BaseInfo from the global base string.
     * Used for StringContent inputs.
     */
    private BaseInfo baseInfoFromBase() {
        if (base == null) {
            return BaseInfo.none();
        }
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return BaseInfo.fromSourceUrl(base);
        }
        if (base.startsWith("file://")) {
            return BaseInfo.fromPath(Path.of(URI.create(base).getPath()));
        }
        return BaseInfo.fromPath(Path.of(base));
    }

    private Path rootPathFromBase() {
        if (base.startsWith("file://")) {
            return Path.of(URI.create(base).getPath());
        }
        return Path.of(base);
    }

    // --- Utilities ---

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
