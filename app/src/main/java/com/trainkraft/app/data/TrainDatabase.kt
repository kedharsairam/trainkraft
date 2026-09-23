package com.trainkraft.app.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * TrainKraft timetable database, version 5.
 *
 * Entities mirror the GTFS source (columns verified by caller):
 * stops.txt -> [StationEntity], routes.txt -> [TrainEntity],
 * trips.txt -> [TripEntity], calendar.txt -> [CalendarEntity],
 * stop_times.txt -> [StopTimeEntity]. No FTS (device SQLite lacks fts5).
 * v3 added app-state tables ([TrackedTrainEntity] / [CachedResponseEntity]);
 * v4 removes them again — they now live in [UserDatabase] (`user.db`), which
 * is excluded from cloud backup so follow-history never leaves the device.
 * `trains.db` itself is also backup-excluded: it re-seeds from the bundled
 * asset via [Room.databaseBuilder.createFromAsset] on a fresh install /
 * restore (see [getInstance]).
 * v5 adds the Phase A intelligence-pack tables ([DelayPriorEntity] /
 * [FogOverlayEntity] / [PackMetaEntity], served via [packDao] and filled by
 * [PackImporter] from a pipeline-built `pack.db`): CRIS average-delay priors
 * plus fog-season timetable overlays for the Phase B prediction engine.
 *
 * Pre-population: [Room.databaseBuilder.createFromAsset]("trains.db") is
 * wired in [getInstance] so dropping a `trains.db` file into
 * `app/src/main/assets/` just works.
 *
 * Migrations: 2 -> 3 is a real migration ([MIGRATION_2_3]) that only
 * CREATEs the new state tables — existing timetable data survives. 3 -> 4
 * ([MIGRATION_3_4]) copies those state tables into `user.db` one-shot, then
 * drops them here. 4 -> 5 ([MIGRATION_4_5]) CREATEs the three pack tables
 * (empty — rows arrive later via [PackImporter]); GTFS tables are untouched.
 * The destructive fallback remains solely for the legacy 1 -> 2 pre-release
 * bump.
 */
@Database(
    entities = [
        StationEntity::class,
        TrainEntity::class,
        TripEntity::class,
        CalendarEntity::class,
        StopTimeEntity::class,
        DelayPriorEntity::class,
        FogOverlayEntity::class,
        PackMetaEntity::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class TrainDatabase : RoomDatabase() {

    abstract fun trainDao(): TrainDao

    abstract fun packDao(): PackDao

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

        /**
         * One-shot privacy-split migration, v3 -> v4: DROPS the user tables.
         *
         * The DATA copy used to live here (ATTACH inside this migration), but
         * that throws under WAL mode on-device (SQLite forbids the journal
         * churn ATTACH triggers inside Room's migration transaction) — the
         * best-effort catch then ate real user rows while the drops still
         * ran. ATTACH inside a Room migration is banned; the copy now runs
         * beforehand in [UserDataMigrator] (plain framework SQLite,
         * autocommit, no encompassing transaction), and this migration only
         * removes the old tables. Static GTFS tables are never touched.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `tracked_trains`")
                db.execSQL("DROP TABLE IF EXISTS `cached_responses`")
            }
        }

        /**
         * Intelligence-pack schema, v4 -> v5.
         *
         * Mechanism: CREATEs the three pack tables with the schema contract
         * DDL verbatim (byte agreement with the pipeline's `pack.db`, see
         * [DelayPriorEntity]'s class KDoc). Tables start empty — rows arrive
         * later via [PackImporter], which never runs inside a migration. The
         * five static GTFS tables are never touched.
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `delay_priors` (" +
                        "`trainNumber` TEXT NOT NULL, " +
                        "`stationCode` TEXT NOT NULL, " +
                        "`arrAvgMin` INTEGER NOT NULL DEFAULT 0, " +
                        "`depAvgMin` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`trainNumber`, `stationCode`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fog_overlays` (" +
                        "`trainNumber` TEXT NOT NULL PRIMARY KEY, " +
                        "`action` TEXT NOT NULL, " +
                        "`fromDate` TEXT NOT NULL, " +
                        "`toDate` TEXT NOT NULL, " +
                        "`season` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL DEFAULT '')"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pack_meta` (" +
                        "`key` TEXT NOT NULL PRIMARY KEY, " +
                        "`value` TEXT NOT NULL)"
                )
            }
        }

        @Volatile
        private var INSTANCE: TrainDatabase? = null

        fun getInstance(context: Context): TrainDatabase {
            return INSTANCE ?: synchronized(this) {
                val app = context.applicationContext
                val instance = Room.databaseBuilder(
                    app,
                    TrainDatabase::class.java,
                    DB_NAME
                )
                    .createFromAsset(ASSET_NAME)
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // CRITICAL: never use the *unconditional* fallback together
                    // with createFromAsset — Room then deletes + re-copies the
                    // asset on EVERY open (even same-version), silently wiping
                    // user data on each cold start.
                    // Scoped to legacy v1 only: v2->v3 runs MIGRATION_2_3,
                    // v3->v4 runs MIGRATION_3_4, v4 opens consult neither.
                    .fallbackToDestructiveMigrationFrom(1)
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
