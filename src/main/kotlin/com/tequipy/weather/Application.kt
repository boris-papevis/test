package com.tequipy.weather

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import org.slf4j.event.Level

fun Application.configureSerialization() {
    install(ContentNegotiation) { json() }
}

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = AppConfig.from(environment.config)
    val registry = configureMetrics()
    val client = OpenMeteoClient(config.upstream)
    val cache = WeatherCache(config.cache, registry)
    val service = WeatherService(client, cache)

    monitor.subscribe(ApplicationStopped) { client.close() }

    configureSerialization()
    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
    }
    configureRouting(service)
}
