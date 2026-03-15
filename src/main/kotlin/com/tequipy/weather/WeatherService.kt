package com.tequipy.weather

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class WeatherService(
    private val client: OpenMeteoClient,
    private val cache: WeatherCache,
) {
    private val inFlight = ConcurrentHashMap<String, Mutex>()

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse {
        cache.get(lat, lon)?.let { return toResponse(lat, lon, it) }

        val key = WeatherCache.coordKey(lat, lon)
        val mutex = inFlight.computeIfAbsent(key) { Mutex() }

        return mutex.withLock {
            try {
                cache.get(lat, lon, recordMetrics = false)?.let { return toResponse(lat, lon, it) }

                val upstream = client.fetchCurrent(lat, lon)
                val data = CachedWeather(
                    current = CurrentWeather(
                        temperatureC = upstream.current.temperature2m,
                        windSpeedKmh = upstream.current.windSpeed10m,
                    ),
                    retrievedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(),
                )
                cache.put(lat, lon, data)
                toResponse(lat, lon, data)
            } finally {
                inFlight.remove(key, mutex)
            }
        }
    }

    private fun toResponse(lat: Double, lon: Double, data: CachedWeather) = WeatherResponse(
        location = Location(lat = lat, lon = lon),
        current = data.current,
        retrievedAt = data.retrievedAt,
    )
}
