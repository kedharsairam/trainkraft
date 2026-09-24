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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the user.db v2→v3 migration ([UserDatabase.MIGRATION_2_3]) for the
 * Phase D travel-fix trace table, plus the [TravelTraceDao] round-trip.
 *
 * Strategy (mirrors [WatchStationMigrationTest]): a real v2-shaped file DB
 * (hand DDL matching the v2 entities exactly — 8-column `tracked_trains`
 * with the Phase C watch columns + `cached_responses`), one tracked row,
 * then open via Room v5 + MIGRATION_1_2 + MIGRATION_2_3 + MIGRATION_3_4 +
 * MIGRATION_4_5 and assert through
 * the real DAOs: old rows survive, `travel_fixes` starts empty, and the new
 * DAO insert/query/prune round-trips. Also pins a fresh v3 install
 * (in-memory) carrying trace rows.
 *
 * Privacy pin: the trace table lives in user.db (backup-excluded) — raw
 * fixes never leave the device; the 30-day prune cap is asserted here.
 */
@RunWith(RobolectricTestRunner::class)
class TravelTraceTest {

    private lateinit var context: Context
    private val dbName = "travel-trace-test-user.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Creates a v2-shaped user.db file with one tracked + one cached row. */
    private fun createV2File(now: Long) {
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Exact v2 entity DDL: tracked_trains gains the watch columns.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracked_trains` (" +
                        "`trainNumber` TEXT NOT NULL, `trackedAt` INTEGER NOT NULL, " +
                        "`lastDelayMin` INTEGER, `lastStation` TEXT, " +
                        "`lastCategory` TEXT, `lastPollAt` INTEGER, " +
                        "`watchStationCode` TEXT, `lastApproachFor` TEXT, " +
                        "PRIMARY KEY(`trainNumber`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_responses` (" +
                        "`cacheKey` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))"
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO `tracked_trains` (`trainNumber`, `trackedAt`, `lastDelayMin`, " +
                "`lastStation`, `lastCategory`, `lastPollAt`, `watchStationCode`, " +
                "`lastApproachFor`) " +
                "VALUES ('12951', $now, 5, 'KOTA', 'ON_TIME', $now, 'BRC', NULL)"
        )
        db.execSQL(
            "INSERT INTO `cached_responses` (`cacheKey`, `json`, `fetchedAt`) " +
                "VALUES ('live:12951:x', '{}', $now)"
        )
        helper.close()
    }

    @Test
    fun `MIGRATION_2_3 creates travel_fixes preserving user rows`() = runBlocking {
        val now = System.currentTimeMillis()
        createV2File(now)

        val db = Room.databaseBuilder(context, UserDatabase::class.java, dbName)
            .addMigrations(
                UserDatabase.MIGRATION_1_2,
                UserDatabase.MIGRATION_2_3,
                UserDatabase.MIGRATION_3_4,
                UserDatabase.MIGRATION_4_5,
            )
            .build()
        try {
            // v2 rows survive untouched, watch columns included.
            val tracked = db.trackingDao().get("12951")!!
            assertEquals("KOTA", tracked.lastStation)
            assertEquals("BRC", tracked.watchStationCode)
            assertNull(tracked.lastApproachFor)
            assertEquals("{}", db.cacheDao().get("live:12951:x")?.json)

            // New table starts empty …
            assertTrue(db.travelTraceDao().fixesForTrain("12951").isEmpty())

            // … and the DAO round-trips (ordered oldest-first).
            val trace = db.travelTraceDao()
            trace.insert(TravelFixEntity(trainNumber = "12951", tsEpochMs = now + 2, lat = 22.3, lon = 73.18, speedKmh = 60.0, accuracyM = 10f))
            trace.insert(TravelFixEntity(trainNumber = "12951", tsEpochMs = now + 1, lat = 22.2, lon = 73.1, speedKmh = 55.0, accuracyM = 12f))
            val fixes = trace.fixesForTrain("12951")
            assertEquals(2, fixes.size)
            assertEquals(now + 1, fixes[0].tsEpochMs)
            assertEquals(60.0, fixes[1].speedKmh, 1e-9)
            assertEquals("12951", fixes[0].trainNumber)

            // Trains are isolated; the 30-day prune cap drops only old rows.
            trace.insert(TravelFixEntity(trainNumber = "12952", tsEpochMs = now, lat = 0.0, lon = 0.0, speedKmh = 0.0, accuracyM = 5f))
            trace.pruneBefore(now + 2)
            val kept = trace.fixesForTrain("12951")
            assertEquals(1, kept.size)
            assertEquals(now + 2, kept[0].tsEpochMs)
            assertTrue(trace.fixesForTrain("12952").isEmpty())
            assertTrue(trace.fixesForTrain("NOPE").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `fresh v3 install carries trace rows alongside user state`() = runBlocking {
        val db = UserDatabase.inMemory(context)
        try {
            val now = System.currentTimeMillis()
            db.trackingDao().upsert(TrackedTrainEntity(trainNumber = "12951", trackedAt = now))
            db.travelTraceDao().insert(
                TravelFixEntity(
                    trainNumber = "12951", tsEpochMs = now,
                    lat = 18.97, lon = 72.82, speedKmh = 0.0, accuracyM = 8f,
                )
            )
            val fixes = db.travelTraceDao().fixesForTrain("12951")
            assertEquals(1, fixes.size)
            assertEquals(18.97, fixes[0].lat, 1e-9)
            assertEquals("12951", db.trackingDao().get("12951")?.trainNumber)
        } finally {
            db.close()
        }
    }
}
