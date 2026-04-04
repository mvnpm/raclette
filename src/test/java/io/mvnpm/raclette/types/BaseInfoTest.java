package io.mvnpm.raclette.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for BaseInfo resolution.
 * Ported from lychee's base_info.rs tests.
 */
class BaseInfoTest {

    // --- Construction ---

    @Test
    void testFromSourceUrlHttp() {
        BaseInfo base = BaseInfo.fromSourceUrl("https://a.com/b/?q#x");
        assertThat(base).isInstanceOf(BaseInfo.Full.class);
        BaseInfo.Full full = (BaseInfo.Full) base;
        assertThat(full.origin()).isEqualTo("https://a.com/");
    }

    @Test
    void testFromSourceUrlFile() {
        BaseInfo base = BaseInfo.fromSourceUrl("file:///some/path");
        assertThat(base).isInstanceOf(BaseInfo.NoRoot.class);
        assertThat(((BaseInfo.NoRoot) base).url()).isEqualTo("file:///some/path");
    }

    @Test
    void testFromPath() {
        BaseInfo base = BaseInfo.fromPath(Path.of("/file-path"));
        assertThat(base).isInstanceOf(BaseInfo.Full.class);
        BaseInfo.Full full = (BaseInfo.Full) base;
        assertThat(full.origin()).startsWith("file:///");
        assertThat(full.origin()).endsWith("/");
        assertThat(full.path()).isEmpty();
    }

