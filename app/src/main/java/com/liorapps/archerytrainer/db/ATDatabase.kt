package com.liorapps.archerytrainer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ShootingSessionEntity::class, ShootingSetEntity::class],
    version = 1,
    exportSchema = true          // set to false if you don't need schema export files
)
abstract class ATDatabase : RoomDatabase() {

    abstract fun shootingSessionDao(): ShootingSessionDao
    abstract fun shootingSetDao(): ShootingSetDao

    companion object {
        @Volatile
        private var INSTANCE: ATDatabase? = null

        /**
         * Returns the singleton database instance, creating it on the very
         * first call (thread-safe via double-checked locking).
         *
         * Call this from your Application class, a Hilt module, or wherever
         * you manage dependency injection.
         */
        fun getInstance(context: Context): ATDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ATDatabase::class.java,
                    "archery_trainer_database"
                )
                    .fallbackToDestructiveMigration(true)  // swap for real migrations in production
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
