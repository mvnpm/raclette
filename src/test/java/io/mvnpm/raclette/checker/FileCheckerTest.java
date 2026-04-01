package io.mvnpm.raclette.checker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.mvnpm.raclette.types.ErrorKind;
import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

/**
 * Tests translated from lychee's checker/file.rs
 */
class FileCheckerTest {

    static final Path FIXTURES_PATH = Paths.get("src/test/resources/fixtures").toAbsolutePath();

    private Uri fixtureUri(String subpath) {
        Path fullPath = FIXTURES_PATH.resolve(subpath);
        return Uri.file(fullPath.toUri().toString());
    }

    private Uri fixtureUriWithFragment(String subpath, String fragment) {
        Path fullPath = FIXTURES_PATH.resolve(subpath);
        String uriStr = fullPath.toUri().toString() + "#" + fragment;
        return Uri.file(uriStr);
    }

    // --- test_default ---

    @Test
    void testDefaultAcceptsDirLinks() {
        // Default behaviour: accepts dir links as long as directory exists, no index file resolution
        FileChecker checker = new FileChecker(List.of(), null, true);

        assertThat(checker.check(fixtureUri("filechecker/index_dir")))
                .isInstanceOf(Status.Ok.class);
    }

    @Test
    void testDefaultEmptyDirAccepted() {
        FileChecker checker = new FileChecker(List.of(), null, true);

        assertThat(checker.check(fixtureUri("filechecker/empty_dir")))
                .isInstanceOf(Status.Ok.class);
        assertThat(checker.check(fixtureUriWithFragment("filechecker/empty_dir", "")))
                .isInstanceOf(Status.Ok.class);
    }

    @Test
    void testDefaultEmptyDirFragmentRejected() {
        FileChecker checker = new FileChecker(List.of(), null, true);

        Status result = checker.check(fixtureUriWithFragment("filechecker/empty_dir", "fragment"));
        assertThat(result).isInstanceOf(Status.Error.class);
        assertThat(((Status.Error) result).error()).isInstanceOf(ErrorKind.InvalidFragment.class);
    }

    @Test
    void testDefaultIndexDirFragmentRejected() {
        // Even though index.html is present, it is not used because index_files is null (default = accept dir as-is)
        FileChecker checker = new FileChecker(List.of(), null, true);

        Status result = checker.check(fixtureUriWithFragment("filechecker/index_dir", "fragment"));
        assertThat(result).isInstanceOf(Status.Error.class);
        assertThat(((Status.Error) result).error()).isInstanceOf(ErrorKind.InvalidFragment.class);
    }

    @Test
    void testDefaultSameName() {
        FileChecker checker = new FileChecker(List.of(), null, true);

        assertThat(checker.check(fixtureUri("filechecker/same_name")))
                .isInstanceOf(Status.Ok.class);
    }

    @Test
    void testDefaultSameNameFragmentRejected() {
        // No fallback extensions, so same_name resolves to directory, no fragments
        FileChecker checker = new FileChecker(List.of(), null, true);

        Status result = checker.check(fixtureUriWithFragment("filechecker/same_name", "a"));
        assertThat(result).isInstanceOf(Status.Error.class);
        assertThat(((Status.Error) result).error()).isInstanceOf(ErrorKind.InvalidFragment.class);
    }

    // --- test_index_files ---

    @Test
    void testIndexFilesResolution() {
        FileChecker checker = new FileChecker(List.of(), List.of("index.html", "index.md"), true);

        // index_dir should resolve to index_dir/index.html
        Path resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/index_dir"),
                fixtureUri("filechecker/index_dir"));
        assertThat(resolved).isNotNull();
        assertThat(resolved.toString()).endsWith("index_dir/index.html");