    @Test
    void testFromPathRejectsRelative() {
        assertThatThrownBy(() -> BaseInfo.fromPath(Path.of("relative")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testForFileWithRoot() {
        BaseInfo base = BaseInfo.forFileWithRoot(
                Path.of("/site/about/index.html"),
                Path.of("/site"));
        assertThat(base).isInstanceOf(BaseInfo.Full.class);
        BaseInfo.Full full = (BaseInfo.Full) base;
        assertThat(full.origin()).endsWith("/site/");
        assertThat(full.path()).isEqualTo("about/index.html");
    }

    // --- test_parse_url_text (from lychee base_info.rs lines 542-586) ---
    // Each row: origin, path, text, expected

    @ParameterizedTest(name = "origin={0} path={1} text={2} -> {3}")
    @CsvSource({
            // normal HTTP traversal and parsing absolute links
            "https://a.com/b, x/, d, https://a.com/x/d",
            "https://a.com/b/, x/, d, https://a.com/b/x/d",
            "https://a.com/b/, '', https://new.com, https://new.com",
            // parsing absolute file://
            "https://a.com/b/, '', file:///a, file:///a",
            "https://a.com/b/, '', file:///a/, file:///a/",
            "https://a.com/b/, '', file:///a/b/, file:///a/b/",
            // file traversal
            "file:///a/b/, '', a/, file:///a/b/a/",
            "file:///a/b/, a/, '../..', file:///a/",
            "file:///a/b/, '', '?', file:///a/b/?",
            // HTTP relative links
            "https://a.com/x, '', '#', https://a.com/x#",
            "https://a.com/x, '', '../../..', https://a.com/",
            "https://a.com/x/, '', /, https://a.com/",
    })
    void testParseUrlText(String origin, String path, String text, String expected) {
        // CsvSource uses '' for empty strings
        path = path.equals("''") ? "" : path;
        text = text.equals("''") ? "" : text;

        BaseInfo base = new BaseInfo.Full(origin, path);
        Uri result = base.parseUrlText(text);
        assertThat(result).as("origin=%s, path=%s, text=%s", origin, path, text).isNotNull();
        assertThat(result.url()).as("origin=%s, path=%s, text=%s", origin, path, text)
                .isEqualTo(expected);
    }

    // --- File root-relative (special case: prepend "." to stay within origin) ---

    @ParameterizedTest(name = "origin={0} text={1} -> {2}")
    @CsvSource({
            "file:///a/b/, /x/y, file:///a/b/x/y",
            "file:///a/b/, /, file:///a/b/",
            "file:///a/b/, /.., file:///a/",
            "file:///a/b/, /../../, file:///",
    })
    void testParseUrlTextFileRootRelative(String origin, String text, String expected) {
        BaseInfo base = new BaseInfo.Full(origin, "");
        Uri result = base.parseUrlText(text);
        assertThat(result).as("origin=%s, text=%s", origin, text).isNotNull();
        assertThat(result.url()).as("origin=%s, text=%s", origin, text)
                .isEqualTo(expected);
    }

    // --- Scheme-relative links ---

    @ParameterizedTest(name = "origin={0} text={1} -> {2}")
    @CsvSource({
            "file:///root/, ///new-root, file:///new-root",
            "file:///root/, //a.com/boop, file://a.com/boop",
            "https://root/, //a.com/boop, https://a.com/boop",
    })
    void testParseUrlTextSchemeRelative(String origin, String text, String expected) {
        BaseInfo base = new BaseInfo.Full(origin, "");
        Uri result = base.parseUrlText(text);
        assertThat(result).as("origin=%s, text=%s", origin, text).isNotNull();
        assertThat(result.url()).as("origin=%s, text=%s", origin, text)
                .isEqualTo(expected);
    }

    // --- test_parse_url_text_with_trailing_filename (lychee lines 588-619) ---
    // File URLs without trailing / (origin points to a file, not directory)

    @ParameterizedTest(name = "origin={0} path={1} text={2} -> {3}")
    @CsvSource({
            "file:///a/b/c, '', /../../x, file:///x",
            "file:///a/b/c, '', /, file:///a/b/",
            "file:///a/b/c, '', '#x', file:///a/b/c#x",
            "file:///a/b/c, '', ./, file:///a/b/",
            "file:///a/b/c, '', c, file:///a/b/c",
            // joining with d
            "file:///a/b/c, d, /../../x, file:///x",
            "file:///a/b/c, d, /, file:///a/b/",
            "file:///a/b/c, d, ., file:///a/b/",
            "file:///a/b/c, d, ./, file:///a/b/",
            // joining with d/
            "file:///a/b/c, d/, /, file:///a/b/",
            "file:///a/b/c, d/, ., file:///a/b/d/",
            "file:///a/b/c, d/, ./, file:///a/b/d/",
    })
    void testParseUrlTextWithTrailingFilename(String origin, String path, String text, String expected) {
        path = path.equals("''") ? "" : path;

        BaseInfo base = new BaseInfo.Full(origin, path);
        Uri result = base.parseUrlText(text);
        assertThat(result).as("origin=%s, path=%s, text=%s", origin, path, text).isNotNull();
        assertThat(result.url()).as("origin=%s, path=%s, text=%s", origin, path, text)
                .isEqualTo(expected);
    }

    // --- test_none_rejects_relative_but_accepts_absolute (lychee line 621) ---

    @Test
    void testNoneRejectsRelativeButAcceptsAbsolute() {
        BaseInfo none = BaseInfo.none();
        // Absolute URLs still work
        assertThat(none.parseUrlText("https://a.com")).isNotNull();
        // Relative links fail
        assertThatThrownBy(() -> none.parseUrlText("relative"))
                .isInstanceOf(LinkResolutionException.class)
                .satisfies(e -> assertThat(((LinkResolutionException) e).kind())
                        .isEqualTo(LinkResolutionException.Kind.RELATIVE_WITHOUT_BASE));
        assertThatThrownBy(() -> none.parseUrlText("/root-relative"))
                .isInstanceOf(LinkResolutionException.class);
    }

    // --- test_no_root_rejects_root_relative (lychee line 632) ---

    @Test
    void testNoRootRejectsRootRelative() {
        BaseInfo noRoot = new BaseInfo.NoRoot("file:///some/path/");
        // Local-relative resolves
        Uri result = noRoot.parseUrlText("sibling.html");
        assertThat(result).isNotNull();
        assertThat(result.url()).isEqualTo("file:///some/path/sibling.html");
        // Root-relative fails
        assertThatThrownBy(() -> noRoot.parseUrlText("/root-relative"))
                .isInstanceOf(LinkResolutionException.class)
                .satisfies(e -> assertThat(((LinkResolutionException) e).kind())
                        .isEqualTo(LinkResolutionException.Kind.ROOT_RELATIVE_WITHOUT_ROOT));
    }

    // --- test_or_fallback_prefers_more_capable_variant (lychee line 643) ---

    @Test
    void testOrFallbackPrefersMoreCapableVariant() {
        BaseInfo none = BaseInfo.none();
        BaseInfo noRoot = new BaseInfo.NoRoot("file:///a/");
        BaseInfo full = new BaseInfo.Full("https://a.com/", "");

        assertThat(none.orFallback(full)).isEqualTo(full);
        assertThat(full.orFallback(none)).isEqualTo(full);
        assertThat(none.orFallback(noRoot)).isEqualTo(noRoot);
        assertThat(noRoot.orFallback(full)).isEqualTo(full);
        assertThat(none.orFallback(none)).isEqualTo(none);
    }

    // --- test_forFileWithRoot resolves both local and root relative (new, validates concern #1 fix) ---

    @Test
    void testForFileWithRootResolvesBothLocalAndRootRelative() {
        BaseInfo base = BaseInfo.forFileWithRoot(
                Path.of("/site/about/index.html"),
                Path.of("/site"));

        // Local-relative: resolves against the file's parent dir
        Uri local = base.parseUrlText("other.html");
        assertThat(local).isNotNull();
        assertThat(local.url()).isEqualTo("file:///site/about/other.html");

        // Root-relative: resolves against the root dir
        Uri root = base.parseUrlText("/docs/index.html");
        assertThat(root).isNotNull();
        assertThat(root.url()).isEqualTo("file:///site/docs/index.html");

        // Parent traversal: stays relative to file location
        Uri parent = base.parseUrlText("../index.html");
        assertThat(parent).isNotNull();
        assertThat(parent.url()).isEqualTo("file:///site/index.html");
    }

    // --- Absolute links pass through regardless of base ---

    @Test
    void testAbsoluteLinksPassThrough() {
        for (BaseInfo base : new BaseInfo[] {
                BaseInfo.none(),
                new BaseInfo.NoRoot("file:///some/path/"),
                new BaseInfo.Full("https://a.com/", "b/")
        }) {
            Uri result = base.parseUrlText("https://example.com/page");
            assertThat(result).as("base=%s", base).isNotNull();
            assertThat(result.url()).as("base=%s", base).isEqualTo("https://example.com/page");
        }
    }

    // --- Null/blank returns null ---

    @Test
    void testNullAndBlankReturnNull() {
        BaseInfo base = new BaseInfo.Full("https://a.com/", "");
        assertThat(base.parseUrlText(null)).isNull();
        assertThat(base.parseUrlText("")).isNull();
        assertThat(base.parseUrlText("   ")).isNull();
    }
}
