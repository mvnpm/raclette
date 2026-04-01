package io.mvnpm.raclette.types;

/**
 * Kinds of errors that can occur during link checking.
 */
public sealed interface ErrorKind {

    record InvalidFilePath(Uri uri) implements ErrorKind {
    }

    record InvalidFragment(Uri uri) implements ErrorKind {
    }

    record InvalidIndexFile(java.util.List<String> indexNames) implements ErrorKind {
    }

    record NetworkError(String message) implements ErrorKind {
    }

    record HttpStatus(int code) implements ErrorKind {
    }

    record Timeout(String message) implements ErrorKind {
    }

    record SslError(String message) implements ErrorKind {
    }

    record TooManyRedirects(int count) implements ErrorKind {
    }
}
