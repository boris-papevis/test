package com.tequipy.weather

import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = AppConfig.from(environment.config)
    val registry = configureMetrics()
    val client = OpenMeteoClient(config.upstream)
    val cache = WeatherCache(config.cache, registry)
    val service = WeatherService(client, cache)

    monitor.subscribe(ApplicationStopped) { client.close() }

    configureSerialization()
    configureCallLogging()
    configureRouting(service)
    this.markReady()
}
