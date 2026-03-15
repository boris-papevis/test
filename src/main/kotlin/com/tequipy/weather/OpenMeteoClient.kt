package com.tequipy.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OpenMeteoClient(
    private val config: AppConfig.UpstreamConfig,
    engine: io.ktor.client.engine.HttpClientEngine? = null,
) {
    private val http = HttpClient(engine ?: CIO.create()) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeoutMs
            connectTimeoutMillis = config.timeoutMs
        }
    }

    suspend fun fetchCurrent(lat: Double, lon: Double): OpenMeteoResponse =
        http.get("${config.baseUrl}/v1/forecast") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter("current", "temperature_2m,wind_speed_10m")
        }.body()
}
