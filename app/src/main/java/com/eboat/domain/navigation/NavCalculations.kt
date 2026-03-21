package com.eboat.domain.navigation

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Nautical mile in meters */
private const val NM_IN_METERS = 1852.0
private const val EARTH_RADIUS_M = 6_371_000.0

/** Haversine distance in nautical miles */
fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val rLat1 = Math.toRadians(lat1)
    val rLat2 = Math.toRadians(lat2)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (EARTH_RADIUS_M * c) / NM_IN_METERS
}

/** Initial bearing in degrees (0-360) from point 1 to point 2 */
fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val rLat1 = Math.toRadians(lat1)
    val rLat2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(rLat2)
    val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

/**
 * Cross-track distance in nautical miles.
 * Positive = starboard of the track, negative = port.
 */
fun crossTrackNm(
    boatLat: Double, boatLon: Double,
    fromLat: Double, fromLon: Double,
    toLat: Double, toLon: Double
): Double {
    val distBoatFrom = distanceNm(fromLat, fromLon, boatLat, boatLon) * NM_IN_METERS / EARTH_RADIUS_M
    val bearingFromBoat = Math.toRadians(bearingDeg(fromLat, fromLon, boatLat, boatLon))
    val bearingFromTo = Math.toRadians(bearingDeg(fromLat, fromLon, toLat, toLon))
    val xte = asin(sin(distBoatFrom) * sin(bearingFromBoat - bearingFromTo))
    return (xte * EARTH_RADIUS_M) / NM_IN_METERS
}

/** ETA in seconds given distance in NM and speed in knots. Returns null if speed ~0. */
fun etaSeconds(distanceNm: Double, speedKnots: Float): Long? {
    if (speedKnots < 0.5f) return null
    return (distanceNm / speedKnots * 3600).toLong()
}
