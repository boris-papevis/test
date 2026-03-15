# Design Decisions

## Framework: Ktor

Spring Boot is overkill for a single-endpoint proxy. Ktor is coroutine-native and modular — ~10MB fat JAR, sub-second startup, built-in HTTP client.

Trade-off: smaller ecosystem. Fine for this scope.

## Cache: Caffeine (in-memory)

Caffeine handles "cache by (lat, lon) for 60 seconds" with TTL and bounded size.

No Redis — avoids extra infrastructure. Each pod caches independently, which means some duplicate upstream calls but no shared state.

Cache key: `round(lat*100),round(lon*100)` — rounds to 2 decimal places (~1.1 km), matches Open-Meteo's grid resolution, avoids floating-point key issues.

## HTTP Client: Ktor CIO

Upstream timeout: 1s default (connect + request + socket). On timeout, returns 504 with structured error.

The upstream path (`/v1/forecast`) is hardcoded — it's an integration contract, not config.

## API Versioning: URL path (`/api/v1/...`)

Simple, easy to test with curl, easy to extend.

## No Swagger/OpenAPI

Single endpoint — docs in `api.md` with runnable curl examples are enough.

## Logging

Structured JSON via Logstash encoder. CallLogging plugin at INFO level. Pod hostname in every log line.

## Containerization

Multi-stage Docker build (Gradle → Alpine JRE, ~100MB).

K8s: resource limits, liveness/readiness probes, 2 replicas. Readiness does not check upstream — pods keep serving cached data. Stateless, scales horizontally.
