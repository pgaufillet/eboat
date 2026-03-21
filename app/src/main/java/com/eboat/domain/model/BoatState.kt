package com.eboat.domain.model

data class BoatState(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedOverGround: Float = 0f,
    val courseOverGround: Float = 0f,
    val accuracy: Float = 0f,
    val hasPosition: Boolean = false
)
