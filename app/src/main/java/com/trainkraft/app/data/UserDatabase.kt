package com.trainkraft.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
 * Mechanism: plain Room database, version 5, no asset; migrations
 * [MIGRATION_1_2] (Phase C alarm columns), [MIGRATION_2_3] (Phase D
 * travel-fix trace table), [MIGRATION_3_4] (Go-live tier flag) and
 * [MIGRATION_4_5] (on-device alert-log table). First-launch seeding from a pre-split `trains.db` (v3) is performed
 * pre-open by [UserDataMigrator] (plain SQLite, never inside a Room
 * migration — ATTACH there throws under WAL mode); [TrainDatabase]
 * `MIGRATION_3_4` only drops the old tables. Opening this database
 * *before* the trains database (as [com.trainkraft.app.di.AppContainer]
 * does) keeps file creation order stable.
 *
 * Provenance: split out of [TrainDatabase] v3 in Sep 2026 (privacy fix);
 * [TrackedTrainEntity] and [CachedResponseEntity] moved here unchanged, so old
 * payloads keep parsing byte-for-byte.
 */
@Database(
    entities = [
        TrackedTrainEntity::class,
        CachedResponseEntity::class,
        TravelFixEntity::class,
        AlertLogEntity::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun trackingDao(): TrackingDao
    abstract fun cacheDao(): CacheDao
    abstract fun travelTraceDao(): TravelTraceDao
    abstract fun alertLogDao(): AlertLogDao

    companion object {
        const val DB_NAME = "user.db"

        /**
         * v1 -> v2: station-alarm columns on `tracked_trains` (Phase C).
         * Nullable TEXT, no backfill — existing rows read null (= no watch
         * station, no approach notified), which is the correct default.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracked_trains` ADD COLUMN `watchStationCode` TEXT")
                db.execSQL("ALTER TABLE `tracked_trains` ADD COLUMN `lastApproachFor` TEXT")
            }
        }

        /**
         * v2 -> v3: Phase D travel-mode GPS trace table (`travel_fixes`).
         * Fresh CREATE TABLE matching [TravelFixEntity] DDL exactly
         * (composite (`trainNumber`, `tsEpochMs`) primary key — no
         * autoincrement/`sqlite_sequence` — plus the two indices); no
         * backfill — a new table starts empty, which is the correct default.
         * Existing `tracked_trains` / `cached_responses` rows are untouched.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `travel_fixes` (" +
                        "`trainNumber` TEXT NOT NULL, " +
                        "`tsEpochMs` INTEGER NOT NULL, " +
                        "`lat` REAL NOT NULL, " +
                        "`lon` REAL NOT NULL, " +
                        "`speedKmh` REAL NOT NULL, " +
                        "`accuracyM` REAL NOT NULL, " +
                        "PRIMARY KEY(`trainNumber`, `tsEpochMs`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_travel_fixes_trainNumber` " +
                        "ON `travel_fixes` (`trainNumber`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_travel_fixes_tsEpochMs` " +
                        "ON `travel_fixes` (`tsEpochMs`)"
                )
            }
        }

        /**
         * v3 -> v4: Go-live tier flag on `tracked_trains`. Stopping minute
         * presence must not unfollow (the bell owns row existence) — hence a
         * column, not row deletion. Default false = baseline-only, which is
         * the correct reading of every pre-existing row.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `tracked_trains` ADD COLUMN `liveTracking` " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v4 -> v5: on-device alert-log table (`alert_log`).
         * Fresh CREATE TABLE matching [AlertLogEntity] DDL exactly
         * (auto-id PK + index on `tsEpochMs` for the newest-first query); no
         * backfill — a new table starts empty, which is the correct default.
         * Existing `tracked_trains` / `cached_responses` / `travel_fixes`
         * rows are untouched. Lives in user.db (backup-excluded) so alert
         * history never leaves the device; growth capped by the 90-day
         * prune on every log (see [ALERT_LOG_RETENTION_MS]).
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alert_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trainNumber` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`tsEpochMs` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_alert_log_tsEpochMs` " +
                        "ON `alert_log` (`tsEpochMs`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    DB_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
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
