---
title: Collecting Links
description: How to extract links from HTML files, directories, URLs, and strings
layout: docs
---

## Input Sources

The `Collector` extracts links from four input types:

```java
import io.mvnpm.raclette.collector.Collector;
import io.mvnpm.raclette.collector.Input;
import io.mvnpm.raclette.types.Uri;

Set<Uri> links = Collector.builder()
    .build()
    .collectLinks(Set.of(
        new Input.FsPath(Path.of("target/site")),        // local file or directory
        new Input.FsGlob("docs/**/*.html"),               // glob pattern
        new Input.RemoteUrl("https://example.com"),        // remote URL
        new Input.StringContent("<a href='...'>link</a>") // raw HTML string
    ));
```

### `Input.FsPath`

Checks a single file or walks a directory recursively. Hidden files and directories are skipped by default.

### `Input.FsGlob`

Matches files using glob patterns like `docs/**/*.html`.

### `Input.RemoteUrl`

Fetches the page over HTTP and extracts links from the response body. The URL itself is used as the base for resolving relative links.

### `Input.StringContent`

Parses a raw HTML string. Useful for testing or checking dynamically generated content.

## Base URL Resolution

Relative links need a base URL to resolve against. Set it on the builder:

```java
Collector.builder()
    .base("https://example.com/docs/")
    .build()
    .collectLinks(Set.of(new Input.StringContent(html)));
```

With base `https://example.com/docs/`:
- `../index.html` resolves to `https://example.com/index.html`
- `/about` resolves to `https://example.com/about`
- `page.html` resolves to `https://example.com/docs/page.html`

For `RemoteUrl` inputs, the fetched URL is automatically used as the base.

## Collector Options

| Option | Default | Description |
|--------|---------|-------------|
| `base` | none | Base URL for resolving relative links |
| `includeVerbatim` | false | Extract links from verbatim blocks (`<pre>`, `<code>`) and process non-HTML files |
| `skipHidden` | true | Skip hidden files and directories |
| `userAgent` | `raclette/0.1` | User-Agent for fetching remote URLs |

## What Gets Extracted

The collector uses JSoup to extract links from HTML elements:

- `<a href="...">`
- `<img src="...">`
- `<link href="...">`
- `<script src="...">`
- `<source src="...">`
- `<img srcset="...">` (all URLs in srcset)
- `<form action="...">`
- Bare email addresses in text content
