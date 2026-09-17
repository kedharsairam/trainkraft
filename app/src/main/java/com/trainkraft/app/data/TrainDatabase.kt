package com.trainkraft.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * TrainKraft timetable database, version 1.
 *
 * Entities mirror the GTFS source (columns verified by caller):
 * stops.txt -> [StationEntity], routes.txt -> [TrainEntity],
 * trips.txt -> [TripEntity], calendar.txt -> [CalendarEntity],
 * stop_times.txt -> [StopTimeEntity]. No FTS (device SQLite lacks fts5).
 *
 * Pre-population: [Room.databaseBuilder.createFromAsset]("trains.db") is
 * wired in [getInstance] so dropping a `trains.db` file into
 * `app/src/main/assets/` just works. Until the asset exists, do NOT call
 * getInstance() on a fresh install (Room throws for a missing asset);
 * schema/DAO verification via assembleDebug is unaffected.
 *
 * [fallbackToDestructiveMigration] is enabled for v1 iteration; replace
 * with real migrations once the DB ships to users.
 */
@Database(
    entities = [
        StationEntity::class,
        TrainEntity::class,
        TripEntity::class,
        CalendarEntity::class,
        StopTimeEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TrainDatabase : RoomDatabase() {

    abstract fun trainDao(): TrainDao

    companion object {
        const val ASSET_NAME = "trains.db"
        const val DB_NAME = "trains.db"

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
                    .fallbackToDestructiveMigration(false)
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
