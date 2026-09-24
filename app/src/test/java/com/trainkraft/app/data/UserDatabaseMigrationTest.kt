package com.trainkraft.app.data

import android.content.Context
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
import java.io.File

/**
 * Verifies the v4 privacy split ([TrainDatabase.MIGRATION_3_4]).
 *
 * Strategy: a v3-shaped database is built with [FrameworkSQLiteOpenHelper]
 * (no Room validation on the source side, exactly like a real on-device v3
 * file), seeded with one static row + user rows, then migrated. Table DDL is
 * dumped from live Room in-memory databases so the test tracks the real
 * schema instead of a hand-written copy.
 *
 * Provenance: the split (Sep 2026) must preserve Kedhar's existing follows
 * (e.g. 12951) on first launch post-upgrade; this test pins that contract.
 */
@RunWith(RobolectricTestRunner::class)
class UserDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var userFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Isolated ATTACH target for the verification opens below.
        userFile = context.getDatabasePath("migration-test-user.db")
        userFile.parentFile?.mkdirs()
        userFile.delete()
    }

    @After
    fun tearDown() {
        context.deleteDatabase("migration-test-user.db")
    }

    /** Dumps live CREATE statements from a Room database (tables + indices). */
    private fun dumpSchema(db: SupportSQLiteDatabase): List<String> {
        val out = mutableListOf<String>()
        // sqlite_sequence is an internal AUTOINCREMENT artifact (present since
        // the v5 alert_log's auto-id PK) — never re-execute its CREATE.
        db.query("SELECT sql FROM sqlite_master WHERE sql NOT NULL AND name NOT LIKE 'room_%' AND name != 'android_metadata' AND name != 'sqlite_sequence'").use { cursor ->
            while (cursor.moveToNext()) {
                out.add(cursor.getString(0))
            }
        }
        return out
    }

    /**
     * True v3 user-table shape, hardcoded (NOT dumped from live Room): real
     * on-device v3 files predate the Phase C alarm columns. The migration
     * must accept this 6-column shape (explicit column list on copy) as well
     * as v2-shaped files — both are pinned by tests.
     */
    private val v3UserDdl = listOf(
        "CREATE TABLE `tracked_trains` (" +
            "`trainNumber` TEXT NOT NULL PRIMARY KEY, " +
            "`trackedAt` INTEGER NOT NULL, " +
            "`lastDelayMin` INTEGER, " +
            "`lastStation` TEXT, " +
            "`lastCategory` TEXT, " +
            "`lastPollAt` INTEGER)",
        "CREATE TABLE `cached_responses` (" +
            "`cacheKey` TEXT NOT NULL PRIMARY KEY, " +
            "`json` TEXT NOT NULL, " +
            "`fetchedAt` INTEGER NOT NULL)",
    )

    /** Builds an in-memory v3-shaped source DB: static GTFS + user tables. */
    private fun openV3Source(): SupportSQLiteOpenHelper {
        // Real DDL straight from Room so schema drift breaks this test loudly.
        val trainsRef = TrainDatabase.inMemory(context)
        val trainsDdl = dumpSchema(trainsRef.openHelper.writableDatabase)
        trainsRef.close()

        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                trainsDdl.forEach { db.execSQL(it) }
                v3UserDdl.forEach { db.execSQL(it) }
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // In-memory: no file bookkeeping, ATTACH still works.
                .callback(callback)
                .build()
        )
        // Trigger onCreate.
        helper.writableDatabase
        return helper
    }

    @Test
    fun `MIGRATION_3_4 drops user tables and leaves static rows intact`() = runBlocking {
        // Row rescue is UserDataMigrator's job (pre-open, covered there);
        // the migration itself only removes the old tables.
        val source = openV3Source()
        val db = source.writableDatabase
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO `stations` (`stop_id`, `code`, `name`, `lat`, `lon`) " +
                "VALUES ('MMCT00', 'MMCT', 'MUMBAI CENTRAL', 18.9, 72.8)"
        )
        db.execSQL(
            "INSERT INTO `tracked_trains` (`trainNumber`, `trackedAt`, `lastDelayMin`, " +
                "`lastStation`, `lastCategory`, `lastPollAt`) " +
                "VALUES ('12951', $now, 0, 'MMCT', 'ON_TIME', $now)"
        )
        db.execSQL(
            "INSERT INTO `cached_responses` (`cacheKey`, `json`, `fetchedAt`) " +
                "VALUES ('live:12951:23-SEP-2026', '{\"ok\":true}', $now)"
        )

        TrainDatabase.MIGRATION_3_4.migrate(db)

        // Old tables gone from trains.db ...
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
            assertFalse(tables.contains("tracked_trains"))
            assertFalse(tables.contains("cached_responses"))
            assertTrue(tables.contains("stations"))
        }
        // ... static rows intact ...
        db.query("SELECT `code`, `name` FROM `stations` WHERE `stop_id` = 'MMCT00'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("MMCT", cursor.getString(0))
            assertEquals("MUMBAI CENTRAL", cursor.getString(1))
        }
        source.close()
    }

    @Test
    fun `MIGRATION_3_4 drops v2-shaped user tables without touching values`() = runBlocking {
        // A file already carrying the Phase C alarm columns: drops must not
        // care about table shape (rescue, if any, is the migrator's job).
        val trainsRef = TrainDatabase.inMemory(context)
        val trainsDdl = dumpSchema(trainsRef.openHelper.writableDatabase)
        trainsRef.close()
        val userRef = UserDatabase.inMemory(context)
        val userDdl = dumpSchema(userRef.openHelper.writableDatabase)
        userRef.close()

        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                trainsDdl.forEach { db.execSQL(it) }
                userDdl.forEach { db.execSQL(it) }
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) = Unit
        }
        val source = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(callback)
                .build()
        )
        val db = source.writableDatabase
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO `tracked_trains` (`trainNumber`, `trackedAt`, " +
                "`watchStationCode`) VALUES ('12952', $now, 'BZA')"
        )

        TrainDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
            assertFalse(tables.contains("tracked_trains"))
            assertFalse(tables.contains("cached_responses"))
        }
        source.close()
    }

    @Test
    fun `MIGRATION_3_4 drops user tables unconditionally`() {
        val source = openV3Source()
        val db = source.writableDatabase
        db.execSQL(
            "INSERT INTO `tracked_trains` (`trainNumber`, `trackedAt`) VALUES ('12951', 1)"
        )

        // Data rescue lives in UserDataMigrator (pre-open); the migration
        // itself only drops, so it cannot fail the upgrade.
        TrainDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
            assertFalse(tables.contains("tracked_trains"))
            assertFalse(tables.contains("cached_responses"))
        }
        source.close()
    }

    @Test
    fun `trains dot db no longer declares user DAOs`() {
        // Room's @Database is BINARY retention (invisible to reflection), so
        // assert on the declared DAO accessors instead — same contract:
        // user tables live in user.db only.
        val trainsMethods = TrainDatabase::class.java.declaredMethods.map { it.name }
        assertFalse(trainsMethods.contains("trackingDao"))
        assertFalse(trainsMethods.contains("cacheDao"))
        assertTrue(trainsMethods.contains("trainDao"))

        val userMethods = UserDatabase::class.java.declaredMethods.map { it.name }
        assertTrue(userMethods.contains("trackingDao"))
        assertTrue(userMethods.contains("cacheDao"))
    }

    @Test
    fun `container serves tracking and cache from user dot db`() = runBlocking {
        val container = com.trainkraft.app.di.AppContainer(context)
        val now = System.currentTimeMillis()
        container.trackingDao.upsert(
            TrackedTrainEntity(trainNumber = "12951", trackedAt = now)
        )
        container.cacheDao.put(
            CachedResponseEntity("live:12951:x", "{}", now)
        )

        // Same singleton the container holds: rows round-trip through user.db.
        val direct = UserDatabase.getInstance(context)
        assertTrue(container.userDatabase === direct)
        assertEquals("12951", direct.trackingDao().get("12951")?.trainNumber)
        assertEquals("{}", direct.cacheDao().get("live:12951:x")?.json)

        // And trains.db cannot serve them: no such DAOs exist anymore.
        assertNull(
            TrainDatabase::class.java.methods.firstOrNull { it.name == "trackingDao" }
        )
        assertNull(
            TrainDatabase::class.java.methods.firstOrNull { it.name == "cacheDao" }
        )

        container.trackingDao.delete("12951")
    }
}
