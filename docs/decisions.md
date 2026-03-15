# Design Decisions

## Framework: Ktor

Spring Boot is overkill for a single-endpoint proxy. Ktor is coroutine-native and modular — ~10MB fat JAR, sub-second startup, built-in HTTP client.

Trade-off: smaller ecosystem. Fine for this scope.

## Cache: Caffeine (in-memory)

Caffeine handles "cache by (lat, lon) for 60 seconds" with TTL and bounded size.

No Redis — avoids extra infrastructure. Each pod caches independently, which means some duplicate upstream calls but no shared state.

Cache key: `round(lat*100),round(lon*100)` — rounds to 2 decimal places (~1.1 km), matches Open-Meteo's grid resolution, avoids floating-point key issues.

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
