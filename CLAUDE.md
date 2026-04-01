# Raclette — Java Link Checker

## Project Overview

Java equivalent of [lychee](https://github.com/lycheeverse/lychee). Generic, reusable link checker library.

- **GroupId:** `io.mvnpm`
- **ArtifactId:** `raclette`
- **Java:** 21+ (virtual threads)
- **Tech stack:** JSoup (HTML parsing), java.net.http.HttpClient (HTTP)
- **Lychee reference:** `/tmp/lychee/lychee-lib/src/`

## Development Approach

- **Test-first**: Translate lychee tests to JUnit 5, then implement to make them pass
- Tests use JUnit 5, AssertJ, WireMock
- Follow esbuild-java patterns for Maven release config

## Package Structure

```
io.mvnpm.raclette
io.mvnpm.raclette.types
io.mvnpm.raclette.extract
io.mvnpm.raclette.checker
io.mvnpm.raclette.filter
io.mvnpm.raclette.ratelimit
io.mvnpm.raclette.collector
```

## Build & Test

```bash
mvn test          # Run all tests
mvn compile       # Compile only
```
