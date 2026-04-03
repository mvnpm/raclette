---
title: Overview
description: What is Raclette and why should you use it?
layout: docs
---

## What is Raclette?

Raclette is a fast, lightweight link checker library for the JVM, the Java equivalent of [lychee](https://github.com/lycheeverse/lychee). It finds broken hyperlinks and mail addresses in your HTML files and websites. It's simple, lightweight, and easy to embed in any Java project.

Raclette is perfect for checking documentation sites, static sites, and generated HTML content. Drop it into your build pipeline as a Maven dependency with no external binaries or native installs required.

## Why Raclette?

- **JVM-native** with no shelling out to external tools. Raclette runs in your JVM process, making it easy to integrate with any Java, Kotlin, or Quarkus project.
- **Virtual threads** built on Java 21 for lightweight, high-throughput concurrency. Thousands of concurrent checks without callback complexity.
- **HTML extraction** with thorough link extraction via [JSoup](https://jsoup.org/): anchors, images, srcsets, forms, and more.
- **Rate limiting** per host with configurable intervals and concurrency limits. Respects target servers automatically.
- **Quirks system** with built-in handling for site-specific behaviors (YouTube, crates.io, GitHub markdown fragments) so you get fewer false positives.
- **Customizable** with fine-grained filtering, custom headers, timeouts, retries, HTTPS enforcement, and more via a fluent builder API.
- **Open source**, Apache 2.0 licensed.

## Inspired by lychee

Raclette is heavily inspired by [lychee](https://github.com/lycheeverse/lychee), the excellent async link checker written in Rust. We follow lychee's design philosophy (filtering logic, quirks system, rate limiting, and overall architecture) and bring it to the JVM.

## Trivia

### Project Name

The name lychee is a play on *link checker*. Raclette continues the food theme, named after the 450-year-old cheese dish, claimed by both Switzerland and France. Where lychee is a fruit, raclette is melted cheese: warm, comforting, and makes everything better. Also, it *grates* through your links.
