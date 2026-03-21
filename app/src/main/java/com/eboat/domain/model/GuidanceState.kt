package com.eboat.domain.model

data class GuidanceState(
    val active: Boolean = false,
    val nextWaypointIndex: Int = 0,
    val nextWaypointName: String = "",
    val bearingToWaypoint: Double = 0.0,
    val distanceToWaypointNm: Double = 0.0,
    val crossTrackNm: Double = 0.0,
    val etaSeconds: Long? = null,
    val totalWaypoints: Int = 0,
    val routeComplete: Boolean = false
)
