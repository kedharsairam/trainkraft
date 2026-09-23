package com.trainkraft.app.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * TrainKraft timetable database, version 4.
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
 *
 * Pre-population: [Room.databaseBuilder.createFromAsset]("trains.db") is
 * wired in [getInstance] so dropping a `trains.db` file into
 * `app/src/main/assets/` just works.
 *
 * Migrations: 2 -> 3 is a real migration ([MIGRATION_2_3]) that only
 * CREATEs the new state tables — existing timetable data survives. 3 -> 4
 * ([MIGRATION_3_4]) copies those state tables into `user.db` one-shot, then
 * drops them here. The destructive fallback remains solely for the legacy
 * 1 -> 2 pre-release bump.
 */
@Database(
    entities = [
        StationEntity::class,
        TrainEntity::class,
        TripEntity::class,
        CalendarEntity::class,
        StopTimeEntity::class,
    ],
    version = 4,
    exportSchema = false
)
abstract class TrainDatabase : RoomDatabase() {

    abstract fun trainDao(): TrainDao

    companion object {
        const val ASSET_NAME = "trains.db"
        const val DB_NAME = "trains.db"

        private const val TAG = "TrainDatabase"

        /**
         * Absolute path of the `user.db` file the 3 -> 4 migration copies
         * user rows into. Set in [getInstance] before the database opens so
         * the migration (which only receives a [SupportSQLiteDatabase], never
         * a [Context]) knows where to ATTACH. Tests override it directly to
         * point at an isolated file.
         */
        @Volatile
        var userDbPath: String? = null

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
         * One-shot privacy-split migration, v3 -> v4.
         *
         * Mechanism: ATTACH DATABASE on the `user.db` file at [userDbPath],
         * CREATE the two user tables there if missing (same DDL Room itself
         * generates for [UserDatabase], so the later Room open validates
         * cleanly), `INSERT OR REPLACE ... SELECT *` every row across, DETACH,
         * then `DROP TABLE` the old copies here. Static GTFS tables are never
         * touched.
         *
         * Failure policy: the copy block is best-effort — any exception is
         * logged and swallowed so the upgrade can never brick the timetable.
         * The drops always run. If the copy failed (e.g. no path set, disk
         * full), the user loses followed-train bells / offline cache once and
         * starts from an empty `user.db`; the timetable itself is unaffected.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val target = userDbPath
                if (target != null) {
                    try {
                        val escaped = target.replace("'", "''")
                        db.execSQL("ATTACH DATABASE '$escaped' AS userdb")
                        try {
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `userdb`.`tracked_trains` (" +
                                    "`trainNumber` TEXT NOT NULL PRIMARY KEY, " +
                                    "`trackedAt` INTEGER NOT NULL, " +
                                    "`lastDelayMin` INTEGER, " +
                                    "`lastStation` TEXT, " +
                                    "`lastCategory` TEXT, " +
                                    "`lastPollAt` INTEGER)"
                            )
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `userdb`.`cached_responses` (" +
                                    "`cacheKey` TEXT NOT NULL PRIMARY KEY, " +
                                    "`json` TEXT NOT NULL, " +
                                    "`fetchedAt` INTEGER NOT NULL)"
                            )
                            db.execSQL(
                                "INSERT OR REPLACE INTO `userdb`.`tracked_trains` " +
                                    "SELECT * FROM `tracked_trains`"
                            )
                            db.execSQL(
                                "INSERT OR REPLACE INTO `userdb`.`cached_responses` " +
                                    "SELECT * FROM `cached_responses`"
                            )
                        } finally {
                            db.execSQL("DETACH DATABASE userdb")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "user.db copy failed, starting fresh: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "userDbPath unset, starting fresh user.db")
                }
                db.execSQL("DROP TABLE IF EXISTS `tracked_trains`")
                db.execSQL("DROP TABLE IF EXISTS `cached_responses`")
            }
        }

        @Volatile
        private var INSTANCE: TrainDatabase? = null

        fun getInstance(context: Context): TrainDatabase {
            return INSTANCE ?: synchronized(this) {
                val app = context.applicationContext
                // Canonical user.db path for MIGRATION_3_4's ATTACH; ensure the
                // parent exists so ATTACH can create the file on first upgrade.
                val userFile = app.getDatabasePath(UserDatabase.DB_NAME)
                userFile.parentFile?.mkdirs()
                userDbPath = userFile.absolutePath
                val instance = Room.databaseBuilder(
                    app,
                    TrainDatabase::class.java,
                    DB_NAME
                )
                    .createFromAsset(ASSET_NAME)
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
