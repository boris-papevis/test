package com.tequipy.weather

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.plugins.*

fun Application.configureRouting(service: WeatherService) {
    install(StatusPages) {
        exception<HttpRequestTimeoutException> { call, _ ->
            call.respond(HttpStatusCode.GatewayTimeout, mapOf("error" to "Upstream timeout"))
        }
        exception<IllegalArgumentException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
        }
        exception<Throwable> { call, e ->
            this@configureRouting.log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }

    routing {
        get("/api/v1/weather/current") {
            val lat = call.parameters["lat"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("Missing or invalid 'lat' parameter")
            val lon = call.parameters["lon"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("Missing or invalid 'lon' parameter")

            require(lat in -90.0..90.0) { "lat must be between -90 and 90" }
            require(lon in -180.0..180.0) { "lon must be between -180 and 180" }

            call.respond(service.getCurrentWeather(lat, lon))
        }

        get("/health") {
            call.respond(mapOf("status" to "UP", "version" to "1.0.0"))
        }
    }
}
