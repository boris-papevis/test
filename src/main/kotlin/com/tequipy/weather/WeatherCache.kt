package com.tequipy.weather

import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration

class WeatherCache(config: AppConfig.CacheConfig) {
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(config.ttlSeconds))
        .maximumSize(config.maxSize)
        .build<String, WeatherResponse>()

    fun get(lat: Double, lon: Double): WeatherResponse? =
        cache.getIfPresent(key(lat, lon))

    fun put(lat: Double, lon: Double, response: WeatherResponse) {
        cache.put(key(lat, lon), response)
    }

    private fun key(lat: Double, lon: Double): String = "$lat,$lon"
}
