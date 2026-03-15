package com.tequipy.weather

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.math.roundToLong

class WeatherService(
    private val client: OpenMeteoClient,
    private val cache: WeatherCache,
) {
    private val inFlight = mutableMapOf<String, Mutex>()
    private val mapLock = Mutex()

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse {
        cache.get(lat, lon)?.let { return it }

        val key = "${(lat * 100).roundToLong()},${(lon * 100).roundToLong()}"
        val mutex = mapLock.withLock { inFlight.getOrPut(key) { Mutex() } }

        return mutex.withLock {
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
            mapLock.withLock { inFlight.remove(key) }
            response
        }
    }
}
