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

    default boolean isSuccess() {
        return this instanceof Ok;
    }

    default boolean isError() {
        return this instanceof Error;
    }

    default boolean isTimeout() {
        return this instanceof Timeout;
    }

    default boolean isExcluded() {
        return this instanceof Excluded;
    }

    default boolean isUnsupported() {
        return this instanceof Unsupported;
    }

    /**
     * Extract the HTTP status code if available.
     */
    default Integer code() {
        if (this instanceof Ok ok) {
            return ok.statusCode();
        }
        if (this instanceof Error err && err.error() instanceof ErrorKind.HttpStatus hs) {
            return hs.statusCode();
        }
        return null;
    }
}
