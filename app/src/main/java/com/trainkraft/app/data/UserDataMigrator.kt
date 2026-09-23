package com.trainkraft.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.trainkraft.app.BuildConfig

/**
 * One-shot v3 user-data rescue, run BEFORE Room opens `trains.db`.
 *
 * Background: `MIGRATION_3_4` originally copied `tracked_trains` /
 * `cached_responses` into `user.db` via ATTACH *inside Room's migration
 * transaction*. Proven on-device (Sep 2026, Realme, trains.db in WAL mode
 * with a live `-wal` file) to throw
 * `SQLiteException: WAL mode cannot be enabled or disabled while there are
 * transactions in progress` — the best-effort catch swallowed it, the drops
 * still ran, and the user's followed trains were lost. ATTACH inside a Room
 * migration transaction is therefore banned in this codebase.
 *
 * This runs instead at [com.trainkraft.app.di.AppContainer] init (before
 * `TrainDatabase.getInstance`), in two phases, both outside any Room
 * migration transaction: (1) probe the file read-only via framework SQLite
 * (version + table presence, no side effects); (2) copy through an androidx
 * helper pinned at the probed version via ATTACH — the same
 * FrameworkSQLiteDatabase path the old in-migration copy used, proven in
 * every test environment (plain framework ATTACH is refused by Robolectric's
 * SQLite with SQLITE_CANTOPEN, so production doesn't use it either).
 * Reads see committed data (WAL readers included); writes are single
 * statements.
 *
 * Contract:
 * - Only acts when `trains.db` exists at exactly user_version 3 AND still
 *   carries the user tables (pre-split file). Anything else → false, no-op.
 * - Creates `user.db` tables with the CURRENT entity DDL (8-col tracked,
 *   3-col cache — keep these literals in sync with [TrackedTrainEntity] /
 *   [CachedResponseEntity]; the migration test pins both shapes).
 * - Copies with an explicit 6-column list (real v3 files predate the alarm
 *   columns; v2-shaped extras default null).
 * - Never throws, never touches versions or drops anything — dropping stays
 *   in `MIGRATION_3_4`, which runs right after inside Room.
 */
object UserDataMigrator {

    private const val TAG = "UserDataMigrator"

    fun migrateV3UserTables(appContext: Context): Boolean {
        val app = appContext.applicationContext
        val trainsFile = app.getDatabasePath(TrainDatabase.DB_NAME)
        if (!trainsFile.exists()) return false
        // Phase 1 — probe with a read-only framework connection (no version
        // side effects, no upgrade/downgrade triggers).
        val probe = try {
            SQLiteDatabase.openDatabase(
                trainsFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
        } catch (e: Exception) {
            debugLog("probe open failed: ${e.message}")
            return false
        }
        val (version, tables) = try {
            val v = probe.version
            val names = mutableSetOf<String>()
            probe.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'", null
            ).use { cursor ->
                while (cursor.moveToNext()) names.add(cursor.getString(0))
            }
            v to names
        } catch (e: Exception) {
            debugLog("probe read failed: ${e.message}")
            return false
        } finally {
            runCatching { probe.close() }
        }
        if (version != 3) return false
        if (!tables.contains("tracked_trains") || !tables.contains("cached_responses")) {
            return false
        }
        // Phase 2 — copy through an androidx helper pinned at the probed
        // version (no upgrade/downgrade can fire). ATTACH goes through
        // FrameworkSQLiteDatabase — the same path the old in-migration copy
        // used, which the migration suite proves in every environment.
        val userFile = app.getDatabasePath(UserDatabase.DB_NAME)
        userFile.parentFile?.mkdirs()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(trainsFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        return try {
            val db = helper.writableDatabase
            val escaped = userFile.absolutePath.replace("'", "''")
            db.execSQL("ATTACH DATABASE '$escaped' AS userdb")
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userdb`.`tracked_trains` (" +
                        "`trainNumber` TEXT NOT NULL PRIMARY KEY, " +
                        "`trackedAt` INTEGER NOT NULL, " +
                        "`lastDelayMin` INTEGER, " +
                        "`lastStation` TEXT, " +
                        "`lastCategory` TEXT, " +
                        "`lastPollAt` INTEGER, " +
                        "`watchStationCode` TEXT, " +
                        "`lastApproachFor` TEXT, " +
                        "`liveTracking` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `userdb`.`cached_responses` (" +
                        "`cacheKey` TEXT NOT NULL PRIMARY KEY, " +
                        "`json` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `userdb`.`tracked_trains` " +
                        "(`trainNumber`, `trackedAt`, `lastDelayMin`, " +
                        "`lastStation`, `lastCategory`, `lastPollAt`) " +
                        "SELECT `trainNumber`, `trackedAt`, `lastDelayMin`, " +
                        "`lastStation`, `lastCategory`, `lastPollAt` " +
                        "FROM `tracked_trains`"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `userdb`.`cached_responses` " +
                        "SELECT * FROM `cached_responses`"
                )
            } finally {
                runCatching { db.execSQL("DETACH DATABASE userdb") }
            }
            debugLog("v3 user rows rescued into user.db")
            true
        } catch (e: Exception) {
            debugLog("rescue failed: ${e.message}")
            false
        } finally {
            runCatching { helper.close() }
        }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
