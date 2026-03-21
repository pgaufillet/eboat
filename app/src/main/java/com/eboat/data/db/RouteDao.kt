package com.eboat.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.eboat.domain.model.Route
import com.eboat.domain.model.RouteWaypoint
import com.eboat.domain.model.Waypoint
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Insert
    suspend fun insertRoute(route: Route): Long

    @Insert
    suspend fun insertRouteWaypoints(items: List<RouteWaypoint>)

    @Delete
    suspend fun deleteRoute(route: Route)

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Route>>

    @Query("""
        SELECT w.* FROM waypoints w
        INNER JOIN route_waypoints rw ON rw.waypointId = w.id
        WHERE rw.routeId = :routeId
        ORDER BY rw.sortOrder
    """)
    fun observeWaypointsForRoute(routeId: Long): Flow<List<Waypoint>>

    @Query("""
        SELECT w.* FROM waypoints w
        INNER JOIN route_waypoints rw ON rw.waypointId = w.id
        WHERE rw.routeId = :routeId
        ORDER BY rw.sortOrder
    """)
    suspend fun getWaypointsForRoute(routeId: Long): List<Waypoint>

    @Transaction
    suspend fun createRoute(name: String, waypointIds: List<Long>): Long {
        val routeId = insertRoute(Route(name = name))
        val items = waypointIds.mapIndexed { index, wpId ->
            RouteWaypoint(routeId = routeId, waypointId = wpId, sortOrder = index)
        }
        insertRouteWaypoints(items)
        return routeId
    }
}
