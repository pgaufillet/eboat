package com.eboat.domain.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class RouteWithWaypoints(
    @Embedded val route: Route,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RouteWaypoint::class,
            parentColumn = "routeId",
            entityColumn = "waypointId"
        )
    )
    val waypoints: List<Waypoint>
) {
    /** Waypoints sorted by their order in the route */
    val orderedWaypoints: List<Waypoint>
        get() = waypoints // Room returns them in join order with the query below
}
