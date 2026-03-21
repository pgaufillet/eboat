package com.eboat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Query
import com.eboat.domain.model.Waypoint
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {
    @Query("SELECT * FROM waypoints ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Waypoint>>

    @Insert
    suspend fun insert(waypoint: Waypoint): Long

    @Delete
    suspend fun delete(waypoint: Waypoint)
}
