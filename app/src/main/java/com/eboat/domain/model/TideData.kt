package com.eboat.domain.model

data class TidePoint(
    val time: Long,     // epoch millis
    val height: Double  // meters
)

data class TideData(
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val predictions: List<TidePoint> = emptyList(),
    val highLow: List<TideExtreme> = emptyList()
)

data class TideExtreme(
    val time: Long,
    val height: Double,
    val isHigh: Boolean
)
