package com.trainkraft.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Per-user private database (`user.db`): followed trains + offline cache.
 *
 * Why it exists: `trains.db` used to hold the static GTFS timetable together
 * with `tracked_trains` / `cached_responses`, and the whole file was covered
 * by cloud backup — train-follow history (which station the user watches, and
 * when) left the device. The v4 split keeps `trains.db` to the five static
 * GTFS tables (re-seedable from the bundled asset via
 * [TrainDatabase.getInstance]'s `createFromAsset`, so excluding it from backup
 * is safe) while this database owns everything user-specific. Both
 * `backup_rules.xml` and `data_extraction_rules.xml` exclude `user.db`, so
 * this file never leaves the device.
 *
 * Mechanism: plain Room database, version 1, no asset, no migrations yet.
 * First-launch seeding from a pre-split `trains.db` (v3) is performed by
 * [TrainDatabase.MIGRATION_3_4], which ATTACHes this file's canonical path
 * ([DB_NAME] under the app database directory) and copies rows before
 * dropping the old tables — see that migration's comment block. Opening this
 * database *before* the trains database (as [com.trainkraft.app.di.AppContainer]
 * does) guarantees the file and its `room_master_table` already exist when the
 * copy runs, so the ATTACH path only ever fills pre-created Room tables.
 *
 * Provenance: split out of [TrainDatabase] v3 in Sep 2026 (privacy fix);
 * [TrackedTrainEntity] and [CachedResponseEntity] moved here unchanged, so old
 * payloads keep parsing byte-for-byte.
 */
@Database(
    entities = [
        TrackedTrainEntity::class,
        CachedResponseEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun trackingDao(): TrackingDao
    abstract fun cacheDao(): CacheDao

    companion object {
        const val DB_NAME = "user.db"

        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    DB_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }

        /** Test/preview helper: in-memory DB. */
        fun inMemory(context: Context): UserDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                UserDatabase::class.java
            ).build()
        }
    }
}
