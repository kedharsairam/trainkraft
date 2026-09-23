package com.trainkraft.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * TrainKraft timetable database, version 3.
 *
 * Entities mirror the GTFS source (columns verified by caller):
 * stops.txt -> [StationEntity], routes.txt -> [TrainEntity],
 * trips.txt -> [TripEntity], calendar.txt -> [CalendarEntity],
 * stop_times.txt -> [StopTimeEntity]. No FTS (device SQLite lacks fts5).
 * v3 adds app-state tables: [TrackedTrainEntity] (followed trains) and
 * [CachedResponseEntity] (offline response cache).
 *
 * Pre-population: [Room.databaseBuilder.createFromAsset]("trains.db") is
 * wired in [getInstance] so dropping a `trains.db` file into
 * `app/src/main/assets/` just works.
 *
 * Migrations: 2 -> 3 is a real migration ([MIGRATION_2_3]) that only
 * CREATEs the new state tables — existing timetable data survives. The
 * destructive fallback remains solely for the legacy 1 -> 2 pre-release bump.
 */
@Database(
    entities = [
        StationEntity::class,
        TrainEntity::class,
        TripEntity::class,
        CalendarEntity::class,
        StopTimeEntity::class,
        TrackedTrainEntity::class,
        CachedResponseEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class TrainDatabase : RoomDatabase() {

    abstract fun trainDao(): TrainDao
    abstract fun trackingDao(): TrackingDao
    abstract fun cacheDao(): CacheDao

    companion object {
        const val ASSET_NAME = "trains.db"
        const val DB_NAME = "trains.db"

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracked_trains` (" +
                        "`trainNumber` TEXT NOT NULL PRIMARY KEY, " +
                        "`trackedAt` INTEGER NOT NULL, " +
                        "`lastDelayMin` INTEGER, " +
                        "`lastStation` TEXT, " +
                        "`lastCategory` TEXT, " +
                        "`lastPollAt` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_responses` (" +
                        "`cacheKey` TEXT NOT NULL PRIMARY KEY, " +
                        "`json` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL)"
                )
            }
        }

        @Volatile
        private var INSTANCE: TrainDatabase? = null

        fun getInstance(context: Context): TrainDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrainDatabase::class.java,
                    DB_NAME
                )
                    .createFromAsset(ASSET_NAME)
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Test/preview helper: in-memory DB without the asset requirement. */
        fun inMemory(context: Context): TrainDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                TrainDatabase::class.java
            ).build()
        }
    }
}
