package com.eboat.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_log")
data class TripLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val sog: Float,
    val cog: Float
)

data class TripSummary(
    val tripId: Long,
    val startTime: Long,
    val endTime: Long,
    val pointCount: Int,
    val distanceNm: Double
)
