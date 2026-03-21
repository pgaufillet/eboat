package com.eboat.domain.model

data class AisTarget(
    val mmsi: String,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val cog: Float = 0f,
    val sog: Float = 0f,
    val heading: Int = 0,
    val shipType: Int = 0,
    val lastUpdate: Long = System.currentTimeMillis()
) {
    val hasPosition: Boolean get() = latitude != 0.0 || longitude != 0.0
}
