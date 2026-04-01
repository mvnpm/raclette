---
title: Fragment Checking
description: Verify that #fragment targets exist in HTML files
layout: docs
---

## What Are Fragments?

A fragment is the `#section` part of a URL like `page.html#section`. It points to a specific element in the target page — an element with a matching `id` or `name` attribute.

By default, Raclette only checks that the target file or URL exists. Fragment checking goes further and verifies that the anchor target actually exists in the page.

## Enable Fragment Checking

```java
Raclette raclette = Raclette.builder()
    .includeFragments(true)
    .build();
```

## How It Works

When `includeFragments` is enabled and a URI contains a fragment:

1. Raclette checks that the target file exists (as usual)
2. It parses the HTML and collects all `id` and `name` attributes
3. It checks if the fragment matches any of those attributes
4. If no match is found, the check returns an `InvalidFragment` error

Fragment sets are cached per file, so checking multiple anchors in the same file doesn't re-parse it.

## GitHub Compatibility

GitHub prefixes heading anchors with `user-content-`. Raclette handles this automatically — a fragment `#installation` will match both `id="installation"` and `id="user-content-installation"`.

## Directory and File Resolution

Fragment checking works together with `fallbackExtensions` and `indexFiles`:

```java
Raclette raclette = Raclette.builder()
    .includeFragments(true)
    .fallbackExtensions(List.of("html"))
    .indexFiles(List.of("index.html"))
    .build();
```

- `docs/#intro` resolves to `docs/index.html` and checks for `#intro`
- `page#section` tries `page.html` if `page` doesn't exist, then checks `#section`
