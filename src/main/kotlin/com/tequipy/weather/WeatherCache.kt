package com.tequipy.weather

import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration
import kotlin.math.roundToLong

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

    private fun key(lat: Double, lon: Double): String {
        val latKey = (lat * 100).roundToLong()
        val lonKey = (lon * 100).roundToLong()
        return "$latKey,$lonKey"
    }
}
