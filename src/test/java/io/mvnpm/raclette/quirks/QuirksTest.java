package io.mvnpm.raclette.quirks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.mvnpm.raclette.quirks.Quirks.QuirkResult;

/**
 * Tests translated from lychee's quirks/mod.rs.
 */
class QuirksTest {

    private final Quirks quirks = new Quirks();

    @Test
    void testCratesIoRequest() {
        QuirkResult result = quirks.apply("https://crates.io/crates/lychee");
        assertThat(result.url()).isEqualTo("https://crates.io/crates/lychee");
        assertThat(result.extraHeaders()).containsEntry("Accept", "text/html");
    }

    @Test
    void testYoutubeVideoRequest() {
        QuirkResult result = quirks.apply(
                "https://www.youtube.com/watch?v=NlKuICiT470&list=PLbWDhxwM_45mPVToqaIZNbZeIzFchsKKQ&index=7");
        assertThat(result.url()).isEqualTo("https://img.youtube.com/vi/NlKuICiT470/0.jpg");
    }

    @Test
    void testYoutubeVideoNocookieRequest() {
        QuirkResult result = quirks.apply("https://www.youtube-nocookie.com/embed/BIguvia6AvM");
        assertThat(result.url()).isEqualTo("https://img.youtube.com/vi/BIguvia6AvM/0.jpg");
    }

    @Test
    void testYoutubeVideoShortlinkRequest() {
        QuirkResult result = quirks.apply("https://youtu.be/Rvu7N4wyFpk?t=42");
        assertThat(result.url()).isEqualTo("https://img.youtube.com/vi/Rvu7N4wyFpk/0.jpg");
    }

    @Test
    void testNonVideoYoutubeUrlUntouched() {
        QuirkResult result = quirks.apply("https://www.youtube.com/channel/UCaYhcUwRBNscFNUKTjgPFiA");
        assertThat(result.url()).isEqualTo("https://www.youtube.com/channel/UCaYhcUwRBNscFNUKTjgPFiA");
        assertThat(result.extraHeaders()).isEmpty();
    }

    @Test
    void testGithubBlobMarkdownFragmentRequest() {
        record Case(String input, String expected) {
        }
        var cases = new Case[] {
                new Case(
                        "https://github.com/moby/docker-image-spec/blob/main/spec.md#terminology",
                        "https://raw.githubusercontent.com/moby/docker-image-spec/main/spec.md#terminology"),
                new Case(
                        "https://github.com/moby/docker-image-spec/blob/main/spec.markdown#terminology",
                        "https://raw.githubusercontent.com/moby/docker-image-spec/main/spec.markdown#terminology"),
                // No fragment — not rewritten
                new Case(
                        "https://github.com/moby/docker-image-spec/blob/main/spec.md",
                        "https://github.com/moby/docker-image-spec/blob/main/spec.md"),
                // Not markdown — not rewritten
                new Case(
                        "https://github.com/lycheeverse/lychee/blob/master/.gitignore#section",
                        "https://github.com/lycheeverse/lychee/blob/master/.gitignore#section"),
                // Versioned path
                new Case(
                        "https://github.com/lycheeverse/lychee/blob/v0.15.0/README.md#features",
                        "https://raw.githubusercontent.com/lycheeverse/lychee/v0.15.0/README.md#features"),
        };
        for (Case c : cases) {
            QuirkResult result = quirks.apply(c.input());
            assertThat(result.url()).as("input: %s", c.input()).isEqualTo(c.expected());
        }
    }

    @Test
    void testNoQuirkApplied() {
        QuirkResult result = quirks.apply("https://endler.dev");
        assertThat(result.url()).isEqualTo("https://endler.dev");
        assertThat(result.extraHeaders()).isEmpty();
    }
}
