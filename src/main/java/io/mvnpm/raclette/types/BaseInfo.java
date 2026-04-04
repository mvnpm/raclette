package io.mvnpm.raclette.types;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * Resolution context for converting relative links into fully-qualified URIs.
 * There should be a 1:1 correspondence between each BaseInfo and its originating input source.
 * Mirrors lychee's types/base_info.rs.
 *
 * Three variants with increasing resolution capability:
 * - None: no base available, can only handle absolute URIs
 * - NoRoot: file:// base without root dir, can resolve local-relative but not root-relative
 * - Full: complete base with origin and path, can resolve all relative link types
 */
public sealed interface BaseInfo {

    /**
     * No base information available (e.g. stdin, data: URIs).
     * Can resolve no relative links; only fully-qualified links will be parsed successfully.
     */
    record None() implements BaseInfo {
    }

    /**
     * A file:// base without a known root directory.
     * Can resolve locally-relative links (e.g. "sibling.html", "../parent")
     * but NOT root-relative links (e.g. "/docs/page"), because we don't know where "/" maps to.
     */
    record NoRoot(String url) implements BaseInfo {
    }

    /**
     * A full base with origin and path. Can resolve all relative link types.
     * For HTTP: origin is the domain root (e.g. "https://a.com/"), path is the subpath.
     * For file:// with root: origin is the root dir, path is the file's relative location.
     * Invariant: URI.resolve(origin, path) should reconstruct the source URL.
     */
    record Full(String origin, String path) implements BaseInfo {
    }

    // --- Factory methods ---

    static BaseInfo none() {
        return new None();
    }

