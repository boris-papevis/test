# Design Decisions

## Framework: Ktor

Spring Boot is overkill for a single-endpoint proxy. Ktor is coroutine-native and modular — ~10MB fat JAR, sub-second startup, built-in HTTP client.

Trade-off: smaller ecosystem. Fine for this scope.

## Cache: Caffeine (in-memory)

Chose Caffeine over Guava Cache because Caffeine's W-TinyLFU eviction policy has near-optimal hit rates — significantly better than Guava's LRU, especially under skewed access patterns (some locations queried far more than others). Guava is in maintenance mode; Caffeine is its intended successor.

Chose in-process cache over Redis to avoid extra infrastructure. Trade-off: each pod caches independently, so some duplicate upstream calls occur. Acceptable because Open-Meteo is free-tier and the duplication is bounded by TTL.

### Eviction: W-TinyLFU (Caffeine default)

Caffeine's W-TinyLFU combines recency and frequency to decide what to evict. Compared to plain LRU, it avoids cache pollution from one-off requests (a user querying a random location once won't evict a frequently-requested city). Compared to pure LFU, it adapts to changing access patterns. This matters here because weather APIs have heavily skewed access — a few popular cities dominate traffic.

No configuration needed — Caffeine applies W-TinyLFU automatically when `maximumSize` is set.

### Cache size: 1000 entries

Default `maxSize=1000`. Each entry is one grid cell, so this covers ~1000 distinct locations. At ~1 KB per entry, total memory is ~1 MB — negligible relative to JVM heap.

1000 was chosen as a conservative default for single-region deployments. A country-wide service might need 5–10k; a global one 50k+. Configurable via `CACHE_MAX_SIZE` env var.

### TTL: 60 seconds

Chose 60s over shorter (too many upstream calls) or longer (stale weather data). Weather conditions don't change meaningfully within a minute, and Open-Meteo updates its models hourly, so caching longer than ~300s gives diminishing returns. 60s is a safe starting point. Configurable via `CACHE_TTL_SECONDS`.

### Cache key: grid cell rounding

Key format: `round(lat*100),round(lon*100)` — rounds to 2 decimal places (~1.1 km grid cells).

Chose 0.01° over finer resolution (0.001° = ~111m) because Open-Meteo snaps to its own grid anyway — finer keys would create distinct cache entries that return identical upstream data. A coarser grid (0.1° = ~11 km) would improve hit rates but risks returning weather for a noticeably different location (e.g. coast vs. inland). 0.01° balances cache efficiency against location accuracy.

Trade-off: the response echoes the original request coordinates, not the grid-snapped ones, so two requests 500m apart may return identical weather data under different reported locations. This is acceptable for the current use case.

## Serialization: kotlinx.serialization

Kotlin-native, compile-time, no reflection. Pairs naturally with Ktor's content negotiation. Jackson would work too but adds reflection overhead and a larger dependency tree.

## Server Engine: Netty

Netty is the default production engine for Ktor — mature, async, well-tested. Ktor's CIO engine is lighter but less battle-tested for production load.

## HTTP Client: Ktor CIO

Upstream timeout: 1s default (connect + request + socket). On timeout, returns 504 with structured error.

The upstream path (`/v1/forecast`) is hardcoded — it's an integration contract, not config.

## API Versioning: URL path (`/api/v1/...`)

Simple, easy to test with curl, easy to extend.

## Request Collapsing

Concurrent requests for the same (lat, lon) are coalesced into one upstream call using `ConcurrentHashMap` + coroutine `Mutex`. Without this, a burst of identical requests (e.g. a dashboard) would all hit Open-Meteo in parallel before the cache is populated.

## No Swagger/OpenAPI

Single endpoint — docs in `api.md` with runnable curl examples are enough.

## Metrics: Prometheus via Micrometer

Ktor's MicrometerMetrics plugin exposes request latency, error rates, and JVM stats at `/metrics`. Custom counter `weather_cache_requests{result=hit|miss}` tracks cache effectiveness.

No Grafana or alerting setup — those are infrastructure concerns, not app code.

## Logging

Structured JSON via Logstash encoder. CallLogging plugin at INFO level. Pod hostname in every log line.

## Containerization

Multi-stage Docker build (Gradle → Alpine JRE, ~100MB).

K8s: resource limits, liveness/readiness probes, 2 replicas. Readiness does not check upstream — pods keep serving cached data. Stateless, scales horizontally.
