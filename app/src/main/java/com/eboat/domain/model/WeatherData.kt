package com.eboat.domain.model

data class WeatherForecast(
    val time: Long,
    val windSpeedKnots: Double,
    val windDirectionDeg: Double,
    val gustSpeedKnots: Double,
    val waveHeightM: Double,
    val pressureHpa: Double,
    val visibility: Double // km
)

data class WeatherData(
    val latitude: Double,
    val longitude: Double,
    val forecasts: List<WeatherForecast> = emptyList()
)
