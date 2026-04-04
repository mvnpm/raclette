package io.mvnpm.raclette.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for ParsedUri and RelativeUri classification.
 * Test cases derived from lychee's base_info.rs test_parse_url_text
 * and test_parse_url_text_with_trailing_filename parametrized tests.
 */
class ParsedUriTest {

    // --- Null / blank ---

    @Test
    void testNullReturnsNull() {
        assertThat(ParsedUri.parse(null)).isNull();
    }

    @Test
    void testEmptyReturnsNull() {
        assertThat(ParsedUri.parse("")).isNull();
    }

    @Test
    void testBlankReturnsNull() {
        assertThat(ParsedUri.parse("   ")).isNull();
    }

    // --- Absolute URIs ---

    @Test
    void testHttpsAbsolute() {
        ParsedUri result = ParsedUri.parse("https://example.com");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
        assertThat(((ParsedUri.Absolute) result).uri().url()).isEqualTo("https://example.com");
    }

    @Test
    void testHttpsWithPath() {
        ParsedUri result = ParsedUri.parse("https://a.com/b");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
    }

    @Test
    void testHttpAbsolute() {
        ParsedUri result = ParsedUri.parse("http://example.com/page");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
    }

    @Test
    void testFileAbsolute() {
        ParsedUri result = ParsedUri.parse("file:///a");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
        assertThat(((ParsedUri.Absolute) result).uri().kind()).isEqualTo(Uri.UriKind.FILE);
    }

    @Test
    void testFileAbsoluteWithTrailingSlash() {
        ParsedUri result = ParsedUri.parse("file:///a/");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
    }

    @Test
    void testFileAbsoluteWithPath() {
        ParsedUri result = ParsedUri.parse("file:///a/b/");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
    }

    @Test
    void testMailtoAbsolute() {
        ParsedUri result = ParsedUri.parse("mailto:a@b.com");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
        assertThat(((ParsedUri.Absolute) result).uri().kind()).isEqualTo(Uri.UriKind.MAIL);
    }

    @Test
    void testTelAbsolute() {
        ParsedUri result = ParsedUri.parse("tel:1234567890");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
        assertThat(((ParsedUri.Absolute) result).uri().kind()).isEqualTo(Uri.UriKind.TEL);
    }

    @Test
    void testDataAbsolute() {
        // data: URIs are absolute (filtered by Filter, not here)
        ParsedUri result = ParsedUri.parse("data:text/plain,hello");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
    }

    @Test
    void testHttpsNewDomain() {
        // From lychee: "https://new.com" as link text
        ParsedUri result = ParsedUri.parse("https://new.com");
        assertThat(result).isInstanceOf(ParsedUri.Absolute.class);
    }

    // --- Root-relative (starts with /, not //) ---

    @Test
    void testRootRelativePath() {
        // From lychee: "/x/y" in file context
        ParsedUri result = ParsedUri.parse("/x/y");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        RelativeUri rel = ((ParsedUri.Relative) result).rel();
        assertThat(rel).isInstanceOf(RelativeUri.Root.class);
        assertThat(rel.linkText()).isEqualTo("/x/y");
    }

    @Test
    void testRootRelativeSlash() {
        // From lychee: "/" in file and http contexts
        ParsedUri result = ParsedUri.parse("/");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Root.class);
    }

    @Test
    void testRootRelativeParentTraversal() {
        // From lychee: "/.." in file context
        ParsedUri result = ParsedUri.parse("/..");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Root.class);
        assertThat(((ParsedUri.Relative) result).rel().linkText()).isEqualTo("/..");
    }

    @Test
    void testRootRelativeDoubleParentTraversal() {
        // From lychee: "/../../" in file context
        ParsedUri result = ParsedUri.parse("/../../");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Root.class);
    }

    @Test
    void testRootRelativeTraversalWithPath() {
        // From lychee: "/../../x" in file context
        ParsedUri result = ParsedUri.parse("/../../x");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Root.class);
    }

    // --- Scheme-relative (starts with //) ---

    @Test
    void testSchemeRelative() {
        // From lychee: "//a.com/boop"
        ParsedUri result = ParsedUri.parse("//a.com/boop");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        RelativeUri rel = ((ParsedUri.Relative) result).rel();
        assertThat(rel).isInstanceOf(RelativeUri.Scheme.class);
        assertThat(rel.linkText()).isEqualTo("//a.com/boop");
    }

    @Test
    void testSchemeRelativeTripleSlash() {
        // From lychee: "///new-root"
        ParsedUri result = ParsedUri.parse("///new-root");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Scheme.class);
    }

    // --- Local-relative (no leading /) ---

    @Test
    void testLocalRelativeSimple() {
        // From lychee: "d" as link text
        ParsedUri result = ParsedUri.parse("d");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        RelativeUri rel = ((ParsedUri.Relative) result).rel();
        assertThat(rel).isInstanceOf(RelativeUri.Local.class);
        assertThat(rel.linkText()).isEqualTo("d");
    }

    @Test
    void testLocalRelativeDirectory() {
        // From lychee: "a/" and "x/" as link text
        ParsedUri result = ParsedUri.parse("a/");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeParentTraversal() {
        // From lychee: "../.." as link text
        ParsedUri result = ParsedUri.parse("../..");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeTripleParent() {
        // From lychee: "../../.." as link text
        ParsedUri result = ParsedUri.parse("../../..");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeFragment() {
        // From lychee: "#" and "#x" as link text
        ParsedUri result = ParsedUri.parse("#");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
        assertThat(((ParsedUri.Relative) result).rel().linkText()).isEqualTo("#");
    }

    @Test
    void testLocalRelativeFragmentWithName() {
        // From lychee: "#x" as link text
        ParsedUri result = ParsedUri.parse("#x");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeQuery() {
        // From lychee: "?" and "?a" as link text
        ParsedUri result = ParsedUri.parse("?");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeQueryWithValue() {
        // From lychee: "?a" as link text
        ParsedUri result = ParsedUri.parse("?a");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeDot() {
        // From lychee: "." as link text
        ParsedUri result = ParsedUri.parse(".");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeDotSlash() {
        // From lychee: "./" as link text
        ParsedUri result = ParsedUri.parse("./");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeDotQuery() {
        // From lychee: ".?qq" as link text
        ParsedUri result = ParsedUri.parse(".?qq");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Local.class);
    }

    @Test
    void testLocalRelativeFilename() {
        // From lychee: "c", "relative", "sibling.html"
        for (String text : new String[] { "c", "relative", "sibling.html", "relative.html" }) {
            ParsedUri result = ParsedUri.parse(text);
            assertThat(result).as("text=%s", text).isInstanceOf(ParsedUri.Relative.class);
            assertThat(((ParsedUri.Relative) result).rel()).as("text=%s", text)
                    .isInstanceOf(RelativeUri.Local.class);
        }
    }

    // --- Leading whitespace trimmed (matches lychee's trim_ascii_start) ---

    @Test
    void testLeadingWhitespaceTrimmed() {
        ParsedUri result = ParsedUri.parse("  /docs/index.html");
        assertThat(result).isInstanceOf(ParsedUri.Relative.class);
        assertThat(((ParsedUri.Relative) result).rel()).isInstanceOf(RelativeUri.Root.class);
        assertThat(((ParsedUri.Relative) result).rel().linkText()).isEqualTo("/docs/index.html");
    }

    // --- Malformed URIs throw ---

    @Test
    void testMalformedUriThrows() {
        assertThatThrownBy(() -> ParsedUri.parse("http://[invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed URI");
    }
}
