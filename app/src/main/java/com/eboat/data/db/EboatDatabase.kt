package com.eboat.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.eboat.domain.model.Route
import com.eboat.domain.model.RouteWaypoint
import com.eboat.domain.model.Waypoint

@Database(
    entities = [Waypoint::class, Route::class, RouteWaypoint::class],
    version = 2
)
abstract class EboatDatabase : RoomDatabase() {
    abstract fun waypointDao(): WaypointDao
    abstract fun routeDao(): RouteDao

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
