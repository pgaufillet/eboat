package com.eboat.domain.model

data class AnchorState(
    val active: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = 50,
    val currentDistanceMeters: Double = 0.0,
    val isDragging: Boolean = false
)
