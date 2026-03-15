# Design Decisions

## Framework: Ktor (not Spring Boot)

Spring Boot is the default for Kotlin backends, but it's overkill here. A single-endpoint proxy doesn't need dependency injection, auto-configuration, or 30MB of framework code.

Ktor is built by JetBrains, coroutine-native, and modular — you only pull in what you use. The result is a ~10MB fat JAR with sub-second startup. It also comes with a built-in HTTP client, so the upstream call doesn't need a separate dependency.

Trade-off: smaller ecosystem, fewer StackOverflow answers. Acceptable for this scope.

## Cache: Caffeine (in-memory)

The requirement is "cache by (lat, lon) for 60 seconds." Caffeine is the standard JVM in-memory cache — high performance, configurable TTL, bounded size.

Why not Redis: adds infrastructure for a simple TTL cache. In a multi-instance deployment, each pod caches independently — this means slightly more upstream calls but zero shared state, which simplifies operations. For a weather proxy with 60s TTL, the duplication is negligible.

Cache key is integer-encoded at 2 decimal places: `round(lat*100),round(lon*100)`. This means `52.52` and `52.5200` hit the same entry, and coordinates differing only in the 3rd decimal place or beyond are coalesced. This matches Open-Meteo's ~0.25-degree grid resolution while avoiding floating-point key ambiguity.

## HTTP Client: Ktor CIO with timeout

The upstream timeout (1s default) covers both connect and request phases. On timeout, the API returns 504 Gateway Timeout with a structured error body.

The Open-Meteo upstream version (`/v1/forecast`) is hardcoded — it's part of the integration contract, not a runtime config. If Open-Meteo releases v2, we'd update the code and test, not flip a flag.

## API Versioning: URL path (`/api/v1/...`)

URL-based versioning is explicit, debuggable, and trivial to extend. If v2 is needed, new routes are added alongside v1 without breaking existing consumers.

Alternatives considered:
- Header-based (`Accept: application/vnd.tequipy.v1+json`) — more "correct" per REST purists, but harder to test with curl and unnecessary for this scope.

## No Swagger/OpenAPI

For a single endpoint, a generated Swagger UI adds dependencies and configuration overhead for minimal value. The API contract is documented in `docs/api.md` with curl examples that are directly runnable.

In a larger service with 10+ endpoints, OpenAPI generation (via ktor-swagger or similar) would be worth the setup cost.

## Logging

Request logging uses Ktor's CallLogging plugin at INFO level. In production, the log level is controlled via logback.xml or environment-specific configuration — set to DEBUG for verbose request/response logging during troubleshooting.

## Containerization

Multi-stage Docker build: Gradle builds the shadow JAR, then only the JRE and JAR are copied to the runtime image (Alpine-based, ~100MB total).

Kubernetes manifests define resource limits, liveness probe (`/health/live`) and readiness probe (`/health/ready`), and 2 replicas. Readiness gates on app initialization only — it does not check upstream availability, so pods stay in rotation to serve cached data when the upstream is down. Since the cache is in-memory per pod, the app is stateless and scales horizontally without coordination.
