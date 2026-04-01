package io.mvnpm.raclette.extract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests translated from lychee's extract/html/srcset.rs
 */
class SrcsetParserTest {

    // --- splitAt tests ---

    @Test
    void testSplitAtEmptyString() {
        String[] result = SrcsetParser.splitAt("", Character::isAlphabetic);
        assertThat(result[0]).isEmpty();
        assertThat(result[1]).isEmpty();
    }

    @Test
    void testSplitAtAlphabeticPredicate() {
        String[] result = SrcsetParser.splitAt("abc123", Character::isAlphabetic);
        assertThat(result[0]).isEqualTo("abc");
        assertThat(result[1]).isEqualTo("123");
    }

    @Test
    void testSplitAtDigitPredicate() {
        String[] result = SrcsetParser.splitAt("123abc", Character::isDigit);
        assertThat(result[0]).isEqualTo("123");
        assertThat(result[1]).isEqualTo("abc");
    }

    @Test
    void testSplitAtNoMatch() {
        String[] result = SrcsetParser.splitAt("123abc", Character::isWhitespace);
        assertThat(result[0]).isEmpty();
        assertThat(result[1]).isEqualTo("123abc");
    }

    @Test
    void testSplitAtAllMatch() {
        String[] result = SrcsetParser.splitAt("123abc", c -> !Character.isWhitespace(c));
        assertThat(result[0]).isEqualTo("123abc");
        assertThat(result[1]).isEmpty();
    }

    // --- parse tests ---

    @Test
    void testParseNoValue() {
        assertThat(SrcsetParser.parse("")).isEmpty();
    }

    @Test
    void testParseOneValue() {
        assertThat(SrcsetParser.parse("test-img-320w.jpg 320w"))
                .containsExactly("test-img-320w.jpg");
    }

    @Test
    void testParseTwoValues() {
        assertThat(SrcsetParser.parse("test-img-320w.jpg 320w, test-img-480w.jpg 480w"))
                .containsExactly("test-img-320w.jpg", "test-img-480w.jpg");
    }

    @Test
    void testParseWithUnencodedComma() {
        assertThat(SrcsetParser.parse(
                "/cdn-cgi/image/format=webp,width=640/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg 640w, /cdn-cgi/image/format=webp,width=750/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg 750w"))
                .containsExactly(
                        "/cdn-cgi/image/format=webp,width=640/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg",
                        "/cdn-cgi/image/format=webp,width=750/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg");
    }

    @Test
    void testParseSrcsetUrl() {
        assertThat(SrcsetParser.parse(
                "https://example.com/image1.jpg 1x, https://example.com/image2.jpg 2x"))
                .containsExactly(
                        "https://example.com/image1.jpg",
                        "https://example.com/image2.jpg");
    }

    @Test
    void testParseSrcsetWithCommas() {
        assertThat(SrcsetParser.parse(
                "/cdn-cgi/image/format=webp,width=640/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg 640w, /cdn-cgi/image/format=webp,width=750/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg 750w"))
                .containsExactly(
                        "/cdn-cgi/image/format=webp,width=640/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg",
                        "/cdn-cgi/image/format=webp,width=750/https://img.youtube.com/vi/hVBl8_pgQf0/maxresdefault.jpg");
    }

    @Test
    void testParseSrcsetWithoutSpaces() {
        assertThat(SrcsetParser.parse(
                "/300.png 300w,/600.png 600w,/900.png 900w,https://x.invalid/a.png 1000w,relative.png 10w"))
                .containsExactly(
                        "/300.png",
                        "/600.png",
                        "/900.png",
                        "https://x.invalid/a.png",
                        "relative.png");
    }
}
