package io.mvnpm.raclette.types;

/**
 * Status of a link check.
 */
public sealed interface Status {

    record Ok(int statusCode) implements Status {
    }

    record Error(ErrorKind error) implements Status {
    }

    record Timeout(String message) implements Status {
    }

    record Excluded(String reason) implements Status {
    }

    record Unsupported(String scheme) implements Status {
    }

    static Status ok() {
        return new Ok(200);
    }

    static Status ok(int code) {
        return new Ok(code);
    }

    static Status error(ErrorKind error) {
        return new Error(error);
    }

    static Status excluded(String reason) {
        return new Excluded(reason);
    }

    static Status unsupported(String scheme) {
        return new Unsupported(scheme);
    }
}
