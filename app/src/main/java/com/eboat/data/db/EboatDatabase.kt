package com.eboat.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.eboat.domain.model.Waypoint

@Database(entities = [Waypoint::class], version = 1)
abstract class EboatDatabase : RoomDatabase() {
    abstract fun waypointDao(): WaypointDao

    companion object {
        @Volatile
        private var instance: EboatDatabase? = null

        fun getInstance(context: Context): EboatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EboatDatabase::class.java,
                    "eboat.db"
                ).build().also { instance = it }
            }
    }
}
