package com.tequipy.weather

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class WeatherIntegrationTest {

    @Test
    fun `fetches real weather data from Open-Meteo`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "weather.upstream.baseUrl" to "https://api.open-meteo.com",
                "weather.upstream.timeoutMs" to "5000",
                "weather.cache.ttlSeconds" to "60",
                "weather.cache.maxSize" to "1000",
            )
        }
        application { module() }

        val response = createClient {
            install(ContentNegotiation) { json() }
        }.get("/api/v1/weather/current?lat=52.52&lon=13.41")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<WeatherResponse>()
        assertEquals("open-meteo", body.source)
        assertEquals(52.52, body.location.lat)
        assertEquals(13.41, body.location.lon)
        assertTrue(body.current.temperatureC in -40.0..50.0)
    }

    @Test
    fun `invalid request returns 400 with structured error`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "weather.upstream.baseUrl" to "https://api.open-meteo.com",
                "weather.upstream.timeoutMs" to "5000",
                "weather.cache.ttlSeconds" to "60",
                "weather.cache.maxSize" to "1000",
            )
        }
        application { module() }

        val response = createClient {
            install(ContentNegotiation) { json() }
        }.get("/api/v1/weather/current?lat=abc&lon=13.41")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ErrorResponse>()
        assertTrue(body.error.contains("lat"))
    }

    @Test
    fun `health and metrics endpoints work with full module`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "weather.upstream.baseUrl" to "https://api.open-meteo.com",
                "weather.upstream.timeoutMs" to "5000",
                "weather.cache.ttlSeconds" to "60",
                "weather.cache.maxSize" to "1000",
            )
        }
        application { module() }

        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
        assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)

        val metrics = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, metrics.status)
        assertTrue(metrics.body<String>().contains("ktor_http_server_requests"))
    }
}
