# Deployment

## Local (Docker Compose)

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`. No Java installation required.

## Configuration

All settings are externalized via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | 8080 | Server port |
| `UPSTREAM_BASE_URL` | `https://api.open-meteo.com` | Open-Meteo API base URL |
| `UPSTREAM_TIMEOUT_MS` | 1000 | Upstream request timeout in milliseconds |
| `CACHE_TTL_SECONDS` | 60 | Cache TTL per (lat, lon) pair |
| `CACHE_MAX_SIZE` | 1000 | Maximum number of cached entries |

Override in Docker Compose by editing `docker-compose.yml` or using an `.env` file.

## Kubernetes

Prerequisites: a container registry and a running cluster.

```bash
# Build and push the image
docker build -t your-registry/weather-proxy:1.0.0 .
docker push your-registry/weather-proxy:1.0.0

# Update the image in k8s/deployment.yaml, then apply
kubectl apply -f k8s/
```

The deployment includes:
- 2 replicas (stateless, each with its own in-memory cache)
- Resource requests (100m CPU, 128Mi RAM) and limits (500m CPU, 256Mi RAM)
- Liveness probe: `GET /health/live` every 15s (initial delay 10s)
- Readiness probe: `GET /health/ready` every 10s (initial delay 5s)
- Configuration via environment variables in the deployment spec

To scale:

```bash
kubectl scale deployment weather-proxy --replicas=4
```

No shared state between pods — scales horizontally.

## CI

GitHub Actions runs on every push and pull request to `main`:

1. Checks out code
2. Sets up JDK 17 (Corretto)
3. Runs `./gradlew build` (compile + unit tests)
4. Builds the Docker image

Integration tests (hitting real Open-Meteo) are excluded from CI. Run them manually:

```bash
./gradlew integrationTest
```
