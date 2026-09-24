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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the user.db v1→v2 migration ([UserDatabase.MIGRATION_1_2]) for the
 * Phase C approach-alert columns, chained through v2→v3, v3→v4 and v4→v5
 * to the current version 5 (incl. the Go-live [liveTracking] flag default).
 *
 * WHY the columns: approach alerts need a TARGET station, and
 * [TrackedTrainEntity.lastStation] is the last SEEN station — encoding the
 * target anywhere existing would be a hack, so option (a): two new nullable
 * columns on `tracked_trains`:
 * - `watchStationCode` — the station the user wants approach alerts for
 *   (null = no watch; written by the detail bell sheet, peer's UI).
 * - `lastApproachFor` — the watch code already notified this journey
 *   (one-shot dedup so the 30s service loop doesn't spam).
 *
 * Strategy (mirrors [UserDatabaseMigrationTest]): a real v1-shaped file DB
 * (hand DDL matching the v1 entity exactly — 6 columns), one tracked row,
 * then open via Room v2 + MIGRATION_1_2 and assert through the real DAOs:
 * row survives, new columns read null, and the new DAO setters round-trip.
 * Also pins a fresh v2 install (in-memory) carrying a watched station.
 */
@RunWith(RobolectricTestRunner::class)
class WatchStationMigrationTest {

    private lateinit var context: Context
    private val dbName = "watch-migration-test-user.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Creates a v1-shaped user.db file with one tracked row (pre-migration). */
    private fun createV1File(now: Long) {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Exact v1 entity DDL: 6 columns, no watch columns.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracked_trains` (" +
                        "`trainNumber` TEXT NOT NULL, `trackedAt` INTEGER NOT NULL, " +
                        "`lastDelayMin` INTEGER, `lastStation` TEXT, " +
                        "`lastCategory` TEXT, `lastPollAt` INTEGER, " +
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
                "`lastStation`, `lastCategory`, `lastPollAt`) " +
                "VALUES ('12951', $now, 5, 'KOTA', 'ON_TIME', $now)"
        )
        db.execSQL(
            "INSERT INTO `cached_responses` (`cacheKey`, `json`, `fetchedAt`) " +
                "VALUES ('live:12951:x', '{}', $now)"
        )
        helper.close()
    }

    @Test
    fun `MIGRATION_1_2 adds watch columns preserving the tracked row`() = runBlocking {
        val now = System.currentTimeMillis()
        createV1File(now)

        val db = Room.databaseBuilder(context, UserDatabase::class.java, dbName)
            // v2→v3 (travel_fixes), v3→v4 (liveTracking flag) and v4→v5
            // (alert_log) join the chain: the v1 file must migrate
            // 1→2→3→4→5 to reach version 5.
            .addMigrations(
                UserDatabase.MIGRATION_1_2,
                UserDatabase.MIGRATION_2_3,
                UserDatabase.MIGRATION_3_4,
                UserDatabase.MIGRATION_4_5,
            )
            .build()
        try {
            val tracked = db.trackingDao().get("12951")!!
            assertEquals("12951", tracked.trainNumber)
            assertEquals(now, tracked.trackedAt)
            assertEquals(5, tracked.lastDelayMin)
            assertEquals("KOTA", tracked.lastStation)
            // New columns default null on migrated rows.
            assertNull(tracked.watchStationCode)
            assertNull(tracked.lastApproachFor)
            // Go-live flag defaults false (baseline-only reading of old rows).
            assertEquals(false, tracked.liveTracking)

            // Cache table untouched.
            assertEquals("{}", db.cacheDao().get("live:12951:x")?.json)

            // New DAO setters round-trip on the migrated row.
            db.trackingDao().setWatchStation("12951", "BRC")
            assertEquals("BRC", db.trackingDao().get("12951")?.watchStationCode)
            db.trackingDao().markApproachNotified("12951", "BRC")
            assertEquals("BRC", db.trackingDao().get("12951")?.lastApproachFor)
            db.trackingDao().setWatchStation("12951", null)
            assertNull(db.trackingDao().get("12951")?.watchStationCode)
            db.trackingDao().setLiveTracking("12951", true)
            assertEquals(true, db.trackingDao().get("12951")?.liveTracking)
            db.trackingDao().clearAllLiveTracking()
            assertEquals(false, db.trackingDao().get("12951")?.liveTracking)
        } finally {
            db.close()
        }
    }

    @Test
    fun `fresh v2 install carries a watched station`() = runBlocking {
        val db = UserDatabase.inMemory(context)
        try {
            db.trackingDao().upsert(
                TrackedTrainEntity(
                    trainNumber = "12952",
                    trackedAt = 1L,
                    watchStationCode = "MMCT",
                )
            )
            val row = db.trackingDao().get("12952")!!
            assertEquals("MMCT", row.watchStationCode)
            assertNull(row.lastApproachFor)
        } finally {
            db.close()
        }
    }
}
