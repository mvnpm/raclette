package io.mvnpm.raclette.ratelimit;

import java.util.Locale;

import io.mvnpm.raclette.types.Uri;

/**
 * Identifies a host for rate limiting purposes.
 * Normalized to lowercase, port stripped.
 * Translated from lychee's ratelimit module.
 */
public record HostKey(String host) {

    /**
     * Extract the host key from a URI (lowercase, no port).
     * Returns null if the URI has no host (e.g. file://).
     */
    public static HostKey fromUri(Uri uri) {
        String domain = uri.domain();
        if (domain == null || domain.isEmpty()) {
            return null;
        }
        return new HostKey(domain.toLowerCase(Locale.ROOT));
    }
}
