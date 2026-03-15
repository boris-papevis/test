# Weather Proxy API

REST API that proxies current weather data from [Open-Meteo](https://open-meteo.com/). Built with Kotlin and Ktor.

## Quick Start

```bash
# With Java 17+
./gradlew run

# With Docker
docker compose up --build
```

```bash
curl "http://localhost:8080/api/v1/weather/current?lat=52.52&lon=13.41"
```

## Tests

```bash
./gradlew test                # Unit tests (mock upstream)
./gradlew integrationTest     # Against real Open-Meteo
```

## Architecture

**Caching**: Per-pod in-memory cache (Caffeine, 60s TTL). No shared cache. If upstream goes down, cached locations still work; cache misses return 502. Shared cache (Redis) could improve this but adds complexity.

**Observability**: JSON logs (Logstash encoder), Prometheus metrics at `/metrics`, health probes at `/health/live` and `/health/ready`. Readiness does not check upstream — pods stay in rotation to serve cached data.

## Documentation

- [API Reference](docs/api.md) — endpoints, parameters, error codes
- [Design Decisions](docs/decisions.md) — why Ktor, caching strategy, trade-offs
- [Deployment](docs/deployment.md) — Docker, Kubernetes, configuration
