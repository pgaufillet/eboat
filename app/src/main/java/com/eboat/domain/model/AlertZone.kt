package com.eboat.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_zones")
data class AlertZone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Comma-separated lat,lng pairs: "lat1,lng1;lat2,lng2;..." */
    val pointsEncoded: String,
    /** true = alert on ENTRY, false = alert on EXIT */
    val alertOnEntry: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun decodePoints(): List<Pair<Double, Double>> =
        pointsEncoded.split(";").mapNotNull { pair ->
            val parts = pair.split(",")
            if (parts.size == 2) parts[0].toDoubleOrNull()?.let { lat ->
                parts[1].toDoubleOrNull()?.let { lng -> lat to lng }
            } else null
        }

    companion object {
        fun encodePoints(points: List<Pair<Double, Double>>): String =
            points.joinToString(";") { "${it.first},${it.second}" }
    }
}
