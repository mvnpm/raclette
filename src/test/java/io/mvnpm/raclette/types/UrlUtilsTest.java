package io.mvnpm.raclette.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlUtilsTest {

    // --- splitFragment ---

    @Test
    void splitFragmentWithFragment() {
        String[] result = UrlUtils.splitFragment("file:///path/page#section");
        assertThat(result[0]).isEqualTo("file:///path/page");
        assertThat(result[1]).isEqualTo("section");
    }

    @Test
    void splitFragmentWithoutFragment() {
        String[] result = UrlUtils.splitFragment("file:///path/page");
        assertThat(result[0]).isEqualTo("file:///path/page");
        assertThat(result[1]).isNull();
    }

    @Test
    void splitFragmentEmptyFragment() {
        String[] result = UrlUtils.splitFragment("file:///path/page#");
        assertThat(result[0]).isEqualTo("file:///path/page");
        assertThat(result[1]).isEmpty();
    }

    @Test
    void splitFragmentMultipleHashes() {
        // First # is the delimiter per RFC 3986
        String[] result = UrlUtils.splitFragment("file:///path#first#second");
        assertThat(result[0]).isEqualTo("file:///path");
        assertThat(result[1]).isEqualTo("first#second");
    }

    // --- stripQueryAndFragment ---

    @Test
    void stripQueryAndFragmentBoth() {
        assertThat(UrlUtils.stripQueryAndFragment("file:///path?q=1#section"))
                .isEqualTo("file:///path");
    }

    @Test
    void stripQueryAndFragmentQueryOnly() {
        assertThat(UrlUtils.stripQueryAndFragment("file:///path?q=1"))
                .isEqualTo("file:///path");
    }

    @Test
    void stripQueryAndFragmentFragmentOnly() {
        assertThat(UrlUtils.stripQueryAndFragment("file:///path#section"))
                .isEqualTo("file:///path");
    }

    @Test
    void stripQueryAndFragmentNeither() {
        assertThat(UrlUtils.stripQueryAndFragment("file:///path"))
                .isEqualTo("file:///path");
    }

    @Test
    void stripQueryAndFragmentQuestionMarkInsideFragment() {
        // ? inside a fragment is legal and should not be treated as query delimiter
        assertThat(UrlUtils.stripQueryAndFragment("file:///path#section?note"))
                .isEqualTo("file:///path");
    }

    // --- extractFragment ---

    @Test
    void extractFragmentPresent() {
        assertThat(UrlUtils.extractFragment("file:///path#section")).isEqualTo("section");
    }

    @Test
    void extractFragmentAbsent() {
        assertThat(UrlUtils.extractFragment("file:///path")).isNull();
    }

    @Test
    void extractFragmentEmpty() {
        assertThat(UrlUtils.extractFragment("file:///path#")).isEmpty();
    }

    // --- fileUrlToPath ---

    @Test
    void fileUrlToPathTripleSlash() {
        assertThat(UrlUtils.fileUrlToPath("file:///path/to/file")).isEqualTo("/path/to/file");
    }

    @Test
    void fileUrlToPathSingleSlash() {
        assertThat(UrlUtils.fileUrlToPath("file:/path/to/file")).isEqualTo("/path/to/file");
    }

    @Test
    void fileUrlToPathPercentEncoded() {
        assertThat(UrlUtils.fileUrlToPath("file:///path/my%20file.html")).isEqualTo("/path/my file.html");
    }

    @Test
    void fileUrlToPathNonFilePassthrough() {
        assertThat(UrlUtils.fileUrlToPath("/plain/path")).isEqualTo("/plain/path");
    }

    // --- pathToFileUrl ---

    @Test
    void pathToFileUrl() {
        assertThat(UrlUtils.pathToFileUrl("/path/to/file")).isEqualTo("file:///path/to/file");
    }

    @Test
    void pathToFileUrlTrailingSlash() {
        assertThat(UrlUtils.pathToFileUrl("/path/to/dir/")).isEqualTo("file:///path/to/dir/");
    }

    // --- normalizePathPrefix ---

    @Test
    void normalizePathPrefixNoSlashes() {
        assertThat(UrlUtils.normalizePathPrefix("raclette")).isEqualTo("/raclette/");
    }

    @Test
    void normalizePathPrefixLeadingOnly() {
        assertThat(UrlUtils.normalizePathPrefix("/raclette")).isEqualTo("/raclette/");
    }

    @Test
    void normalizePathPrefixTrailingOnly() {
        assertThat(UrlUtils.normalizePathPrefix("raclette/")).isEqualTo("/raclette/");
    }

    @Test
    void normalizePathPrefixBothSlashes() {
        assertThat(UrlUtils.normalizePathPrefix("/raclette/")).isEqualTo("/raclette/");
    }

    @Test
    void normalizePathPrefixJustSlash() {
        assertThat(UrlUtils.normalizePathPrefix("/")).isEqualTo("/");
    }

    // --- normalizeFileUrl ---

    @Test
    void normalizeFileUrlSingleSlash() {
        assertThat(UrlUtils.normalizeFileUrl("file:/path/to/file"))
                .isEqualTo("file:///path/to/file");
    }

    @Test
    void normalizeFileUrlTripleSlash() {
        assertThat(UrlUtils.normalizeFileUrl("file:///path/to/file"))
                .isEqualTo("file:///path/to/file");
    }

    @Test
    void normalizeFileUrlDoubleSlash() {
        assertThat(UrlUtils.normalizeFileUrl("file://path/to/file"))
                .isEqualTo("file:///path/to/file");
    }

    @Test
    void normalizeFileUrlHttpPassthrough() {
        assertThat(UrlUtils.normalizeFileUrl("https://example.com"))
                .isEqualTo("https://example.com");
    }

    // --- parentDir ---

    @Test
    void parentDirWithSlash() {
        assertThat(UrlUtils.parentDir("/path/to/file")).isEqualTo("/path/to/");
    }

    @Test
    void parentDirRoot() {
        assertThat(UrlUtils.parentDir("/root")).isEqualTo("/");
    }

    @Test
    void parentDirNoSlash() {
        assertThat(UrlUtils.parentDir("noSlash")).isNull();
    }
}
