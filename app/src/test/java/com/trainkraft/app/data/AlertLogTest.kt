package com.trainkraft.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the user.db v4→v5 migration ([UserDatabase.MIGRATION_4_5]) for the
 * on-device alert-log table, plus the [AlertLogDao] round-trip.
 *
 * Strategy (mirrors [TravelTraceTest]): a real v4-shaped file DB (hand DDL
 * matching the v4 entities exactly — 9-column `tracked_trains` with the
 * Phase C watch columns + Go-live `liveTracking` flag, `cached_responses`,
 * `travel_fixes`), one tracked + one cached row, then open via Room v5 +
 * the full migration chain and assert through the real DAOs: old rows
 * survive, `alert_log` starts empty, and the new DAO log/recent/prune/clear
 * round-trips. Also pins a fresh v5 install (in-memory) carrying log rows.
 *
 * Privacy pin: the log table lives in user.db (backup-excluded) — alert
 * history never leaves the device; the 90-day prune cap is asserted here.
 */
@RunWith(RobolectricTestRunner::class)
class AlertLogTest {

    private lateinit var context: Context
    private val dbName = "alert-log-test-user.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Creates a v4-shaped user.db file with one tracked + one cached row. */
    private fun createV4File(now: Long) {
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Exact v4 entity DDL: tracked_trains carries watch columns +
                // the Go-live liveTracking flag (NOT NULL DEFAULT 0).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracked_trains` (" +
                        "`trainNumber` TEXT NOT NULL, `trackedAt` INTEGER NOT NULL, " +
                        "`lastDelayMin` INTEGER, `lastStation` TEXT, " +
                        "`lastCategory` TEXT, `lastPollAt` INTEGER, " +
                        "`watchStationCode` TEXT, `lastApproachFor` TEXT, " +
                        "`liveTracking` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`trainNumber`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_responses` (" +
                        "`cacheKey` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))"
                )
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
                "`lastApproachFor`, `liveTracking`) " +
                "VALUES ('12951', $now, 5, 'KOTA', 'ON_TIME', $now, 'BRC', NULL, 0)"
        )
        db.execSQL(
            "INSERT INTO `cached_responses` (`cacheKey`, `json`, `fetchedAt`) " +
                "VALUES ('live:12951:x', '{}', $now)"
        )
        helper.close()
    }

    @Test
    fun `MIGRATION_4_5 creates alert_log preserving user rows`() = runBlocking {
        val now = System.currentTimeMillis()
        createV4File(now)

        val db = Room.databaseBuilder(context, UserDatabase::class.java, dbName)
            .addMigrations(
                UserDatabase.MIGRATION_1_2,
                UserDatabase.MIGRATION_2_3,
                UserDatabase.MIGRATION_3_4,
                UserDatabase.MIGRATION_4_5,
            )
            .build()
        try {
            // v4 rows survive untouched, watch columns + Go-live flag included.
            val tracked = db.trackingDao().get("12951")!!
            assertEquals("KOTA", tracked.lastStation)
            assertEquals("BRC", tracked.watchStationCode)
            assertEquals(false, tracked.liveTracking)
            assertEquals("{}", db.cacheDao().get("live:12951:x")?.json)

            val log = db.alertLogDao()

            // New table starts empty …
            assertTrue(log.recent().first().isEmpty())

            // … and the DAO round-trips newest-first with the limit honored.
            log.log(AlertLogEntity(trainNumber = "12951", title = "On time", body = "b1", tsEpochMs = now + 1))
            log.log(AlertLogEntity(trainNumber = "12951", title = "Delayed", body = "b2", tsEpochMs = now + 2))
            val all = log.recent().first()
            assertEquals(2, all.size)
            assertEquals(now + 2, all[0].tsEpochMs)
            assertEquals("Delayed", all[0].title)
            assertEquals(now + 1, all[1].tsEpochMs)
            assertEquals(listOf("Delayed"), log.recent(1).first().map { it.title })

            // logAlertWithPrune writes then drops rows older than 90 days.
            assertEquals(90L * 24 * 60 * 60 * 1000, ALERT_LOG_RETENTION_MS)
            logAlertWithPrune(log, "12952", "Old", "stale", now + 3)
            log.pruneBefore(now + 3) // drops the now+1 / now+2 rows only
            val kept = log.recent().first()
            assertEquals(1, kept.size)
            assertEquals("12952", kept[0].trainNumber)

            // Clear-all empties the table; tracked + cache rows are unaffected.
            log.clearAll()
            assertTrue(log.recent().first().isEmpty())
            assertEquals("12951", db.trackingDao().get("12951")?.trainNumber)
            assertEquals("{}", db.cacheDao().get("live:12951:x")?.json)
        } finally {
            db.close()
        }
    }

    @Test
    fun `fresh v5 install carries alert rows alongside user state`() = runBlocking {
        val db = UserDatabase.inMemory(context)
        try {
            val now = System.currentTimeMillis()
            db.trackingDao().upsert(TrackedTrainEntity(trainNumber = "12951", trackedAt = now))
            db.alertLogDao().log(
                AlertLogEntity(trainNumber = "12951", title = "Delayed", body = "15 min late", tsEpochMs = now)
            )
            val rows = db.alertLogDao().recent().first()
            assertEquals(1, rows.size)
            assertEquals("12951", rows[0].trainNumber)
            assertEquals("Delayed", rows[0].title)
            assertEquals("12951", db.trackingDao().get("12951")?.trainNumber)
        } finally {
            db.close()
        }
    }
}
