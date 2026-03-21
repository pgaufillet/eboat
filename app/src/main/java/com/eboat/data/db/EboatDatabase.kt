package com.eboat.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.eboat.domain.model.AlertZone
import com.eboat.domain.model.Route
import com.eboat.domain.model.RouteWaypoint
import com.eboat.domain.model.TripLogEntry
import com.eboat.domain.model.Waypoint

@Database(
    entities = [Waypoint::class, Route::class, RouteWaypoint::class, AlertZone::class, TripLogEntry::class],
    version = 4
)
abstract class EboatDatabase : RoomDatabase() {
    abstract fun waypointDao(): WaypointDao
    abstract fun routeDao(): RouteDao
    abstract fun alertZoneDao(): AlertZoneDao
    abstract fun tripLogDao(): TripLogDao

    companion object {
        @Volatile
        private var instance: EboatDatabase? = null

        fun getInstance(context: Context): EboatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EboatDatabase::class.java,
                    "eboat.db"
                ).fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
