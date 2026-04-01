package io.mvnpm.raclette.quirks;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL rewriting quirks for known problematic sites.
 * Only the first matching quirk is applied (same as lychee quirks/mod.rs).
 */
public class Quirks {

    private static final Pattern CRATES_PATTERN = Pattern.compile("^(https?://)?(www\\.)?crates\\.io");
    private static final Pattern YOUTUBE_PATTERN = Pattern
            .compile("^(https?://)?(www\\.)?youtube(-nocookie)?\\.com");
    private static final Pattern YOUTUBE_SHORT_PATTERN = Pattern.compile("^(https?://)?(www\\.)?(youtu\\.?be)");
    private static final Pattern GITHUB_BLOB_MARKDOWN_FRAGMENT_PATTERN = Pattern.compile(
            "^https://github\\.com/(?<user>.*?)/(?<repo>.*?)/blob/(?<path>.*?)/(?<file>.*\\.(md|markdown)#.*)$");

    /**
     * Result of applying quirks: potentially rewritten URL and extra headers.
     */
    public record QuirkResult(String url, Map<String, String> extraHeaders) {
        public static QuirkResult unchanged(String url) {
            return new QuirkResult(url, Map.of());
        }

        public static QuirkResult withHeaders(String url, Map<String, String> headers) {
            return new QuirkResult(url, headers);
        }

        public static QuirkResult rewritten(String newUrl) {
            return new QuirkResult(newUrl, Map.of());
        }
    }

    /**
     * Apply quirks to a URL. Returns the (potentially rewritten) URL and any extra headers.
     */
    public QuirkResult apply(String url) {
        // Crates.io: add Accept: text/html
        if (CRATES_PATTERN.matcher(url).find()) {
            return QuirkResult.withHeaders(url, Map.of("Accept", "text/html"));
        }

        // YouTube standard URLs: rewrite video pages to thumbnail
        if (YOUTUBE_PATTERN.matcher(url).find()) {
            return applyYoutube(url);
        }

        // YouTube short links: rewrite to thumbnail
        if (YOUTUBE_SHORT_PATTERN.matcher(url).find()) {
            return applyYoutubeShort(url);
        }

        // GitHub blob markdown with fragment: rewrite to raw.githubusercontent.com
        Matcher ghMatcher = GITHUB_BLOB_MARKDOWN_FRAGMENT_PATTERN.matcher(url);
        if (ghMatcher.matches()) {
            String rawUrl = "https://raw.githubusercontent.com/"
                    + ghMatcher.group("user") + "/"
                    + ghMatcher.group("repo") + "/"
                    + ghMatcher.group("path") + "/"
                    + ghMatcher.group("file");
            return QuirkResult.rewritten(rawUrl);
        }

        return QuirkResult.unchanged(url);
    }

    private QuirkResult applyYoutube(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            String videoId = null;

            if ("/watch".equals(path)) {
                // Extract v= query param
                String query = uri.getQuery();
                if (query != null) {
                    videoId = parseQueryParam(query, "v");
                }
            } else if (path != null && path.startsWith("/embed/")) {
                videoId = path.substring("/embed/".length());
            }

            if (videoId != null && !videoId.isEmpty()) {
                return QuirkResult.rewritten("https://img.youtube.com/vi/" + videoId + "/0.jpg");
            }
        } catch (Exception e) {
            // Fall through
        }
        return QuirkResult.unchanged(url);
    }

    private QuirkResult applyYoutubeShort(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path != null) {
                // Use the full path as video ID (lychee quirks/mod.rs uses url.path())
                String id = path.startsWith("/") ? path.substring(1) : path;
                if (!id.isEmpty()) {
                    return QuirkResult.rewritten("https://img.youtube.com/vi/" + id + "/0.jpg");
                }
            }
        } catch (Exception e) {
            // Fall through
        }
        return QuirkResult.unchanged(url);
    }

    private static String parseQueryParam(String query, String param) {
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(param)) {
                return kv[1];
            }
        }
        return null;
    }
}
