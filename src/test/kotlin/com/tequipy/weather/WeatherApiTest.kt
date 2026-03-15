package com.tequipy.weather

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        }
        block()
    }

    @Test
    fun `metrics endpoint returns prometheus data`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "weather.upstream.baseUrl" to "http://fake-meteo",
                "weather.upstream.timeoutMs" to "1000",
                "weather.cache.ttlSeconds" to "60",
                "weather.cache.maxSize" to "1000",
            )
        }
        application { module() }

        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<String>()
        assertTrue(body.contains("ktor_http_server_requests"))
        assertTrue(body.contains("weather_cache_requests"))
    }

    @Test
    fun `health endpoints return UP`() = withApp {
        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
        assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
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
        assertEquals(13.41, body.location.lon)
        assertTrue(body.retrievedAt.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")))
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
    fun `boundary coordinates are accepted`() = withApp {
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/weather/current?lat=0&lon=0").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/weather/current?lat=-90&lon=-180").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/weather/current?lat=90&lon=180").status)
    }

    @Test
    fun `boundary coordinates just outside range return 400`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/weather/current?lat=-90.1&lon=0").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/weather/current?lat=0&lon=180.1").status)
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
        val barrier = CompletableDeferred<Unit>()
        val mockEngine = MockEngine { _ ->
            callCount++
            barrier.await() // hold until all requests are in-flight
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
            barrier.complete(Unit) // release upstream response
            val responses = requests.awaitAll()
            responses.forEach { assertEquals(HttpStatusCode.OK, it.status) }
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `upstream failure does not deadlock subsequent requests`() = testApplication {
        var callCount = 0
        val mockEngine = MockEngine { _ ->
            callCount++
            if (callCount == 1) {
                respond("not json", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(upstreamJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
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

        // First request fails (malformed JSON from upstream → 502)
        val first = client.get("/api/v1/weather/current?lat=52.52&lon=13.41")
        assertEquals(HttpStatusCode.BadGateway, first.status)

        // Second request should succeed (mutex cleaned up by finally)
        val second = client.get("/api/v1/weather/current?lat=52.52&lon=13.41")
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(2, callCount)
    }
}
