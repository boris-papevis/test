package com.tequipy.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeatherCacheTest {

    private val cache = WeatherCache(AppConfig.CacheConfig(ttlSeconds = 60, maxSize = 100))

    private val sample = WeatherResponse(
        location = Location(52.52, 13.41),
        current = CurrentWeather(temperatureC = 5.0, windSpeedKmh = 10.0),
        retrievedAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun `returns null on cache miss`() {
        assertNull(cache.get(52.52, 13.41))
    }

    @Test
    fun `returns cached value on hit`() {
        cache.put(52.52, 13.41, sample)
        assertEquals(sample, cache.get(52.52, 13.41))
    }

    @Test
    fun `different coordinates are separate entries`() {
        cache.put(52.52, 13.41, sample)
        assertNull(cache.get(48.85, 2.35))
    }
}
