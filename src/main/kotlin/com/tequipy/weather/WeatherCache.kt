package com.tequipy.weather

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import kotlin.math.roundToLong

class WeatherCache(
    config: AppConfig.CacheConfig,
    registry: MeterRegistry? = null,
) {
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(config.ttlSeconds))
        .maximumSize(config.maxSize)
        .build<String, CachedWeather>()

    private val hitCounter = registry?.let {
        Counter.builder("weather_cache_requests").tag("result", "hit").register(it)
    }
    private val missCounter = registry?.let {
        Counter.builder("weather_cache_requests").tag("result", "miss").register(it)
    }

    fun get(lat: Double, lon: Double, recordMetrics: Boolean = true): CachedWeather? {
        val result = cache.getIfPresent(coordKey(lat, lon))
        if (recordMetrics) {
            if (result != null) hitCounter?.increment() else missCounter?.increment()
        }
        return result
    }

    fun put(lat: Double, lon: Double, data: CachedWeather) {
        cache.put(coordKey(lat, lon), data)
    }

    companion object {
        fun coordKey(lat: Double, lon: Double): String {
            val latKey = (lat * 100).roundToLong()
            val lonKey = (lon * 100).roundToLong()
            return "$latKey,$lonKey"
        }
    }
}