        // index_md should resolve to index_md/index.md
        resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/index_md"),
                fixtureUri("filechecker/index_md"));
        assertThat(resolved).isNotNull();
        assertThat(resolved.toString()).endsWith("index_md/index.md");
    }

    @Test
    void testIndexFilesEmptyDirRejected() {
        FileChecker checker = new FileChecker(List.of(), List.of("index.html", "index.md"), true);

        // empty dir has no index.html or index.md
        Path resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/empty_dir"),
                fixtureUri("filechecker/empty_dir"));
        assertThat(resolved).isNull();
    }

    @Test
    void testIndexFilesFragmentChecked() {
        FileChecker checker = new FileChecker(List.of(), List.of("index.html", "index.md"), true);

        // index.html has <p id="fragment"> so this should pass
        assertThat(checker.check(fixtureUriWithFragment("filechecker/index_dir", "fragment")))
                .isInstanceOf(Status.Ok.class);

        // but non-existing fragments should fail
        Status result = checker.check(fixtureUriWithFragment("filechecker/index_dir", "non-existingfragment"));
        assertThat(result).isInstanceOf(Status.Error.class);
        assertThat(((Status.Error) result).error()).isInstanceOf(ErrorKind.InvalidFragment.class);
    }

    @Test
    void testIndexFilesDirWithExtensionRejected() {
        // Directories that look like files should still have index files applied
        FileChecker checker = new FileChecker(List.of(), List.of("index.html", "index.md"), true);

        Path resolved = checker.resolveLocalPath(
                FIXTURES_PATH.resolve("filechecker/dir_with_extension.html"),
                fixtureUri("filechecker/dir_with_extension.html"));
        assertThat(resolved).isNull();
    }

    // --- test_both_fallback_and_index_corner ---

    @Test
    void testBothFallbackAndIndexCorner() {
        FileChecker checker = new FileChecker(List.of("html"), List.of("index"), false);

        // same_name subdir exists + same_name.html file exists
        // index file resolving is applied, fallback extensions are NOT
        Path resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/same_name"),
                fixtureUri("filechecker/same_name"));
        assertThat(resolved).isNull(); // no "index" file in same_name dir

        // index_dir has index.html but indexFiles is ["index"] — fallback extensions NOT applied to index names
        resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/index_dir"),
                fixtureUri("filechecker/index_dir"));
        assertThat(resolved).isNull();

        // dir_with_extension.html is a directory — fallback ext must resolve to file, not dir
        resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/dir_with_extension"),
                fixtureUri("filechecker/dir_with_extension"));
        assertThat(resolved).isNull();
    }

    // --- test_empty_index_list_corner ---

    @Test
    void testEmptyIndexListCorner() {
        // Empty index_files list rejects all directory links
        FileChecker checker = new FileChecker(List.of(), List.of(), false);

        Path resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/index_dir"),
                fixtureUri("filechecker/index_dir"));
        assertThat(resolved).isNull();

        resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/empty_dir"),
                fixtureUri("filechecker/empty_dir"));
        assertThat(resolved).isNull();
    }

    // --- test_index_list_of_directories_corner ---

    @Test
    void testIndexListOfDirectoriesCorner() {
        // Index names that resolve to directories (not the special ".") should be rejected
        FileChecker checker = new FileChecker(List.of(),
                List.of("", "./.", "..", "/"), false);

        Path resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/index_dir"),
                fixtureUri("filechecker/index_dir"));
        assertThat(resolved).isNull();

        resolved = checker.resolveLocalPath(FIXTURES_PATH.resolve("filechecker/empty_dir"),
                fixtureUri("filechecker/empty_dir"));
        assertThat(resolved).isNull();
    }

    // --- test_fallback_extensions_on_directories ---

    @Test
    void testFallbackExtensionsOnDirectories() {
        FileChecker checker = new FileChecker(List.of("html"), null, true);

        // same_name is a directory, but same_name.html is a file with <p id="a">
        Path resolved = checker.resolveLocalPath(
                FIXTURES_PATH.resolve("filechecker/same_name"),
                fixtureUriWithFragment("filechecker/same_name", "a"));
        assertThat(resolved).isNotNull();
        assertThat(resolved.toString()).endsWith("same_name.html");
    }
}
