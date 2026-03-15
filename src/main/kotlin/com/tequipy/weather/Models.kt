package com.tequipy.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- API response ---

@Serializable
data class WeatherResponse(
    val location: Location,
    val current: CurrentWeather,
    val source: String = "open-meteo",
    val retrievedAt: String,
)

@Serializable
data class Location(val lat: Double, val lon: Double)

@Serializable
data class CurrentWeather(val temperatureC: Double, val windSpeedKmh: Double)

// --- Open-Meteo upstream response (subset) ---

@Serializable
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val current: OpenMeteoCurrent,
)

@Serializable
data class OpenMeteoCurrent(
    @SerialName("temperature_2m") val temperature2m: Double,
    @SerialName("wind_speed_10m") val windSpeed10m: Double,
)

// --- Error response ---

@Serializable
data class ErrorResponse(val error: String)
