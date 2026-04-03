---
title: Excluding Links
description: Filter links with regex patterns, IP rules, and built-in false positive detection
layout: docs
---

## Exclude Patterns

Skip links matching a regex:

```java
Raclette raclette = Raclette.builder()
    .excludes(".*example\\.com.*", ".*localhost.*")
    .build();
```

## Include Patterns

Only check links matching a regex (everything else is excluded):

```java
Raclette raclette = Raclette.builder()
    .includes(".*github\\.com.*")
    .build();
```

Include patterns take precedence over exclude patterns. If a URL matches both an include and an exclude pattern, it is **included**.

## IP Address Filtering

Exclude links pointing to specific IP ranges:

```java
Raclette raclette = Raclette.builder()
    .excludeLoopbackIps(true)   // 127.0.0.1, ::1
    .excludePrivateIps(true)    // 10/8, 172.16/12, 192.168/16
    .excludeLinkLocalIps(true)  // 169.254/16, fe80::/10
    .build();
```

Or exclude all private/reserved IPs at once:

```java
Raclette raclette = Raclette.builder()
    .excludeAllPrivate(true)  // all of the above
    .build();
```

IP filtering only applies to URLs with literal IP addresses. Hostnames are not resolved.

## Mail and Tel Links

By default, `mailto:` and `tel:` links are excluded. To include mail links:

```java
Raclette raclette = Raclette.builder()
    .includeMail(true)
    .build();
```

`tel:` links are always excluded.

## Built-in False Positive Detection

Raclette automatically excludes URLs that are known to produce false positives:

- W3C schema URLs (`www.w3.org/...`)
- Open Graph namespace (`ogp.me/ns`)
- XML-RPC endpoints (`*/xmlrpc.php`)

These are excluded unless they match an explicit include pattern.

## Filter Order

Filters are applied in this order:

1. Mail/tel scheme check
2. IP address checks (loopback, private, link-local)
3. Include patterns: if set and matching, the URL is **included** regardless of other rules
4. False positive detection
5. Exclude patterns

When include patterns are set but a URL doesn't match any of them, it is excluded even if no exclude patterns are configured.
