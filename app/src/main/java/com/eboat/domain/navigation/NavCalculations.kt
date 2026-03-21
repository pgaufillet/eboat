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

/** Ray-casting point-in-polygon test */
fun isPointInPolygon(lat: Double, lon: Double, polygon: List<Pair<Double, Double>>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val (yi, xi) = polygon[i]
        val (yj, xj) = polygon[j]
        if (((yi > lon) != (yj > lon)) &&
            (lat < (xj - xi) * (lon - yi) / (yj - yi) + xi)) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/** Destination point given start, bearing (degrees) and distance (NM) — haversine inverse */
fun destinationPoint(lat: Double, lon: Double, bearingDeg: Double, distanceNm: Double): Pair<Double, Double> {
    val d = distanceNm * NM_IN_METERS / EARTH_RADIUS_M
    val brng = Math.toRadians(bearingDeg)
    val rLat = Math.toRadians(lat)
    val rLon = Math.toRadians(lon)
    val lat2 = asin(sin(rLat) * cos(d) + cos(rLat) * sin(d) * cos(brng))
    val lon2 = rLon + atan2(sin(brng) * sin(d) * cos(rLat), cos(d) - sin(rLat) * sin(lat2))
    return Math.toDegrees(lat2) to Math.toDegrees(lon2)
}

/**
 * Generate a grid of sample points in a corridor around waypoints.
 * @param waypoints Route waypoints (lat, lon pairs)
 * @param radiusNm Corridor half-width in NM (default 10)
 * @param spacingNm Distance between samples along the route in NM (default 10)
 * @return List of (lat, lon) sample points
 */
fun corridorGrid(
    waypoints: List<Pair<Double, Double>>,
    radiusNm: Double = 10.0,
    spacingNm: Double = 10.0
): List<Pair<Double, Double>> {
    if (waypoints.isEmpty()) return emptyList()

    // Single point: 3x3 grid around it
    if (waypoints.size == 1) {
        val (lat, lon) = waypoints[0]
        val offsets = listOf(-radiusNm, 0.0, radiusNm)
        return offsets.flatMap { dLat ->
            offsets.map { dLon ->
                val p = destinationPoint(lat, lon, if (dLat >= 0) 0.0 else 180.0, kotlin.math.abs(dLat))
                destinationPoint(p.first, p.second, if (dLon >= 0) 90.0 else 270.0, kotlin.math.abs(dLon))
            }
        }
    }

    val points = mutableSetOf<Pair<Double, Double>>()

    for (i in 0 until waypoints.size - 1) {
        val (lat1, lon1) = waypoints[i]
        val (lat2, lon2) = waypoints[i + 1]
        val legDist = distanceNm(lat1, lon1, lat2, lon2)
        val legBearing = bearingDeg(lat1, lon1, lat2, lon2)
        val perpLeft = (legBearing - 90 + 360) % 360
        val perpRight = (legBearing + 90) % 360
        val steps = (legDist / spacingNm).toInt().coerceAtLeast(1)

        for (s in 0..steps) {
            val frac = s.toDouble() / steps
            val along = frac * legDist
            val center = destinationPoint(lat1, lon1, legBearing, along)

            // Center point
            points.add(center)
            // Left and right offsets
            points.add(destinationPoint(center.first, center.second, perpLeft, radiusNm))
            points.add(destinationPoint(center.first, center.second, perpRight, radiusNm))
        }
    }

    return points.toList()
}

/** ETA in seconds given distance in NM and speed in knots. Returns null if speed ~0. */
fun etaSeconds(distanceNm: Double, speedKnots: Float): Long? {
    if (speedKnots < 0.5f) return null
    return (distanceNm / speedKnots * 3600).toLong()
}
