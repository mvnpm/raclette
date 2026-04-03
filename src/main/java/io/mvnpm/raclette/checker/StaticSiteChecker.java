package io.mvnpm.raclette.checker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mvnpm.raclette.Raclette;
import io.mvnpm.raclette.collector.Collector;
import io.mvnpm.raclette.collector.Input;
import io.mvnpm.raclette.ratelimit.RateLimitConfig;
import io.mvnpm.raclette.types.ErrorKind;
import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;
import io.mvnpm.raclette.types.UrlUtils;

/**
 * High-level API for checking links in a generated static site directory.
 * Handles localhost URL rewriting, basePath stripping, parallel execution,
 * and sensible SSG defaults.
 *
 * <pre>
 * // Zero-config: check a generated site directory
 * Map&lt;Uri, Status&gt; broken = StaticSiteChecker.check(Path.of("target/site"));
 *
 * // Full builder:
 * Map&lt;Uri, Status&gt; broken = StaticSiteChecker.builder()
 *         .path(Path.of("target/site"))
 *         .basePath("/my-project/")
 *         .checkRemoteLinks(true)
 *         .includeFragments(true)
 *         .build()
 *         .check();
 * </pre>
 */
public class StaticSiteChecker implements AutoCloseable {

    static final Pattern LOCALHOST_PATTERN = Pattern.compile(
            "^https?://(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d+)?");

    private final Path path;
    private final String basePath;
    private final boolean rewriteLocalUrlsToFile;
    private final Collector collector;
    private final Raclette raclette;
    private final boolean checkRemoteLinks;
    private final boolean sequential;

    private StaticSiteChecker(Path path, String basePath, boolean rewriteLocalUrlsToFile,
            Collector collector, Raclette raclette, boolean checkRemoteLinks, boolean sequential) {
        this.path = path.toAbsolutePath().normalize();
        this.basePath = basePath;
        this.rewriteLocalUrlsToFile = rewriteLocalUrlsToFile;
        this.collector = collector;
        this.raclette = raclette;
        this.checkRemoteLinks = checkRemoteLinks;
        this.sequential = sequential;
    }

    /**
     * Check a site directory with zero config. Returns only broken links.
     */
    public static Map<Uri, Status> check(Path path) {
        try (StaticSiteChecker checker = builder().path(path).build()) {
            return checker.check();
        }
    }

    /**
     * Check all links and return only broken ones.
     */
    public Map<Uri, Status> check() {
        Map<Uri, Status> all = checkAll();
        Map<Uri, Status> broken = new LinkedHashMap<>();
        for (var entry : all.entrySet()) {
            Status status = entry.getValue();
            if (!status.isSuccess() && !status.isExcluded() && !status.isUnsupported()) {
                broken.put(entry.getKey(), status);
            }
        }
        return broken;
    }

    /**
     * Check all links and return all results (both successes and failures).
     */
    public Map<Uri, Status> checkAll() {
        Set<Uri> links = collector.collectLinks(Set.of(new Input.FsPath(path)));

        // Filter: only FILE uris unless checkRemoteLinks is enabled
        Map<Uri, Uri> toCheck = new LinkedHashMap<>();
        Map<Uri, Status> results = new LinkedHashMap<>();
        for (Uri uri : links) {
            Uri rewritten = rewriteUri(uri);
            if (checkRemoteLinks || rewritten.kind() == Uri.UriKind.FILE) {
                toCheck.put(uri, rewritten);
            }
        }

        if (sequential) {
            results.putAll(checkSequentially(toCheck));
        } else {
            results.putAll(checkInParallel(toCheck));
        }
        return results;
    }

    private Map<Uri, Status> checkSequentially(Map<Uri, Uri> toCheck) {
        Map<Uri, Status> results = new LinkedHashMap<>();
        for (var entry : toCheck.entrySet()) {
            results.put(entry.getKey(), raclette.check(entry.getValue()));
        }
        return results;
    }

