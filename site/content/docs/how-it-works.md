---
title: How It Works
description: Raclette's architecture and request pipeline
layout: docs
---

## Architecture

Raclette is composed of several cooperating modules:

```
Collector → extracts URIs from inputs (independent)

Raclette (facade)
  └── Client (dispatcher)
        ├── Filter → excludes URIs by pattern/IP/scheme
        └── Check by scheme:
              ├── file:// → FileChecker
              └── http(s):// → HostPool → WebsiteChecker
```

## Request Pipeline

When you call `raclette.check(uri)`:

1. **Filter** — the URI is checked against exclude/include patterns, IP rules, and false positive detection. Excluded URIs return immediately with an `Excluded` status.

2. **Route** — the URI is dispatched based on its scheme:
   - `file://` goes to the FileChecker
   - `http://` and `https://` go through rate limiting, then to the WebsiteChecker
   - `mailto:` and `tel:` are handled by the filter

3. **Check** — the appropriate checker validates the URI and returns a `Status`.

## FileChecker

The FileChecker resolves local file paths and optionally validates fragments:

- Tries the path as-is, then with fallback extensions (e.g. `.html`)
- Resolves directories using index files (e.g. `index.html`)
- Optionally parses HTML to verify `#fragment` targets exist

## WebsiteChecker

The WebsiteChecker handles HTTP requests with:

- **Quirks** — site-specific URL rewrites applied before the request (YouTube, crates.io, GitHub)
- **Retries** — exponential backoff for transient failures (configurable)
- **Redirect following** — up to a configurable maximum
- **HTTPS enforcement** — optionally checks if HTTP links can be upgraded
- **Custom headers** — applied to every request

## Rate Limiting

HTTP requests go through the `HostPool`, which enforces per-host limits:

- **Concurrency semaphore** — limits in-flight requests per host
- **Rate limit semaphore** — enforces a minimum interval between requests, refilled by a timer

Both semaphores use `Semaphore.acquire()`, which is virtual-thread-friendly — no `Thread.sleep()` or `synchronized` blocks.

## Virtual Threads

Raclette is built on Java 21 virtual threads. All blocking operations (HTTP requests, semaphore waits, file I/O) run on virtual threads, giving you lightweight concurrency without callback complexity.

## Link Extraction

The `HtmlExtractor` uses JSoup to parse HTML and extract URIs from elements like `<a>`, `<img>`, `<link>`, `<script>`, `<source>`, `<form>`, and `srcset` attributes. Bare email addresses in text content are also detected.

## Quirks

Some websites need special handling to avoid false positives. The quirks system rewrites URLs before checking:

- **YouTube** — rewrites video pages to thumbnail URLs
- **crates.io** — adds `Accept: text/html` header
- **GitHub** — rewrites markdown URLs for fragment checking
