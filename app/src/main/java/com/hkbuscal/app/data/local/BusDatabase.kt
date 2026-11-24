package com.hkbuscal.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BusStopEntity::class, CommuteRuleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BusDatabase : RoomDatabase() {
    abstract fun busDao(): BusDao

    companion object {
        @Volatile
        private var INSTANCE: BusDatabase? = null

        fun getInstance(context: Context): BusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BusDatabase::class.java,
                    "hk_bus_calendar.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
