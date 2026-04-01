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
}
