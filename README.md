# Weather Proxy API

REST API that proxies current weather from [Open-Meteo](https://open-meteo.com/). Kotlin + Ktor.

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

**Caching**: Per-pod in-memory (Caffeine, 60s TTL). No shared cache — each pod caches independently.

**Observability**: JSON logs, Prometheus metrics at `/metrics`, health probes at `/health/live` and `/health/ready`.

## Documentation

- [API Reference](docs/api.md)
- [Design Decisions](docs/decisions.md)
- [Deployment](docs/deployment.md)
