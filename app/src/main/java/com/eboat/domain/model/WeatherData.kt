package com.eboat.domain.model

data class WeatherForecast(
    val time: Long,
    val windSpeedKnots: Double,
    val windDirectionDeg: Double,
    val gustSpeedKnots: Double,
    val waveHeightM: Double,
    val waveDirectionDeg: Double,
    val wavePeriodS: Double,
    val swellHeightM: Double,
    val swellDirectionDeg: Double,
    val swellPeriodS: Double,
    val pressureHpa: Double,
    val visibility: Double // km
)

data class WeatherData(
    val latitude: Double,
    val longitude: Double,
    val forecasts: List<WeatherForecast> = emptyList()
)

enum class WeatherLayerType(val label: String) {
    WIND("Vent"),
    WAVE_HEIGHT("Vagues"),
    SWELL("Houle"),
    PRESSURE("Pression")
}
