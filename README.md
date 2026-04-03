<p align="center">
  <img src="site/public/raclette-logo.svg" alt="Raclette" width="200" height="200">
</p>

<h1 align="center">Raclette</h1>

<p align="center">
  <em>A fast, virtual-threaded link checker for Java</em>
</p>

<p align="center">
  Finds broken hyperlinks and mail addresses in websites and HTML files.
</p>

<p align="center">
  <a href="https://github.com/mvnpm/raclette/actions"><img src="https://github.com/mvnpm/raclette/actions/workflows/maven.yml/badge.svg" alt="CI"></a>
  <a href="https://opensource.org/license/apache-2-0"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://github.com/mvnpm/raclette"><img src="https://img.shields.io/badge/java-21%2B-orange" alt="Java 21+"></a>
</p>

---

## What is Raclette?

Raclette is a lightweight link checker library for the JVM, the Java equivalent of [lychee](https://github.com/lycheeverse/lychee).

Drop it into any Java project as a Maven dependency. No external binaries, no native installs. Built on Java 21 virtual threads for high-throughput concurrent checking without callback complexity.

## Features

- **Virtual threads** for lightweight, massive concurrency out of the box
- **HTML extraction** with thorough link extraction via [JSoup](https://jsoup.org/) (anchors, images, srcsets, and more)
- **Per-host rate limiting** to respect target servers with configurable intervals and concurrency limits
- **Quirks system** to handle site-specific behaviors (YouTube, crates.io, GitHub)
- **Flexible filtering** with include/exclude patterns, IP filtering, false positive detection
- **File and website checking** for local files, directories, globs, or remote URLs
- **Static site support** with a one-liner for generated sites (Hugo, Jekyll, Roq) including localhost rewriting and base path stripping
- **Fragment checking** to verify `#fragment` links point to real anchors
- **Retry with backoff** using automatic exponential backoff for transient failures
- **Configurable** with custom headers, user agent, timeouts, redirects, HTTPS enforcement

## Quick Start

### Add the dependency

```xml
<dependency>
    <groupId>io.mvnpm</groupId>
    <artifactId>raclette</artifactId>
    <version>0.1.0.CR1</version>
</dependency>
```

### Check a single URL

```java
try (Raclette raclette = Raclette.builder().build()) {
    Status status = raclette.check("https://example.com");
    if (status.isSuccess()) {
        System.out.println("Link is valid!");
    }
}
```

### Collect and check links from HTML

```java
// Collect links from HTML content
Set<Uri> links = Collector.builder()
    .base("https://example.com")
    .build()
    .collectLinks(Set.of(new Input.StringContent(html)));

// Check all collected links
try (Raclette raclette = Raclette.builder().build()) {
    for (Uri uri : links) {
        Status status = raclette.check(uri);
        if (status.isError()) {
            System.out.println("Broken: " + uri);
        }
    }
}
```

### Check a local directory

```java
Set<Uri> links = Collector.builder()
    .build()
    .collectLinks(Set.of(new Input.FsPath(Path.of("target/site"))));

try (Raclette raclette = Raclette.builder().build()) {
    for (Uri uri : links) {
        System.out.println(uri + " -> " + raclette.check(uri));
    }
}
```

### Check a static site (SSG)

For generated static sites (Hugo, Jekyll, Roq, etc.), `StaticSiteChecker` handles localhost URL rewriting, base path stripping, and parallel execution:

```java
// Zero-config
Map<Uri, Status> broken = StaticSiteChecker.check(Path.of("target/site"));

// With options
Map<Uri, Status> broken = StaticSiteChecker.builder()
    .path(Path.of("target/site"))
    .basePath("/my-project/")
    .checkRemoteLinks(true)
    .includeFragments(true)
    .build()
    .check();
```

## Configuration

```java
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

| Option | Default | Description |
|--------|---------|-------------|
| `maxRetries` | 3 | Max retry attempts for failed requests |
| `retryWaitTime` | 1s | Initial wait between retries (doubles each attempt) |
| `timeout` | 20s | Request timeout |
| `maxRedirects` | 10 | Max redirects to follow |
| `userAgent` | `raclette/0.1` | User-Agent header |
| `requireHttps` | false | Fail HTTP links if HTTPS is available |
| `allowInsecure` | false | Accept invalid SSL certificates |
| `includeFragments` | false | Verify `#fragment` targets exist |
| `includeMail` | false | Include `mailto:` links (excluded by default) |
| `excludeAllPrivate` | false | Exclude loopback, private, and link-local IPs |
| `rateLimitConfig` | 10 concurrent, 50ms | Per-host rate limiting |

## Input Sources

The `Collector` supports multiple input types:

```java
Set.of(
    new Input.StringContent("<a href='...'>"),     // Raw HTML string
    new Input.RemoteUrl("https://example.com"),    // Fetch and extract
    new Input.FsPath(Path.of("docs/")),            // File or directory
    new Input.FsGlob("docs/**/*.html")             // Glob pattern
)
```

## Architecture

```
io.mvnpm.raclette
├── collector/    Collect links from files, URLs, strings, globs
├── extract/      HTML link extraction (JSoup) and srcset parsing
├── filter/       Include/exclude patterns, IP filtering, false positives
├── checker/      Website (HTTP) and file checking
├── quirks/       Site-specific URL rewriting (YouTube, crates.io, GitHub)
├── ratelimit/    Per-host rate limiting with virtual-thread-friendly semaphores
└── types/        Uri, Status, ErrorKind, RawUri
```

## Inspired by lychee

Raclette is heavily inspired by [lychee](https://github.com/lycheeverse/lychee), the excellent link checker written in Rust. We follow lychee's design philosophy (filtering logic, quirks system, and overall architecture) and bring it to the JVM.

## Trivia

Raclette is named after the Swiss cheese dish, a nod to the original lychee link checker. Where lychee is a fruit, raclette is melted cheese: warm, comforting, and makes everything better. Also, it *grates* through your links.

## License

[Apache License 2.0](https://opensource.org/license/apache-2-0)

## Credits

Built by [Andy Damevin](https://github.com/ia3andy), part of the [mvnpm](https://github.com/mvnpm) ecosystem.
