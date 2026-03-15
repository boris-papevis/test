package com.tequipy.weather

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class WeatherService(
    private val client: OpenMeteoClient,
    private val cache: WeatherCache,
) {
    private val inFlight = mutableMapOf<String, Mutex>()
    private val mapLock = Mutex()

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse {
        cache.get(lat, lon)?.let { return it }

        val key = WeatherCache.coordKey(lat, lon)
        val mutex = mapLock.withLock { inFlight.getOrPut(key) { Mutex() } }

        return mutex.withLock {
            try {
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
                response
            } finally {
                mapLock.withLock { inFlight.remove(key) }
            }
        }
    }
}