    private Map<Uri, Status> checkInParallel(Map<Uri, Uri> toCheck) {
        Map<Uri, Status> results = new LinkedHashMap<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<Uri, Future<Status>> futures = new LinkedHashMap<>();
            for (var entry : toCheck.entrySet()) {
                Uri rewritten = entry.getValue();
                futures.put(entry.getKey(), executor.submit(() -> raclette.check(rewritten)));
            }
            for (var entry : futures.entrySet()) {
                try {
                    results.put(entry.getKey(), entry.getValue().get());
                } catch (ExecutionException e) {
                    results.put(entry.getKey(),
                            Status.error(new ErrorKind.NetworkError(e.getCause().getMessage())));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.put(entry.getKey(),
                            Status.error(new ErrorKind.NetworkError("Interrupted")));
                }
            }
        }
        return results;
    }

    /**
     * Rewrite a URI for static site checking.
     * Step 1: rewrite localhost HTTP URLs to file paths.
     * Step 2: strip basePath prefix from file URIs.
     */
    Uri rewriteUri(Uri uri) {
        if (rewriteLocalUrlsToFile && uri.kind() == Uri.UriKind.HTTP) {
            uri = rewriteLocalUrlsToFileUri(uri);
        }
        if (uri.kind() == Uri.UriKind.FILE && basePath != null && !basePath.equals("/")) {
            uri = stripBasePath(uri);
        }
        return uri;
    }

    /**
     * Rewrite localhost/127.0.0.1/[::1] HTTP URLs to file:// URIs resolved against the site root.
     * Strips query strings, preserves fragments.
     */
    Uri rewriteLocalUrlsToFileUri(Uri uri) {
        if (uri.kind() != Uri.UriKind.HTTP) {
            return uri;
        }
        String url = uri.url();
        Matcher m = LOCALHOST_PATTERN.matcher(url);
        if (!m.find()) {
            return uri;
        }
        String rest = url.substring(m.end());

        // Separate fragment
        String[] parts = UrlUtils.splitFragment(rest);
        rest = parts[0];
        String fragment = parts[1] != null ? "#" + parts[1] : null;

        // Strip query string
        rest = UrlUtils.stripQueryAndFragment(rest);

        // Strip basePath prefix
        if (basePath != null && !basePath.equals("/")) {
            String prefix = UrlUtils.normalizePathPrefix(basePath);
            if (rest.startsWith(prefix)) {
                rest = "/" + rest.substring(prefix.length());
            }
        }

        // Resolve against site root
        String pathPart = rest.startsWith("/") ? rest.substring(1) : rest;
        Path resolved = path.resolve(pathPart);
        String fileUri = resolved.toUri().toString();

        // Preserve fragment
        if (fragment != null) {
            fileUri += fragment;
        }
        return Uri.file(fileUri);
    }

    /**
     * Strip basePath prefix from file URIs that are under the site root.
     * E.g. with basePath="/raclette/" and site root "/abs/site/":
     * file:///abs/site/raclette/docs/foo → file:///abs/site/docs/foo
     */
    Uri stripBasePath(Uri uri) {
        String siteRootUri = path.toUri().toString();
        String url = uri.url();
        if (!url.startsWith(siteRootUri)) {
            return uri;
        }

        String rest = url.substring(siteRootUri.length());
        // normalizePathPrefix adds leading /, strip it since rest is relative to siteRootUri
        String prefix = UrlUtils.normalizePathPrefix(basePath).substring(1);

        if (rest.startsWith(prefix)) {
            String stripped = siteRootUri + rest.substring(prefix.length());
            return Uri.file(stripped);
        }
        return uri;
    }

    @Override
    public void close() {
        collector.close();
        raclette.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Path path;
        private String basePath = "/";
        private boolean includeVerbatim = false;
        private boolean skipHidden = true;
        private List<String> fallbackExtensions = List.of("html");
        private List<String> indexFiles = List.of("index.html");
        private boolean includeFragments = false;
        private boolean rewriteLocalUrlsToFile = true;
        private boolean checkRemoteLinks = false;
        private boolean sequential = false;

        // Raclette.Builder options (delegated)
        private final Raclette.Builder racletteBuilder = Raclette.builder();

        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder includeVerbatim(boolean includeVerbatim) {
            this.includeVerbatim = includeVerbatim;
            return this;
        }

        public Builder skipHidden(boolean skipHidden) {
            this.skipHidden = skipHidden;
            return this;
        }

        public Builder fallbackExtensions(List<String> fallbackExtensions) {
            this.fallbackExtensions = fallbackExtensions;
            return this;
        }

        public Builder indexFiles(List<String> indexFiles) {
            this.indexFiles = indexFiles;
            return this;
        }

        public Builder includeFragments(boolean includeFragments) {
            this.includeFragments = includeFragments;
            return this;
        }

        public Builder rewriteLocalUrlsToFile(boolean rewriteLocalUrlsToFile) {
            this.rewriteLocalUrlsToFile = rewriteLocalUrlsToFile;
            return this;
        }

        public Builder checkRemoteLinks(boolean checkRemoteLinks) {
            this.checkRemoteLinks = checkRemoteLinks;
            return this;
        }

        public Builder sequential(boolean sequential) {
            this.sequential = sequential;
            return this;
        }

        // Delegated filter options

        public Builder includes(String... patterns) {
            racletteBuilder.includes(patterns);
            return this;
        }

        public Builder excludes(String... patterns) {
            racletteBuilder.excludes(patterns);
            return this;
        }

        public Builder excludeAllPrivate(boolean exclude) {
            racletteBuilder.excludeAllPrivate(exclude);
            return this;
        }

        public Builder excludePrivateIps(boolean exclude) {
            racletteBuilder.excludePrivateIps(exclude);
            return this;
        }

        public Builder excludeLoopbackIps(boolean exclude) {
            racletteBuilder.excludeLoopbackIps(exclude);
            return this;
        }

        public Builder excludeLinkLocalIps(boolean exclude) {
            racletteBuilder.excludeLinkLocalIps(exclude);
            return this;
        }

        public Builder includeMail(boolean include) {
            racletteBuilder.includeMail(include);
            return this;
        }

        // Delegated HTTP options

        public Builder timeout(Duration timeout) {
            racletteBuilder.timeout(timeout);
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            racletteBuilder.maxRetries(maxRetries);
            return this;
        }

        public Builder userAgent(String userAgent) {
            racletteBuilder.userAgent(userAgent);
            return this;
        }

        public Builder maxRedirects(int maxRedirects) {
            racletteBuilder.maxRedirects(maxRedirects);
            return this;
        }

        public Builder customHeaders(Map<String, String> headers) {
            racletteBuilder.customHeaders(headers);
            return this;
        }

        public Builder allowInsecure(boolean allowInsecure) {
            racletteBuilder.allowInsecure(allowInsecure);
            return this;
        }

        public Builder requireHttps(boolean requireHttps) {
            racletteBuilder.requireHttps(requireHttps);
            return this;
        }

        public Builder retryWaitTime(Duration retryWaitTime) {
            racletteBuilder.retryWaitTime(retryWaitTime);
            return this;
        }

        public Builder rateLimitConfig(RateLimitConfig rateLimitConfig) {
            racletteBuilder.rateLimitConfig(rateLimitConfig);
            return this;
        }

        public StaticSiteChecker build() {
            if (path == null) {
                throw new IllegalArgumentException("path is required");
            }

            Path absPath = path.toAbsolutePath().normalize();

            Collector collector = Collector.builder()
                    .base(absPath.toUri().toString())
                    .includeVerbatim(includeVerbatim)
                    .skipHidden(skipHidden)
                    .build();

            racletteBuilder.fallbackExtensions(fallbackExtensions);
            racletteBuilder.indexFiles(indexFiles);
            racletteBuilder.includeFragments(includeFragments);

            Raclette raclette = racletteBuilder.build();

            return new StaticSiteChecker(absPath, basePath, rewriteLocalUrlsToFile, collector, raclette,
                    checkRemoteLinks, sequential);
        }
    }
}
