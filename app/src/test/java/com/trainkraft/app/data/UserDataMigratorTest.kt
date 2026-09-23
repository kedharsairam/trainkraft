package com.trainkraft.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins [UserDataMigrator] — the out-of-transaction v3 user-data rescue.
 *
 * Background: the rescue originally lived INSIDE `MIGRATION_3_4` (ATTACH in
 * Room's migration transaction). Proven on-device (Sep 2026, Realme,
 * trains.db in WAL mode with a live `-wal` file) to throw
 * `SQLiteException: WAL mode cannot be enabled or disabled while there are
 * transactions in progress` — the best-effort catch ate it, the drops still
 * ran, and the followed-train bell was lost. This suite reproduces that
 * exact shape (WAL-mode source with uncheckpointed rows) and pins the rescue.
 */
@RunWith(RobolectricTestRunner::class)
class UserDataMigratorTest {

    private lateinit var context: Context

    private fun trainsFile() = context.getDatabasePath("trains.db")

    private fun userFile() = context.getDatabasePath("user.db")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        trainsFile().parentFile?.mkdirs()
        context.deleteDatabase("trains.db")
        context.deleteDatabase("user.db")
    }

    @After
    fun tearDown() {
        context.deleteDatabase("trains.db")
        context.deleteDatabase("user.db")
    }

    /** Minimal v3-shaped file DB: user tables (6-col, pre-alarm) + rows. */
    private fun buildV3File(walMode: Boolean): SupportSQLiteOpenHelper {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(trainsFile().absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `tracked_trains` (" +
                                "`trainNumber` TEXT NOT NULL PRIMARY KEY, " +
                                "`trackedAt` INTEGER NOT NULL, " +
                                "`lastDelayMin` INTEGER, " +
                                "`lastStation` TEXT, " +
                                "`lastCategory` TEXT, " +
                                "`lastPollAt` INTEGER)"
                        )
                        db.execSQL(
                            "CREATE TABLE `cached_responses` (" +
                                "`cacheKey` TEXT NOT NULL PRIMARY KEY, " +
                                "`json` TEXT NOT NULL, " +
                                "`fetchedAt` INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        if (walMode) {
            // The on-device failure shape: WAL journal with rows the
            // migrator must still see (no explicit checkpoint). PRAGMA
            // returns rows, so it goes through query(), not execSQL().
            db.query("PRAGMA journal_mode=WAL").use { it.moveToFirst() }
        }
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO `tracked_trains` (`trainNumber`, `trackedAt`, " +
                "`lastDelayMin`, `lastStation`, `lastCategory`, `lastPollAt`) " +
                "VALUES ('12951', $now, 0, 'MMCT', 'ON_TIME', $now)"
        )
        db.execSQL(
            "INSERT INTO `cached_responses` (`cacheKey`, `json`, `fetchedAt`) " +
                "VALUES ('live:12951:x', '{\"ok\":true}', $now)"
        )
        return helper
    }

    @Test
    fun `rescues v3 rows into user dot db without touching trains dot db`() = runBlocking {
        val source = buildV3File(walMode = false)
        source.close()

        assertTrue(UserDataMigrator.migrateV3UserTables(context))

        // Rows land in user.db, readable through the real Room DAO (which is
        // the validation that matters — v2 schema must accept them).
        val userDb = Room.databaseBuilder(context, UserDatabase::class.java, "user.db")
            .build()
        try {
            val tracked = userDb.trackingDao().get("12951")
            assertNotNull(tracked)
            assertEquals("MMCT", tracked!!.lastStation)
            assertNull(tracked.watchStationCode)
            val cached = userDb.cacheDao().get("live:12951:x")
            assertNotNull(cached)
            assertEquals("{\"ok\":true}", cached!!.json)
        } finally {
            userDb.close()
        }

        // Source untouched: still v3, tables intact (drops belong to the
        // Room migration, which runs after — never here).
        val check = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(trainsFile().absolutePath)
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
        val db = check.writableDatabase
        db.query("SELECT COUNT(*) FROM `tracked_trains`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        check.close()
    }

    @Test
    fun `rescues from WAL-mode source with uncheckpointed rows`() = runBlocking {
        // Exact reproduction of the Sep 2026 on-device failure: source in WAL
        // mode, rows committed but never checkpointed, migrator reading
        // through its own connection while the file stays open.
        val source = buildV3File(walMode = true)

        assertTrue(UserDataMigrator.migrateV3UserTables(context))

        val userDb = Room.databaseBuilder(context, UserDatabase::class.java, "user.db")
            .build()
        try {
            assertEquals("12951", userDb.trackingDao().get("12951")?.trainNumber)
        } finally {
            userDb.close()
        }
        source.close()
    }

    @Test
    fun `no-ops when nothing to rescue`() {
        // No trains.db at all.
        assertFalse(UserDataMigrator.migrateV3UserTables(context))

        // Wrong version (already migrated): file exists, version 4.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(trainsFile().absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        helper.writableDatabase
        helper.close()
        assertFalse(UserDataMigrator.migrateV3UserTables(context))
        // Failed migrations must not leave user tables behind: either no
        // user.db at all, or a file without our tables.
        if (userFile().exists()) assertTrue(userTablesAbsent())
    }

    private fun userTablesAbsent(): Boolean {
        if (!userFile().exists()) return true
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(userFile().absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        val db = helper.readableDatabase
        val names = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        ).use { cursor ->
            while (cursor.moveToNext()) names.add(cursor.getString(0))
        }
        helper.close()
        return !names.contains("tracked_trains")
    }
}
