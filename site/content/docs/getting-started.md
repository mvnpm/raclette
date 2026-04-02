---
title: Getting Started
description: Add Raclette to your project and check your first link
layout: docs
---

## Add the dependency

```xml
<dependency>
    <groupId>io.mvnpm</groupId>
    <artifactId>raclette</artifactId>
    <version>0.1.0.CR1</version>
</dependency>
```

## Check a single URL

The simplest way to use Raclette — check one link:

```java
import io.mvnpm.raclette.Raclette;
import io.mvnpm.raclette.types.Status;

try (Raclette raclette = Raclette.builder().build()) {
    Status status = raclette.check("https://example.com");
    if (status.isSuccess()) {
        System.out.println("Link is valid!");
    }
}
```

## Build a custom client

The builder is highly customizable:

```java
try (Raclette raclette = Raclette.builder()
        .maxRetries(3)
        .timeout(Duration.ofSeconds(10))
        .userAgent("my-app/1.0")
        .excludeAllPrivate(true)
        .excludes(".*example\\.com.*")
        .build()) {
    Status status = raclette.check("https://github.com");
    assert status.isSuccess();
}
```

All options set on the builder apply to every link check. See [Configuration](../configuration) for the full list.

## Collect and check links from HTML

For checking all links in an HTML document, use the `Collector` to extract links, then check each one:

```java
import io.mvnpm.raclette.Raclette;
import io.mvnpm.raclette.collector.Collector;
import io.mvnpm.raclette.collector.Input;
import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

String html = """
    <html>
        <a href="https://github.com">GitHub</a>
        <a href="https://example.com/broken">Broken</a>
    </html>""";

Set<Uri> links = Collector.builder()
    .build()
    .collectLinks(Set.of(new Input.StringContent(html)));

try (Raclette raclette = Raclette.builder().build()) {
    for (Uri uri : links) {
        Status status = raclette.check(uri);
        if (status.isError()) {
            System.out.println("Broken: " + uri);
        }
    }
}
```

## Check a local directory

Check all HTML files in a directory — perfect for generated sites:

```java
Set<Uri> links = Collector.builder()
    .build()
    .collectLinks(Set.of(new Input.FsPath(Path.of("target/site"))));

try (Raclette raclette = Raclette.builder().build()) {
    for (Uri uri : links) {
        Status status = raclette.check(uri);
        System.out.println(uri + " -> " + status);
    }
}
```

## Next steps

- [Configuration](../configuration) — all builder options
- [Collecting Links](../collecting-links) — input sources and base URL resolution
- [Excluding Links](../excluding-links) — filtering patterns
