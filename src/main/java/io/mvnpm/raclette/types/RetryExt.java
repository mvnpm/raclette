package io.mvnpm.raclette.types;

/**
 * Extension utility for determining if an HTTP status code should be retried.
 */
public final class RetryExt {

    private RetryExt() {
    }

    /**
     * Determine if a request with this status code should be retried.
     */
    public static boolean shouldRetry(int statusCode) {
        // Server errors (5xx)
        if (statusCode >= 500 && statusCode < 600) {
            return true;
        }
        // 408 Request Timeout
        // 429 Too Many Requests
        return statusCode == 408 || statusCode == 429;
    }

    /**
     * Determine if a Status should be retried.
     * Matches lychee's Status::should_retry() in retry.rs.
     */
    public static boolean shouldRetryStatus(Status status) {
        if (status instanceof Status.Timeout) {
            return true;
        }
        if (status instanceof Status.Error err) {
            if (err.error() instanceof ErrorKind.HttpStatus hs) {
                return shouldRetry(hs.statusCode());
            }
            if (err.error() instanceof ErrorKind.Timeout) {
                return true;
            }
            if (err.error() instanceof ErrorKind.NetworkError) {
                return false;
            }
        }
        return false;
    }
}
