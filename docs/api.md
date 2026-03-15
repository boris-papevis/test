# API Reference

Base URL: `http://localhost:8080`

## `GET /api/v1/weather/current`

Returns current weather from Open-Meteo.

### Parameters

| Name | Type | Required | Constraints | Example |
|------|------|----------|-------------|---------|
| `lat` | number | yes | -90 to 90 | 52.52 |
| `lon` | number | yes | -180 to 180 | 13.41 |

### Success Response (200)

```json
{
  "location": {
    "lat": 52.52,
    "lon": 13.41
  },
  "current": {
    "temperatureC": 5.3,
    "windSpeedKmh": 12.1
  },
  "source": "open-meteo",
  "retrievedAt": "2026-01-11T10:12:54Z"
}
```

Notes:
- `location` echoes the request parameters.
- `retrievedAt` is when data was fetched. Cached responses keep the original timestamp.
- Responses are cached for 60s per (lat, lon).

### Error Responses

| Status | Condition | Body |
|--------|-----------|------|
| 400 | Missing, non-numeric, or out-of-range `lat`/`lon` | `{"error": "Missing or invalid 'lat' parameter"}` |
| 502 | Open-Meteo returned a non-2xx response | `{"error": "Open-Meteo returned 500: ..."}` |
| 504 | Open-Meteo did not respond within timeout | `{"error": "Upstream timeout"}` |
| 500 | Any other server error | `{"error": "Internal server error"}` |

### Examples

```bash
# Berlin
curl "http://localhost:8080/api/v1/weather/current?lat=52.52&lon=13.41"

# London
curl "http://localhost:8080/api/v1/weather/current?lat=51.51&lon=-0.13"

# Missing parameter
curl "http://localhost:8080/api/v1/weather/current?lat=52.52"
# -> 400: {"error": "Missing or invalid 'lon' parameter"}

# Out of range
curl "http://localhost:8080/api/v1/weather/current?lat=91&lon=13.41"
# -> 400: {"error": "lat must be between -90 and 90"}
```

## `GET /health/live`

Liveness probe. Returns 200 if the app is running.

```json
{"status": "UP", "version": "1.0.0"}
```

## `GET /health/ready`

Readiness probe. Always returns 200 — does not gate on upstream health so pods can serve cached data.

```json
{"status": "UP"}
```

## `GET /metrics`

Prometheus metrics: request latency, error rates, cache hit/miss, JVM stats.
