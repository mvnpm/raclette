---
title: Credits
description: Acknowledgements and inspiration
layout: docs
---

## Lychee

Raclette is a Java port of [lychee](https://github.com/lycheeverse/lychee), the fast async link checker written in Rust by [Matthias Endler](https://github.com/mre) and contributors. We follow lychee's design philosophy (filtering logic, quirks system, rate limiting, and overall architecture) and bring it to the JVM.

## Libraries

Raclette is built on:

- [JSoup](https://jsoup.org/) for HTML parsing and link extraction
- [java.net.http.HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html) for HTTP requests with Java 21 virtual threads

## License

Raclette is open source under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
