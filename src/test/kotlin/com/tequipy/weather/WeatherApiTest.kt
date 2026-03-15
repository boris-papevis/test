package com.tequipy.weather

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherApiTest {

    private val upstreamJson = """
        {
          "latitude": 52.52,
          "longitude": 13.419998,
          "current": {
            "temperature_2m": 5.3,
            "wind_speed_10m": 12.1
          }
        }
    """.trimIndent()

    private fun withApp(
        mockResponse: String = upstreamJson,
        mockStatus: HttpStatusCode = HttpStatusCode.OK,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        environment {
            config = MapApplicationConfig(
                "weather.upstream.baseUrl" to "http://fake-meteo",
                "weather.upstream.timeoutMs" to "1000",
                "weather.cache.ttlSeconds" to "60",
                "weather.cache.maxSize" to "1000",
            )
        }
        application {
            val mockEngine = MockEngine { _ ->
                respond(mockResponse, mockStatus, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val config = AppConfig.from(environment.config)
            val client = OpenMeteoClient(config.upstream, mockEngine)
            val cache = WeatherCache(config.cache)
            val service = WeatherService(client, cache)

            configureSerialization()
            configureRouting(service)
            markReady()
        }
        block()
    }

    @Test
    fun `liveness endpoint returns UP`() = withApp {
        val response = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `readiness endpoint always returns UP`() = withApp {
        val response = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `returns normalized weather response`() = withApp {
        val response = createClient {
            install(ContentNegotiation) { json() }
        }.get("/api/v1/weather/current?lat=52.52&lon=13.41")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<WeatherResponse>()
        assertEquals("open-meteo", body.source)
        assertEquals(5.3, body.current.temperatureC)
        assertEquals(12.1, body.current.windSpeedKmh)
        assertEquals(52.52, body.location.lat)
    }

    @Test
    fun `missing lat returns 400`() = withApp {
        val response = client.get("/api/v1/weather/current?lon=13.41")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `invalid lat returns 400`() = withApp {
        val response = client.get("/api/v1/weather/current?lat=abc&lon=13.41")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `lat out of range returns 400`() = withApp {
        val response = client.get("/api/v1/weather/current?lat=91&lon=13.41")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `lon out of range returns 400`() = withApp {
        val response = client.get("/api/v1/weather/current?lat=52&lon=181")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `upstream error returns 502`() = withApp(
        mockResponse = """{"error": "server error"}""",
        mockStatus = HttpStatusCode.InternalServerError,
    ) {
        val response = createClient {
            install(ContentNegotiation) { json() }
        }.get("/api/v1/weather/current?lat=52.52&lon=13.41")
        assertEquals(HttpStatusCode.BadGateway, response.status)
        val body = response.body<ErrorResponse>()
        assertTrue(body.error.contains("500"))
    }

    @Test
    fun `error responses have structured ErrorResponse format`() = withApp {
        val response = createClient {
            install(ContentNegotiation) { json() }
        }.get("/api/v1/weather/current?lon=13.41")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ErrorResponse>()
        assertTrue(body.error.contains("lat"))
    }

    @Test
    fun `cached response avoids second upstream call`() = testApplication {
        var callCount = 0
        val mockEngine = MockEngine { _ ->
            callCount++
            respond(upstreamJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        environment {
            config = MapApplicationConfig(
                "weather.upstream.baseUrl" to "http://fake-meteo",
                "weather.upstream.timeoutMs" to "1000",
                "weather.cache.ttlSeconds" to "60",
                "weather.cache.maxSize" to "1000",
            )
        }
        application {
            val config = AppConfig.from(environment.config)
            val client = OpenMeteoClient(config.upstream, mockEngine)
            val cache = WeatherCache(config.cache)
            val service = WeatherService(client, cache)
            configureSerialization()
            configureRouting(service)
        }

        client.get("/api/v1/weather/current?lat=52.52&lon=13.41")
        client.get("/api/v1/weather/current?lat=52.52&lon=13.41")
        assertEquals(1, callCount)
    }

    @Test
    fun `concurrent requests for same coords make only one upstream call`() = testApplication {
        var callCount = 0
        val mockEngine = MockEngine { _ ->
            callCount++
            delay(100) // simulate upstream latency
            respond(upstreamJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        environment {
            config = MapApplicationConfig(
                "weather.upstream.baseUrl" to "http://fake-meteo",
                "weather.upstream.timeoutMs" to "5000",
                "weather.cache.ttlSeconds" to "60",
                "weather.cache.maxSize" to "1000",
            )
        }
        application {
            val config = AppConfig.from(environment.config)
            val client = OpenMeteoClient(config.upstream, mockEngine)
            val cache = WeatherCache(config.cache)
            val service = WeatherService(client, cache)
            configureSerialization()
            configureRouting(service)
        }

        coroutineScope {
            val requests = (1..5).map {
                async { client.get("/api/v1/weather/current?lat=52.52&lon=13.41") }
            }
            val responses = requests.awaitAll()
            responses.forEach { assertEquals(HttpStatusCode.OK, it.status) }
        }
        assertEquals(1, callCount)
    }
}
