package io.mvnpm.raclette.types;

import java.net.InetAddress;
import java.net.URI;
import java.util.Objects;

/**
 * A parsed URI with its kind.
 * Mirrors lychee's types/uri/valid.rs.
 */
public record Uri(String url, UriKind kind) implements Comparable<Uri> {

    public enum UriKind {
        HTTP,
        FILE,
        MAIL,
        TEL,
        UNSUPPORTED
    }

    public Uri {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }

    public static Uri tryFrom(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        if (input.startsWith("mailto:")) {
            return new Uri(input, UriKind.MAIL);
        }
        if (input.startsWith("tel:")) {
            return new Uri(input, UriKind.TEL);
        }
        if (input.startsWith("file://")) {
            return new Uri(input, UriKind.FILE);
        }
        if (input.startsWith("http://") || input.startsWith("https://")) {
            try {
                URI.create(input);
            } catch (Exception e) {
                return null;
            }
            return new Uri(input, UriKind.HTTP);
        }
        if (input.startsWith("ftp://") || input.startsWith("gopher://") || input.startsWith("slack://")) {
            return new Uri(input, UriKind.UNSUPPORTED);
        }
        // Any other valid URI with a scheme is treated as HTTP (website).
        // Lychee does not classify schemes in Uri — scheme filtering (e.g. rejecting data:, javascript:)
        // is handled by Filter, not here. See lychee client.rs:546-551.
        try {
            URI uri = URI.create(input);
            if (uri.getScheme() != null) {
                return new Uri(input, UriKind.HTTP);
            }
        } catch (Exception e) {
            // not a valid URI
        }
        return null;
    }

    public static Uri website(String url) {
        return new Uri(url, UriKind.HTTP);
    }

    public static Uri mail(String email) {
        String u = email.startsWith("mailto:") ? email : "mailto:" + email;
        return new Uri(u, UriKind.MAIL);
    }

    public boolean isMail() {
        return kind == UriKind.MAIL;
    }

    public boolean isTel() {
        return kind == UriKind.TEL;
    }

    /**
     * Extract the host/domain from the URL.
     */
    public String domain() {
        try {
            URI uri = URI.create(url);
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if the URI points to a loopback address (127.0.0.0/8 or ::1).
     * Only checks IP literals — does NOT resolve hostnames (matches lychee).
     */
    public boolean isLoopback() {
        InetAddress addr = parseIpLiteral();
        return addr != null && addr.isLoopbackAddress();
    }

    /**
     * Check if the URI points to a private IPv4 address (10/8, 172.16/12, 192.168/16).
     * Only checks IP literals — does NOT resolve hostnames (matches lychee).
     * Does NOT handle IPv4-mapped IPv6 addresses (same as lychee).
     */
    public boolean isPrivate() {
        String host = domain();
        if (host == null || isIpv4MappedIpv6(host)) {
            return false;
        }
        InetAddress addr = parseIpLiteral();
        return addr != null && addr.isSiteLocalAddress();
    }

    /**
     * Check if the URI points to a link-local address (169.254/16 or fe80::/10).
     * Only checks IP literals — does NOT resolve hostnames (matches lychee).
     * Does NOT handle IPv4-mapped IPv6 addresses (same as lychee).
     */
    public boolean isLinkLocal() {
        String host = domain();
        if (host == null || isIpv4MappedIpv6(host)) {
            return false;
        }
        InetAddress addr = parseIpLiteral();
        return addr != null && addr.isLinkLocalAddress();
    }

    /**
     * Parse the host as an IP address literal (no DNS resolution).
     * Returns null if the host is a hostname, not an IP literal.
     */
    private InetAddress parseIpLiteral() {
        String host = domain();
        if (host == null) {
            return null;
        }
        // Strip brackets for IPv6
        String cleaned = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        // Only parse if it looks like an IP literal (starts with digit or colon for IPv6)
        if (cleaned.isEmpty()) {
            return null;
        }
        char first = cleaned.charAt(0);
        if (!Character.isDigit(first) && first != ':') {
            return null; // Hostname, not an IP literal — skip DNS resolution
        }
        try {
            // InetAddress.getByName on an IP literal does NOT trigger DNS
            InetAddress addr = InetAddress.getByName(cleaned);
            return addr;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if this is an IPv4-mapped IPv6 address (::ffff:x.x.x.x).
     * Lychee explicitly does NOT exclude these.
     */
    private static boolean isIpv4MappedIpv6(String host) {
        String cleaned = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        return cleaned.toLowerCase().startsWith("::ffff:");
    }

    @Override
    public int compareTo(Uri other) {
        return this.url.compareTo(other.url);
    }

    @Override
    public String toString() {
        return url;
    }
}
