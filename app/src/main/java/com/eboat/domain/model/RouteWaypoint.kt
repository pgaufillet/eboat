package com.eboat.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "route_waypoints",
    primaryKeys = ["routeId", "sortOrder"],
    foreignKeys = [
        ForeignKey(
            entity = Route::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Waypoint::class,
            parentColumns = ["id"],
            childColumns = ["waypointId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RouteWaypoint(
    val routeId: Long,
    val waypointId: Long,
    val sortOrder: Int
)
