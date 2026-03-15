package com.tequipy.weather

import io.ktor.server.config.*

data class AppConfig(
    val upstream: UpstreamConfig,
    val cache: CacheConfig,
) {
    data class UpstreamConfig(
        val baseUrl: String,
        val timeoutMs: Long,
    )

    data class CacheConfig(
        val ttlSeconds: Long,
        val maxSize: Long,
    )

    companion object {
        fun from(config: ApplicationConfig): AppConfig = AppConfig(
            upstream = UpstreamConfig(
                baseUrl = config.property("weather.upstream.baseUrl").getString(),
                timeoutMs = config.property("weather.upstream.timeoutMs").getString().toLong(),
            ),
            cache = CacheConfig(
                ttlSeconds = config.property("weather.cache.ttlSeconds").getString().toLong(),
                maxSize = config.property("weather.cache.maxSize").getString().toLong(),
            ),
        )
    }
}
