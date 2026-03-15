package com.tequipy.weather

import java.time.Instant

class WeatherService(
    private val client: OpenMeteoClient,
    private val cache: WeatherCache,
) {
    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse {
        cache.get(lat, lon)?.let { return it }

        val upstream = client.fetchCurrent(lat, lon)
        val response = WeatherResponse(
            location = Location(lat = upstream.latitude, lon = upstream.longitude),
            current = CurrentWeather(
                temperatureC = upstream.current.temperature2m,
                windSpeedKmh = upstream.current.windSpeed10m,
            ),
            retrievedAt = Instant.now().toString(),
        )
        cache.put(lat, lon, response)
        return response
    }
}