    /**
     * Construct BaseInfo from an input source URL.
     * file:// -> NoRoot (no root dir known), http(s) -> Full(origin, path), other -> None.
     * Mirrors lychee's BaseInfo::from_source_url.
     */
    static BaseInfo fromSourceUrl(String url) {
        if (url.startsWith("file://")) {
            return new NoRoot(url);
        }
        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                return new None();
            }
            URI originUri = new URI(uri.getScheme(), uri.getAuthority(), "/", null, null);
            String origin = originUri.toString();
            // Treat empty path as "/" so relativize works correctly
            String rawPath = uri.getPath();
            if (rawPath == null || rawPath.isEmpty()) {
                uri = new URI(uri.getScheme(), uri.getAuthority(), "/", uri.getQuery(), uri.getFragment());
            }
            String path = originUri.relativize(uri).toString();
            return new Full(origin, path);
        } catch (URISyntaxException e) {
            return new None();
        }
    }

    /**
     * Construct BaseInfo from an absolute filesystem path (treated as a directory).
     * Result: Full(file://path/, ""), so root-relative links resolve within this directory.
     * Mirrors lychee's BaseInfo::from_path.
     */
    static BaseInfo fromPath(Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Path must be absolute: " + path);
        }
        String url = path.toUri().toString();
        if (!url.endsWith("/")) {
            url += "/";
        }
        return new Full(url, "");
    }

    /**
     * Construct BaseInfo for a file within a known root directory.
     * This is the correct way to handle file sources with a site root, because:
     * - local-relative links (e.g. "other.html") resolve against the file's parent dir
     * - root-relative links (e.g. "/docs/") resolve against the root dir
     *
     * Lychee achieves this via the parse_url_text_with_root_dir hack; we do it upfront.
     */
    static BaseInfo forFileWithRoot(Path file, Path rootDir) {
        String origin = rootDir.toUri().toString();
        if (!origin.endsWith("/")) {
            origin += "/";
        }
        String relativePath = rootDir.relativize(file).toString();
        return new Full(origin, relativePath);
    }

    // --- Resolution ---

    /**
     * Parse link text into a fully-qualified Uri, resolving relative links if possible.
     * Main entry point for link resolution.
     *
     * @return resolved Uri, or null if text is null/blank
     * @throws LinkResolutionException if the link is relative and cannot be resolved
     * @throws IllegalArgumentException if the text is genuinely malformed
     */
    default Uri parseUrlText(String text) {
        ParsedUri parsed = ParsedUri.parse(text);
        if (parsed == null) {
            return null;
        }
        return switch (parsed) {
            case ParsedUri.Absolute a -> a.uri();
            case ParsedUri.Relative r -> {
                String resolved = resolveRelativeLink(r.rel());
                yield Uri.tryFrom(resolved);
            }
        };
    }

    /**
     * Resolve a relative link against this base, returning the fully-qualified URL string.
     * Mirrors lychee's BaseInfo::resolve_relative_link.
     *
     * @throws LinkResolutionException if this base cannot resolve the given relative link type
     */
    default String resolveRelativeLink(RelativeUri rel) {
        return switch (this) {
            case None() -> throw new LinkResolutionException(
                    LinkResolutionException.Kind.RELATIVE_WITHOUT_BASE, rel.linkText());

            case NoRoot(String url) -> switch (rel) {
                case RelativeUri.Root r -> throw new LinkResolutionException(
                        LinkResolutionException.Kind.ROOT_RELATIVE_WITHOUT_ROOT, r.text());
                case RelativeUri.Local l -> uriResolve(url, l.text());
                case RelativeUri.Scheme s -> uriResolve(url, s.text());
            };

            case Full(String origin, String path) -> {
                // For file:// root-relative links, prepend "." so "/docs" becomes
                // "./docs" and resolves within the origin (root) directory.
                if (rel instanceof RelativeUri.Root r && origin.startsWith("file://")) {
                    yield uriResolve(origin, "." + r.text());
                }
                // General: reconstruct the source URL, then resolve the link against it.
                String base = path.isEmpty() ? origin : uriResolve(origin, path);
                yield uriResolve(base, rel.linkText());
            }
        };
    }

    // --- Fallback ---

    /**
     * Returns the more capable BaseInfo between this and the fallback.
     * Full > NoRoot > None. If both are the same variant, this is preferred.
     * Mirrors lychee's BaseInfo::or_fallback.
     */
    default BaseInfo orFallback(BaseInfo fallback) {
        return switch (this) {
            case Full f -> f;
            case NoRoot n -> fallback instanceof Full ? fallback : this;
            case None n -> fallback;
        };
    }

    // --- Internal ---

    /**
     * Resolve a relative reference against a base URI using RFC 3986 rules.
     * Normalizes the result: resolves ".." segments and fixes file:// authority.
     */
    private static String uriResolve(String base, String relative) {
        URI resolved = URI.create(base).resolve(relative).normalize();
        // Java's normalize() doesn't strip ".." above root. RFC 3986 says they should be removed.
        resolved = stripLeadingDotSegments(resolved);
        String result = resolved.toString();
        // Java's URI.resolve drops the empty authority from file:/// URIs,
        // producing "file:/path" instead of "file:///path". Fix only when
        // the authority is null (empty), not when it's a real host like "file://a.com/".
        if (result.startsWith("file:") && resolved.getAuthority() == null) {
            result = UrlUtils.normalizeFileUrl(result);
        }
        return result;
    }

    /**
     * Remove leading ".." segments from a URI path.
     * Java's URI.normalize() leaves these in, but RFC 3986 Section 5.4 says
     * they should be removed for absolute URIs.
     */
    private static URI stripLeadingDotSegments(URI uri) {
        String path = uri.getRawPath();
        if (path == null || !path.contains("..")) {
            return uri;
        }
        // Strip leading /.. segments
        while (path.startsWith("/..")) {
            if (path.length() == 3) {
                path = "/";
                break;
            }
            if (path.charAt(3) == '/') {
                path = path.substring(3);
            } else {
                break;
            }
        }
        if (path.equals(uri.getRawPath())) {
            return uri;
        }
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), path,
                    uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            return uri;
        }
    }
}
