package io.mvnpm.raclette.checker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.mvnpm.raclette.quirks.Quirks;
import io.mvnpm.raclette.quirks.Quirks.QuirkResult;
import io.mvnpm.raclette.types.ErrorKind;
import io.mvnpm.raclette.types.RetryExt;
import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

/**
 * Checks website URLs via HTTP using java.net.http.HttpClient.
 * Supports retry with exponential backoff, quirks, and custom headers.
 *
 * Translated from lychee's checker/website.rs.
 * Uses plain blocking calls — on Loom virtual threads the OS thread is released during I/O.
 */
public class WebsiteChecker {

    private final HttpClient httpClient;
    private final int maxRetries;
    private final Duration retryWaitTime;
    private final Duration timeout;
    private final int maxRedirects;
    private final Map<String, String> customHeaders;
    private final String userAgent;
    private final boolean requireHttps;
    private final Quirks quirks;

    public WebsiteChecker(int maxRetries, Duration retryWaitTime, Duration timeout,
            int maxRedirects, Map<String, String> customHeaders, String userAgent,
            boolean allowInsecure, boolean requireHttps) {
        this.maxRetries = maxRetries;
        this.retryWaitTime = retryWaitTime;
        this.timeout = timeout;
        this.maxRedirects = maxRedirects;
        this.customHeaders = customHeaders;
        this.userAgent = userAgent;
        this.requireHttps = requireHttps;
        this.quirks = new Quirks();

        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout);

        if (allowInsecure) {
            try {
                SSLContext sslContext = createInsecureSslContext();
                builder.sslContext(sslContext);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create insecure SSL context", e);
            }
        }

        this.httpClient = builder.build();
    }

    /**
     * Check a website URL with retry and quirks.
     */
    public Status check(Uri uri) {
        // Apply quirks
        QuirkResult quirk = quirks.apply(uri.url());
        String effectiveUrl = quirk.url();

        // Retry with exponential backoff
        Status status = retryRequest(effectiveUrl, quirk.extraHeaders());

        // HTTPS enforcement: if HTTP succeeded, check if HTTPS is available
        if (requireHttps && uri.url().startsWith("http://") && status.isSuccess()) {
            String httpsUrl = "https://" + uri.url().substring("http://".length());
            Status httpsStatus = doRequest(httpsUrl, quirk.extraHeaders());
            if (httpsStatus.isSuccess()) {
                return Status.error(new ErrorKind.HttpStatus(426, "HTTPS is available but HTTP was used"));
            }
        }

        return status;
    }

    private Status retryRequest(String url, Map<String, String> extraHeaders) {
        Duration waitTime = retryWaitTime;
        Status lastStatus = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(waitTime.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Status.error(new ErrorKind.NetworkError("Interrupted"));
                }
                waitTime = waitTime.multipliedBy(2);
            }

            lastStatus = doRequest(url, extraHeaders);

            if (lastStatus.isSuccess() || !RetryExt.shouldRetryStatus(lastStatus)) {
                return lastStatus;
            }
        }

        return lastStatus;
    }

    private Status doRequest(String url, Map<String, String> extraHeaders) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .GET();

            // Apply custom headers
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
            // Apply quirk headers
            for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            // Discard body — we only need the status code (lychee also uses a limited body handler)
            HttpResponse<Void> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.discarding());

            // Detect redirect exhaustion: java.net.http follows redirects internally
            // and returns the final response. We track via redirect count.
            int redirectCount = countRedirects(response);
            if (redirectCount > maxRedirects) {
                return Status.error(new ErrorKind.TooManyRedirects(maxRedirects));
            }

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return Status.ok(statusCode);
            } else if (statusCode >= 300 && statusCode < 400) {
                // Redirect not followed (NEVER policy or exhausted)
                return Status.error(new ErrorKind.TooManyRedirects(maxRedirects));
            } else {
                return Status.error(new ErrorKind.HttpStatus(statusCode, ""));
            }
        } catch (java.net.http.HttpTimeoutException e) {
            return new Status.Timeout(e.getMessage());
        } catch (java.io.IOException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (isSslException(e)) {
                return Status.error(new ErrorKind.SslError(message));
            }
            return Status.error(new ErrorKind.NetworkError(message));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Status.error(new ErrorKind.NetworkError("Interrupted"));
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return Status.error(new ErrorKind.NetworkError(message));
        }
    }

    /**
     * Count redirects by walking the previous response chain.
     */
    private static int countRedirects(HttpResponse<?> response) {
        int count = 0;
        var prev = response.previousResponse();
        while (prev.isPresent()) {
            count++;
            prev = prev.get().previousResponse();
        }
        return count;
    }

    private static boolean isSslException(Throwable e) {
        if (e instanceof javax.net.ssl.SSLException) {
            return true;
        }
        String msg = e.getMessage();
        if (msg != null && (msg.contains("SSL") || msg.contains("certificate") || msg.contains("PKIX"))) {
            return true;
        }
        if (e.getCause() != null && e.getCause() != e) {
            return isSslException(e.getCause());
        }
        return false;
    }

    /**
     * Create an SSLContext that trusts all certificates (for allowInsecure mode).
     */
    private static SSLContext createInsecureSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        return sslContext;
    }

    /**
     * Close the underlying HTTP client.
     */
    public void close() {
        httpClient.close();
    }
}
