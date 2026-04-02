---
title: Configuration
description: All Raclette builder options for customizing link checking behavior
layout: docs
---

## Builder API

Raclette uses a fluent builder pattern for configuration. All options set on the builder apply to every link check.

```java
import io.mvnpm.raclette.Raclette;
import io.mvnpm.raclette.ratelimit.RateLimitConfig;

Raclette raclette = Raclette.builder()
    .maxRetries(3)
    .timeout(Duration.ofSeconds(10))
    .userAgent("my-app/1.0")
    .requireHttps(true)
    .excludeAllPrivate(true)
    .excludes(".*example\\.com.*")
    .includes(".*github\\.com.*")
    .includeFragments(true)
    .rateLimitConfig(new RateLimitConfig(5, Duration.ofMillis(100)))
    .build();
```

## Options Reference

### HTTP Settings

| Option | Default | Description |
|--------|---------|-------------|
| `maxRetries` | 3 | Max retry attempts for failed requests |
| `retryWaitTime` | 1s | Initial wait between retries (doubles each attempt) |
| `timeout` | 20s | Request timeout |
| `maxRedirects` | 10 | Max redirects to follow |
| `userAgent` | `raclette/0.1` | User-Agent header |
| `customHeaders` | empty | Custom HTTP headers applied to all requests |
| `allowInsecure` | false | Accept invalid SSL certificates |
| `requireHttps` | false | Fail HTTP links if HTTPS is available |

### Filtering

| Option | Default | Description |
|--------|---------|-------------|
| `includes` | none | Regex patterns — only matching URLs are checked |
| `excludes` | none | Regex patterns — matching URLs are excluded |
| `includeMail` | false | Include `mailto:` links (excluded by default) |
| `excludeAllPrivate` | false | Exclude loopback, private, and link-local IPs |
| `excludeLoopbackIps` | false | Exclude loopback addresses (127.0.0.1, ::1) |
| `excludePrivateIps` | false | Exclude private IP ranges (10/8, 172.16/12, 192.168/16) |
| `excludeLinkLocalIps` | false | Exclude link-local addresses (169.254/16, fe80::/10) |

### File Checking

| Option | Default | Description |
|--------|---------|-------------|
| `includeFragments` | false | Verify `#fragment` targets exist in target files |
| `fallbackExtensions` | none | Try appending these extensions to file paths (e.g. `html`) |
| `indexFiles` | none | Try these filenames when a path points to a directory |

### Rate Limiting

| Option | Default | Description |
|--------|---------|-------------|
| `rateLimitConfig` | 10 concurrent, 50ms | Per-host rate limiting config |

The `RateLimitConfig` takes two parameters:

```java
new RateLimitConfig(
    5,                          // max concurrent requests per host
    Duration.ofMillis(100)      // minimum interval between requests
)
```

## Retry Behavior

Raclette retries failed requests with exponential backoff. The wait time doubles after each attempt:

- Attempt 1: immediate
- Attempt 2: wait 1s (default)
- Attempt 3: wait 2s
- Attempt 4: wait 4s

Only retryable errors trigger retries — permanent failures like 404 are returned immediately.

## HTTPS Enforcement

When `requireHttps(true)` is set, Raclette will:

1. Check the HTTP URL normally
2. If it succeeds, try the HTTPS equivalent
3. If HTTPS also works, report the HTTP link as an error (status 426)

This helps you find links that should be upgraded to HTTPS.
