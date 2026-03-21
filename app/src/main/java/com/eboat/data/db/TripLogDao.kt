package com.eboat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eboat.domain.model.TripLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TripLogDao {

    @Insert
    suspend fun insert(entry: TripLogEntry)

    @Query("SELECT DISTINCT tripId FROM trip_log ORDER BY tripId DESC")
    fun observeTripIds(): Flow<List<Long>>

    @Query("SELECT * FROM trip_log WHERE tripId = :tripId ORDER BY timestamp")
    suspend fun getEntriesForTrip(tripId: Long): List<TripLogEntry>

    @Query("SELECT * FROM trip_log WHERE tripId = :tripId ORDER BY timestamp")
    fun observeEntriesForTrip(tripId: Long): Flow<List<TripLogEntry>>

    @Query("SELECT COUNT(*) FROM trip_log WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int

    @Query("DELETE FROM trip_log WHERE tripId = :tripId")
    suspend fun deleteTrip(tripId: Long)

    @Query("SELECT MAX(tripId) FROM trip_log")
    suspend fun getLastTripId(): Long?
}
