package io.mvnpm.raclette;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

/**
 * Main entry point for the Raclette link checker.
 *
 * Supports two primary use cases:
 * 1. Checking a generated site directory on disk (file:// URIs)
 * 2. Checking a served website (http(s):// URIs)
 *
 * Usage:
 *
 * <pre>
 * try (Raclette raclette = Raclette.builder().build()) {
 *     Status status = raclette.check("https://example.com");
 * }
 * </pre>
 */
public class Raclette implements AutoCloseable {

    private final Client client;

    private Raclette(Client client) {
        this.client = client;
    }

    /**
     * Check a single URI string.
     */
    public Status check(String url) {
        return client.check(url);
    }

    /**
     * Check a single parsed URI.
     */
    public Status check(Uri uri) {
        return client.check(uri);
    }

    /**
     * Check if a URI is excluded by the configured filter.
     */
    public boolean isExcluded(Uri uri) {
        return client.isExcluded(uri);
    }

    @Override
    public void close() {
        client.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for Raclette.
     */
    public static class Builder {

        private final Client.ClientBuilder clientBuilder = Client.builder();

        public Builder maxRetries(int maxRetries) {
            clientBuilder.maxRetries(maxRetries);
            return this;
        }

        public Builder retryWaitTime(Duration retryWaitTime) {
            clientBuilder.retryWaitTime(retryWaitTime);
            return this;
        }

        public Builder timeout(Duration timeout) {
            clientBuilder.timeout(timeout);
            return this;
        }

        public Builder maxRedirects(int maxRedirects) {
            clientBuilder.maxRedirects(maxRedirects);
            return this;
        }

        public Builder customHeaders(Map<String, String> headers) {
            clientBuilder.customHeaders(headers);
            return this;
        }

        public Builder userAgent(String userAgent) {
            clientBuilder.userAgent(userAgent);
            return this;
        }

        public Builder allowInsecure(boolean allowInsecure) {
            clientBuilder.allowInsecure(allowInsecure);
            return this;
        }

        public Builder requireHttps(boolean requireHttps) {
            clientBuilder.requireHttps(requireHttps);
            return this;
        }

        public Builder fallbackExtensions(List<String> fallbackExtensions) {
            clientBuilder.fallbackExtensions(fallbackExtensions);
            return this;
        }

        public Builder indexFiles(List<String> indexFiles) {
            clientBuilder.indexFiles(indexFiles);
            return this;
        }

        public Builder includeFragments(boolean includeFragments) {
            clientBuilder.includeFragments(includeFragments);
            return this;
        }

        public Builder excludePrivateIps(boolean exclude) {
            clientBuilder.excludePrivateIps(exclude);
            return this;
        }

        public Builder excludeLoopbackIps(boolean exclude) {
            clientBuilder.excludeLoopbackIps(exclude);
            return this;
        }

        public Builder excludeLinkLocalIps(boolean exclude) {
            clientBuilder.excludeLinkLocalIps(exclude);
            return this;
        }

        public Builder excludeAllPrivate(boolean exclude) {
            clientBuilder.excludeAllPrivate(exclude);
            return this;
        }

        public Builder includeMail(boolean include) {
            clientBuilder.includeMail(include);
            return this;
        }

        public Builder includes(String... patterns) {
            clientBuilder.includes(patterns);
            return this;
        }

        public Builder excludes(String... patterns) {
            clientBuilder.excludes(patterns);
            return this;
        }

        public Raclette build() {
            return new Raclette(clientBuilder.build());
        }
    }
}
