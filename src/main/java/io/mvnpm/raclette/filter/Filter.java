package io.mvnpm.raclette.filter;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import io.mvnpm.raclette.types.Uri;

/**
 * Filters URIs based on include/exclude patterns, IP addresses, and schemes.
 * Translated from lychee's filter/mod.rs.
 *
 * Filtering logic:
 * 1. Mail, tel, IP, and host checks first
 * 2. If includes is set and matches, URI is explicitly included (takes precedence)
 * 3. False positives (W3C schemas, etc.) are excluded unless explicitly included
 * 4. If excludes matches, URI is excluded
 */
public class Filter {

    /**
     * Pre-defined false positive patterns (W3C schemas, etc.)
     */
    private static final List<Pattern> FALSE_POSITIVE_PATTERNS = List.of(
            Pattern.compile("^https?://schemas\\.openxmlformats\\.org"),
            Pattern.compile("^https?://schemas\\.microsoft\\.com"),
            Pattern.compile("^https?://schemas\\.zune\\.net"),
            Pattern.compile("^https?://www\\.w3\\.org/1999/xhtml"),
            Pattern.compile("^https?://www\\.w3\\.org/1999/xlink"),
            Pattern.compile("^https?://www\\.w3\\.org/2000/svg"),
            Pattern.compile("^https?://www\\.w3\\.org/2001/XMLSchema-instance"),
            Pattern.compile("^https?://ogp\\.me/ns#"),
            Pattern.compile("^https?://(.*)/xmlrpc\\.php$"));

    private final List<Pattern> includes;
    private final List<Pattern> excludes;
    private final boolean includeMail;
    private final boolean excludePrivateIps;
    private final boolean excludeLinkLocalIps;
    private final boolean excludeLoopbackIps;

    /**
     * Default filter: excludes mail, no IP filtering, no regex patterns.
     */
    public static Filter defaults() {
        return new Filter(List.of(), List.of(), false, false, false, false);
    }

    public Filter(List<Pattern> includes, List<Pattern> excludes,
            boolean includeMail, boolean excludePrivateIps,
            boolean excludeLinkLocalIps, boolean excludeLoopbackIps) {
        this.includes = includes;
        this.excludes = excludes;
        this.includeMail = includeMail;
        this.excludePrivateIps = excludePrivateIps;
        this.excludeLinkLocalIps = excludeLinkLocalIps;
        this.excludeLoopbackIps = excludeLoopbackIps;
    }

    /**
     * Check if a URI should be excluded from checking.
     */
    public boolean isExcluded(Uri uri) {
        // 1. Check mail exclusion
        if (uri.isMail() && !includeMail) {
            return true;
        }

        // 2. Check tel exclusion
        if (uri.isTel()) {
            return true;
        }

        // 3. Check IP exclusion
        // Cache domain once to avoid repeated URI.create() calls
        if (excludeLoopbackIps || excludePrivateIps || excludeLinkLocalIps) {
            String domain = uri.domain();
            if (excludeLoopbackIps && isLoopbackOrLocalhost(domain, uri)) {
                return true;
            }
            if (excludePrivateIps && uri.isPrivate()) {
                return true;
            }
            if (excludeLinkLocalIps && uri.isLinkLocal()) {
                return true;
            }
        }

        String input = uri.url();

        // 4. If includes are set and match, URI is explicitly included
        boolean includesEmpty = includes.isEmpty();
        boolean excludesEmpty = excludes.isEmpty();

        if (includesEmpty) {
            if (excludesEmpty) {
                // Both empty: presumably included unless false positive
                return isFalsePositive(input);
            }
        } else if (isIncludesMatch(input)) {
            // Explicitly included (takes precedence over excludes)
            return false;
        }

        // 5. Check false positives, empty excludes, and exclude patterns
        if (isFalsePositive(input) || excludesEmpty || isExcludesMatch(input)) {
            return true;
        }

        return false;
    }

    private boolean isLoopbackOrLocalhost(String domain, Uri uri) {
        if ("localhost".equals(domain)) {
            return true;
        }
        return uri.isLoopback();
    }

    private static boolean isFalsePositive(String input) {
        for (Pattern p : FALSE_POSITIVE_PATTERNS) {
            if (p.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isIncludesMatch(String input) {
        for (Pattern p : includes) {
            if (p.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcludesMatch(String input) {
        for (Pattern p : excludes) {
            if (p.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    public static FilterBuilder builder() {
        return new FilterBuilder();
    }

    public static class FilterBuilder {

        private List<Pattern> includes = List.of();
        private List<Pattern> excludes = List.of();
        private boolean includeMail = false;
        private boolean excludePrivateIps = false;
        private boolean excludeLinkLocalIps = false;
        private boolean excludeLoopbackIps = false;

        public FilterBuilder includes(String... patterns) {
            this.includes = Arrays.stream(patterns)
                    .map(Pattern::compile)
                    .toList();
            return this;
        }

        public FilterBuilder excludes(String... patterns) {
            this.excludes = Arrays.stream(patterns)
                    .map(Pattern::compile)
                    .toList();
            return this;
        }

        public FilterBuilder includeMail(boolean includeMail) {
            this.includeMail = includeMail;
            return this;
        }

        public FilterBuilder excludePrivateIps(boolean excludePrivateIps) {
            this.excludePrivateIps = excludePrivateIps;
            return this;
        }

        public FilterBuilder excludeLinkLocalIps(boolean excludeLinkLocalIps) {
            this.excludeLinkLocalIps = excludeLinkLocalIps;
            return this;
        }

        public FilterBuilder excludeLoopbackIps(boolean excludeLoopbackIps) {
            this.excludeLoopbackIps = excludeLoopbackIps;
            return this;
        }

        public Filter build() {
            return new Filter(includes, excludes, includeMail, excludePrivateIps, excludeLinkLocalIps,
                    excludeLoopbackIps);
        }
    }
}
