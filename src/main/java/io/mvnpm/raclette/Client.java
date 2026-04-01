package io.mvnpm.raclette;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.mvnpm.raclette.checker.FileChecker;
import io.mvnpm.raclette.checker.WebsiteChecker;
import io.mvnpm.raclette.filter.Filter;
import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

/**
 * Main link checker client. Dispatches to FileChecker or WebsiteChecker based on URI scheme.
 * Supports both local file checking (for generated site dirs) and HTTP checking (for served websites).
 *
 * Translated from lychee's client.rs.
 */
public class Client implements AutoCloseable {

    private final Filter filter;
    private final FileChecker fileChecker;
    private final WebsiteChecker websiteChecker;

    Client(Filter filter, FileChecker fileChecker, WebsiteChecker websiteChecker) {
        this.filter = filter;
        this.fileChecker = fileChecker;
        this.websiteChecker = websiteChecker;
    }

    /**
     * Check a single URI.
     * Routes to the appropriate checker based on scheme:
     * - file:// → FileChecker
     * - http(s):// → WebsiteChecker
     * - mailto:/tel: → excluded or filtered
     * - other → unsupported
     */
    public Status check(Uri uri) {
        if (isExcluded(uri)) {
            return Status.excluded("Filtered by exclusion rules");
        }

        return switch (uri.kind()) {
            case FILE -> fileChecker.check(uri);
            case HTTP -> websiteChecker.check(uri);
            case MAIL -> Status.excluded("Mail checking not supported");
            case TEL -> Status.excluded("Tel URIs are not checked");
            case UNSUPPORTED -> Status.unsupported(uri.url());
        };
    }

    /**
     * Check a URI string. Parses it first via Uri.tryFrom.
     */
    public Status check(String url) {
        Uri uri = Uri.tryFrom(url);
        if (uri == null) {
            return Status.unsupported(url);
        }
        return check(uri);
    }

    /**
     * Check if a URI is excluded by the filter.
     * Matches lychee's Client::is_excluded.
     */
    public boolean isExcluded(Uri uri) {
        // tel: is always excluded (lychee client.rs:548)
        if (uri.isTel()) {
            return true;
        }
        return filter.isExcluded(uri);
    }

    @Override
    public void close() {
        websiteChecker.close();
    }

    /**
     * Create a new builder.
     */
    public static ClientBuilder builder() {
        return new ClientBuilder();
    }

    /**
     * Fluent builder for Client.
     * Translated from lychee's ClientBuilder.
     */
    public static class ClientBuilder {

        private int maxRetries = 3;
        private Duration retryWaitTime = Duration.ofSeconds(1);
        private Duration timeout = Duration.ofSeconds(20);
        private int maxRedirects = 10;
        private Map<String, String> customHeaders = new LinkedHashMap<>();
        private String userAgent = "raclette/0.1";
        private boolean allowInsecure = false;
        private boolean requireHttps = false;
        private List<String> fallbackExtensions = List.of();
        private List<String> indexFiles = null;
        private boolean includeFragments = false;

        // Filter options
        private Filter.FilterBuilder filterBuilder = Filter.builder();

        public ClientBuilder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ClientBuilder retryWaitTime(Duration retryWaitTime) {
            this.retryWaitTime = retryWaitTime;
            return this;
        }

        public ClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public ClientBuilder maxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }

        public ClientBuilder customHeaders(Map<String, String> headers) {
            this.customHeaders = headers;
            return this;
        }

        public ClientBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public ClientBuilder allowInsecure(boolean allowInsecure) {
            this.allowInsecure = allowInsecure;
            return this;
        }

        public ClientBuilder requireHttps(boolean requireHttps) {
            this.requireHttps = requireHttps;
            return this;
        }

        public ClientBuilder fallbackExtensions(List<String> fallbackExtensions) {
            this.fallbackExtensions = fallbackExtensions;
            return this;
        }

        public ClientBuilder indexFiles(List<String> indexFiles) {
            this.indexFiles = indexFiles;
            return this;
        }

        public ClientBuilder includeFragments(boolean includeFragments) {
            this.includeFragments = includeFragments;
            return this;
        }

        public ClientBuilder excludePrivateIps(boolean exclude) {
            filterBuilder.excludePrivateIps(exclude);
            return this;
        }

        public ClientBuilder excludeLoopbackIps(boolean exclude) {
            filterBuilder.excludeLoopbackIps(exclude);
            return this;
        }

        public ClientBuilder excludeLinkLocalIps(boolean exclude) {
            filterBuilder.excludeLinkLocalIps(exclude);
            return this;
        }

        public ClientBuilder excludeAllPrivate(boolean exclude) {
            filterBuilder.excludePrivateIps(exclude);
            filterBuilder.excludeLoopbackIps(exclude);
            filterBuilder.excludeLinkLocalIps(exclude);
            return this;
        }

        public ClientBuilder includeMail(boolean include) {
            filterBuilder.includeMail(include);
            return this;
        }

        public ClientBuilder includes(String... patterns) {
            filterBuilder.includes(patterns);
            return this;
        }

        public ClientBuilder excludes(String... patterns) {
            filterBuilder.excludes(patterns);
            return this;
        }

        public Client build() {
            Filter filter = filterBuilder.build();
            FileChecker fileChecker = new FileChecker(fallbackExtensions, indexFiles, includeFragments);
            WebsiteChecker websiteChecker = new WebsiteChecker(maxRetries, retryWaitTime,
                    timeout, maxRedirects, customHeaders, userAgent, allowInsecure, requireHttps);
            return new Client(filter, fileChecker, websiteChecker);
        }
    }
}
