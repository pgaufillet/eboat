package com.eboat.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.eboat.domain.model.AlertZone
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertZoneDao {
    @Query("SELECT * FROM alert_zones ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlertZone>>

    @Query("SELECT * FROM alert_zones WHERE enabled = 1")
    suspend fun getEnabled(): List<AlertZone>

    @Insert
    suspend fun insert(zone: AlertZone): Long

    @Update
    suspend fun update(zone: AlertZone)

    @Delete
    suspend fun delete(zone: AlertZone)
}
