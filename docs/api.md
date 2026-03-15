# API Reference

Base URL: `http://localhost:8080`

## `GET /api/v1/weather/current`

Returns current weather for a given location, proxied from Open-Meteo.

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
    "lon": 13.419998
  },
  "current": {
    "temperatureC": 5.3,
    "windSpeedKmh": 12.1
  },
  "source": "open-meteo",
  "retrievedAt": "2026-01-11T10:12:54.123456Z"
}
```

Notes:
- `location` values come from Open-Meteo's response (snapped to ~1km grid), so may differ slightly from the request.
- `retrievedAt` is the timestamp when the data was fetched from upstream. Cached responses retain the original timestamp.
- Responses are cached for 60s per (lat, lon) pair.

### Error Responses

| Status | Condition | Body |
|--------|-----------|------|
| 400 | Missing, non-numeric, or out-of-range `lat`/`lon` | `{"error": "Missing or invalid 'lat' parameter"}` |
| 504 | Open-Meteo did not respond within timeout | `{"error": "Upstream timeout"}` |
| 500 | Any other upstream or server error | `{"error": "Internal server error"}` |

### Examples

```bash
# Current weather in Berlin
curl "http://localhost:8080/api/v1/weather/current?lat=52.52&lon=13.41"

# Current weather in London
curl "http://localhost:8080/api/v1/weather/current?lat=51.51&lon=-0.13"

# Missing parameter
curl "http://localhost:8080/api/v1/weather/current?lat=52.52"
# -> 400: {"error": "Missing or invalid 'lon' parameter"}

# Out of range
curl "http://localhost:8080/api/v1/weather/current?lat=91&lon=13.41"
# -> 400: {"error": "lat must be between -90 and 90"}
```

## `GET /health`

Health check endpoint for load balancers and orchestrators.

### Response (200)

```json
{
  "status": "UP",
  "version": "1.0.0"
}
```
