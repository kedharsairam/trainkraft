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
import java.io.File

/**
 * Phase A intelligence-pack tests: [PackDao] CRUD, the v4 -> v5 migration
 * ([TrainDatabase.MIGRATION_4_5]), [PackImporter], and [PackImporter.sha256Hex].
 *
 * Follows the [UserDatabaseMigrationTest] pattern: table DDL is dumped from a
 * live Room in-memory database so the tests track the real schema, and
 * pack.db fixtures are built with [FrameworkSQLiteOpenHelper] on temp files
 * (no Room validation on the fixture side, exactly like a pipeline artifact).
 */
@RunWith(RobolectricTestRunner::class)
class PackSchemaTest {

    private lateinit var context: Context
    private lateinit var db: TrainDatabase
    private lateinit var importer: PackImporter

    /** Contract DDL, whitespace-stripped for byte-agreement comparison. */
    private fun norm(sql: String) = sql.replace("\\s+".toRegex(), "")

    /**
     * DDL comparison form: [norm] plus SQLite's own normalization — the
     * engine drops the `IF NOT EXISTS` clause when storing schema text in
     * `sqlite_master`, so both sides strip it before comparing.
     */
    private fun normDdl(sql: String) = norm(sql).replace("IFNOTEXISTS", "")

    private val contractDdl = mapOf(
        "delay_priors" to
            "CREATE TABLE IF NOT EXISTS `delay_priors` (" +
            "`trainNumber` TEXT NOT NULL, `stationCode` TEXT NOT NULL," +
            "`arrAvgMin` INTEGER NOT NULL DEFAULT 0, `depAvgMin` INTEGER NOT NULL DEFAULT 0," +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`trainNumber`, `stationCode`))",
        "fog_overlays" to
            "CREATE TABLE IF NOT EXISTS `fog_overlays` (" +
            "`trainNumber` TEXT NOT NULL PRIMARY KEY, `action` TEXT NOT NULL," +
            "`fromDate` TEXT NOT NULL, `toDate` TEXT NOT NULL," +
            "`season` TEXT NOT NULL, `note` TEXT NOT NULL DEFAULT '')",
        "pack_meta" to
            "CREATE TABLE IF NOT EXISTS `pack_meta` (`key` TEXT NOT NULL PRIMARY KEY, `value` TEXT NOT NULL)"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = TrainDatabase.inMemory(context)
        importer = PackImporter(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Dumps live CREATE statements from a Room database (tables + indices). */
    private fun dumpSchema(db: SupportSQLiteDatabase): List<String> {
        val out = mutableListOf<String>()
        db.query(
            "SELECT sql FROM sqlite_master WHERE sql NOT NULL " +
                "AND name NOT LIKE 'room_%' AND name != 'android_metadata'"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(cursor.getString(0))
            }
        }
        return out
    }

    private fun tablesOf(db: SupportSQLiteDatabase): Set<String> {
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
            return tables
        }
    }

    private fun tableSql(db: SupportSQLiteDatabase, table: String): String? {
        db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    // ---- PackDao ----

    @Test
    fun `priorsForTrain returns rows ordered by station`() = runBlocking {
        val dao = db.packDao()
        dao.insertPriors(
            listOf(
                DelayPriorEntity("12951", "ST", 4, 6, 1000L),
                DelayPriorEntity("12951", "BRC", 0, 2, 1000L),
                DelayPriorEntity("12952", "BRC", 9, 9, 1000L)
            )
        )
        val rows = dao.priorsForTrain("12951")
        assertEquals(listOf("BRC", "ST"), rows.map { it.stationCode })
        assertEquals(0, rows[0].arrAvgMin)
        assertEquals(2, rows[0].depAvgMin)
        assertEquals(1000L, rows[0].updatedAt)
        assertTrue(dao.priorsForTrain("12639").isEmpty())
    }

    @Test
    fun `fogForTrain matches only inside the date window`() = runBlocking {
        val dao = db.packDao()
        dao.insertOverlays(
            listOf(
                FogOverlayEntity("12417", "REDUCED_FREQ", "2026-12-15", "2027-02-15", "2026-27", "Rly circular W-44")
            )
        )
        val hit = dao.fogForTrain("12417", "2027-01-10")
        assertNotNull(hit)
        assertEquals("REDUCED_FREQ", hit!!.action)
        // Inclusive edges.
        assertNotNull(dao.fogForTrain("12417", "2026-12-15"))
        assertNotNull(dao.fogForTrain("12417", "2027-02-15"))
        // Outside the window / unknown train.
        assertNull(dao.fogForTrain("12417", "2026-12-14"))
        assertNull(dao.fogForTrain("12417", "2027-02-16"))
        assertNull(dao.fogForTrain("12951", "2027-01-10"))
    }

    @Test
    fun `meta and metaAll round-trip pack metadata`() = runBlocking {
        val dao = db.packDao()
        assertNull(dao.meta("packVersion"))
        dao.putMeta(PackMetaEntity("packVersion", "1"))
        dao.putMeta(PackMetaEntity("source", "cris+circulars"))
        assertEquals("1", dao.meta("packVersion"))
        assertEquals(
            mapOf("packVersion" to "1", "source" to "cris+circulars"),
            dao.metaAll()
        )
    }

    // ---- Migration 4 -> 5 ----

    /** Builds an in-memory v4-shaped source DB (v5 DDL minus the pack tables). */
    private fun openV4Source(): SupportSQLiteOpenHelper {
        val ref = TrainDatabase.inMemory(context)
        val v4Ddl = dumpSchema(ref.openHelper.writableDatabase)
            .filter { ddl ->
                contractDdl.keys.none { table -> ddl.contains("`$table`") }
            }
        ref.close()

        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                v4Ddl.forEach { db.execSQL(it) }
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // In-memory: no file bookkeeping.
                .callback(callback)
                .build()
        )
        // Trigger onCreate.
        helper.writableDatabase
        return helper
    }

    @Test
    fun `MIGRATION_4_5 endpoints are 4 to 5`() {
        assertEquals(4, TrainDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, TrainDatabase.MIGRATION_4_5.endVersion)
    }

    @Test
    fun `MIGRATION_4_5 creates empty pack tables with contract DDL and keeps GTFS rows`() {
        val source = openV4Source()
        val sqlite = source.writableDatabase
        sqlite.execSQL(
            "INSERT INTO `stations` (`stop_id`, `code`, `name`, `lat`, `lon`) " +
                "VALUES ('BRC00', 'BRC', 'VADODARA JN', 22.3, 73.2)"
        )

        TrainDatabase.MIGRATION_4_5.migrate(sqlite)

        // Tables exist, empty, and byte-match the contract (modulo whitespace
        // and SQLite's sqlite_master normalization, see normDdl).
        contractDdl.forEach { (table, expected) ->
            val actual = tableSql(sqlite, table)
            assertNotNull("missing table $table", actual)
            assertEquals(table, normDdl(expected), normDdl(actual!!))
            sqlite.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(table, 0, cursor.getInt(0))
            }
        }
        // GTFS rows intact.
        sqlite.query("SELECT `code` FROM `stations` WHERE `stop_id` = 'BRC00'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("BRC", cursor.getString(0))
        }
        source.close()
    }

    @Test
    fun `Room upgrade 4 to 5 opens at version 5 with usable pack DAO`() = runBlocking {
        // Same resolution Room.databaseBuilder(name) uses, so the seed and
        // the upgrade open below hit the identical file.
        val dbName = "pack-migration-4-5.db"
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        // Seed a real v4 file on disk.
        val ref = TrainDatabase.inMemory(context)
        val v4Ddl = dumpSchema(ref.openHelper.writableDatabase)
            .filter { ddl -> contractDdl.keys.none { table -> ddl.contains("`$table`") } }
        ref.close()
        val seed = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        v4Ddl.forEach { db.execSQL(it) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        seed.writableDatabase.execSQL(
            "INSERT INTO `stations` (`stop_id`, `code`, `name`, `lat`, `lon`) " +
                "VALUES ('MMCT00', 'MMCT', 'MUMBAI CENTRAL', 18.9, 72.8)"
        )
        seed.close()

        val upgraded = Room.databaseBuilder(context, TrainDatabase::class.java, dbName)
            .addMigrations(TrainDatabase.MIGRATION_4_5)
            .build()
        try {
            assertEquals(5, upgraded.openHelper.writableDatabase.version)
            // Pack tables usable through the real DAO; GTFS row survived.
            upgraded.packDao().putMeta(PackMetaEntity("packVersion", "1"))
            assertEquals("1", upgraded.packDao().meta("packVersion"))
            val boards = upgraded.trainDao().searchStations("MMCT")
            assertEquals(1, boards.size)
        } finally {
            upgraded.close()
            context.deleteDatabase(dbName)
        }
    }

    // ---- PackImporter fixtures ----

    /**
     * Builds a pipeline-style `pack.db` file with the contract DDL.
     * [extraDdl] runs after the contract tables (for negative fixtures).
     * [packVersion] null omits the key entirely.
     */
    private fun buildPackFile(
        name: String,
        packVersion: String? = "1",
        priors: List<DelayPriorEntity> = emptyList(),
        fogs: List<FogOverlayEntity> = emptyList(),
        extraMeta: Map<String, String> = emptyMap(),
        onlyTables: Set<String>? = null,
    ): File {
        val file = File(context.cacheDir, name)
        if (file.exists()) file.delete()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        if (onlyTables == null || "delay_priors" in onlyTables) {
                            db.execSQL(contractDdl.getValue("delay_priors"))
                        }
                        if (onlyTables == null || "fog_overlays" in onlyTables) {
                            db.execSQL(contractDdl.getValue("fog_overlays"))
                        }
                        if (onlyTables == null || "pack_meta" in onlyTables) {
                            db.execSQL(contractDdl.getValue("pack_meta"))
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val sqlite = helper.writableDatabase
        priors.forEach {
            sqlite.execSQL(
                "INSERT INTO `delay_priors` (`trainNumber`, `stationCode`, `arrAvgMin`, `depAvgMin`, `updatedAt`) " +
                    "VALUES ('${it.trainNumber}', '${it.stationCode}', ${it.arrAvgMin}, ${it.depAvgMin}, ${it.updatedAt})"
            )
        }
        fogs.forEach {
            val note = it.note.replace("'", "''")
            sqlite.execSQL(
                "INSERT INTO `fog_overlays` (`trainNumber`, `action`, `fromDate`, `toDate`, `season`, `note`) " +
                    "VALUES ('${it.trainNumber}', '${it.action}', '${it.fromDate}', '${it.toDate}', '${it.season}', '$note')"
            )
        }
        if (onlyTables == null || "pack_meta" in onlyTables) {
            if (packVersion != null) {
                sqlite.execSQL("INSERT INTO `pack_meta` (`key`, `value`) VALUES ('packVersion', '$packVersion')")
            }
            extraMeta.forEach { (k, v) ->
                sqlite.execSQL("INSERT INTO `pack_meta` (`key`, `value`) VALUES ('$k', '$v')")
            }
        }
        helper.close()
        return file
    }

    private fun attachedAliases(): List<String> {
        db.openHelper.writableDatabase.query("PRAGMA database_list").use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names.add(cursor.getString(1))
            return names
        }
    }

    @Test
    fun `importPack copies rows and stamps importedAt`() = runBlocking {
        val pack = buildPackFile(
            "pack-ok.db",
            priors = listOf(
                DelayPriorEntity("12951", "BRC", 4, 6, 1111L),
                DelayPriorEntity("12951", "ST", 0, 0, 1111L),
                DelayPriorEntity("12952", "BRC", 9, 3, 1111L)
            ),
            fogs = listOf(
                FogOverlayEntity("12417", "CANCELLED", "2026-12-20", "2027-01-10", "2026-27")
            ),
            extraMeta = mapOf("generatedAt" to "2026-09-23T00:00:00Z", "source" to "test")
        )
        try {
            val result = importer.importPack(pack)
            assertTrue(result is PackImporter.ImportResult.Ok)
            assertEquals(2, (result as PackImporter.ImportResult.Ok).importedTrains)
            assertEquals(1, result.fogEntries)

            val dao = db.packDao()
            assertEquals(2, dao.priorsForTrain("12951").size)
            assertEquals(4, dao.priorsForTrain("12951").first { it.stationCode == "BRC" }.arrAvgMin)
            assertEquals("CANCELLED", dao.fogForTrain("12417", "2026-12-25")?.action)
            assertEquals("2026-09-23T00:00:00Z", dao.meta("generatedAt"))
            assertNotNull(dao.meta("importedAt"))

            // DETACH ran: the pack alias is gone (SQLite always lists main+temp).
            assertFalse(attachedAliases().contains("pack"))

            // Re-import is idempotent (REPLACE, same counts).
            val again = importer.importPack(pack)
            assertEquals(result, again)
            assertEquals(2, dao.priorsForTrain("12951").size)
        } finally {
            pack.delete()
        }
    }

    @Test
    fun `importPack returns MissingTables and writes nothing`() = runBlocking {
        val pack = buildPackFile("pack-narrow.db", onlyTables = setOf("pack_meta"))
        try {
            assertTrue(importer.importPack(pack) is PackImporter.ImportResult.MissingTables)
            val dao = db.packDao()
            assertTrue(dao.priorsForTrain("12951").isEmpty())
            assertNull(dao.meta("packVersion"))
            assertNull(dao.meta("importedAt"))
            assertFalse(attachedAliases().contains("pack"))
        } finally {
            pack.delete()
        }
    }

    @Test
    fun `importPack returns UnsupportedVersion and writes nothing`() = runBlocking {
        val pack = buildPackFile(
            "pack-future.db",
            packVersion = "99",
            priors = listOf(DelayPriorEntity("12951", "BRC", 4, 6, 1111L))
        )
        try {
            val result = importer.importPack(pack)
            assertTrue(result is PackImporter.ImportResult.UnsupportedVersion)
            assertEquals("99", (result as PackImporter.ImportResult.UnsupportedVersion).foundVersion)
            assertTrue(db.packDao().priorsForTrain("12951").isEmpty())
            assertFalse(attachedAliases().contains("pack"))
        } finally {
            pack.delete()
        }
    }

    @Test
    fun `importPack treats a missing packVersion key as unsupported`() {
        val pack = buildPackFile("pack-noversion.db", packVersion = null)
        try {
            val result = importer.importPack(pack)
            assertTrue(result is PackImporter.ImportResult.UnsupportedVersion)
            assertNull((result as PackImporter.ImportResult.UnsupportedVersion).foundVersion)
        } finally {
            pack.delete()
        }
    }

    @Test
    fun `importPack fails cleanly on missing or empty files`() {
        val missing = importer.importPack(File(context.cacheDir, "does-not-exist.db"))
        assertTrue(missing is PackImporter.ImportResult.Failed)

        val empty = File(context.cacheDir, "pack-empty.db")
        empty.writeBytes(ByteArray(0))
        try {
            assertTrue(importer.importPack(empty) is PackImporter.ImportResult.Failed)
        } finally {
            empty.delete()
        }
    }

    // ---- sha256Hex ----

    @Test
    fun `sha256Hex matches the known SHA-256 vector`() {
        val file = File(context.cacheDir, "sha-vec.bin")
        try {
            file.writeBytes("abc".toByteArray(Charsets.UTF_8))
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PackImporter.sha256Hex(file)
            )
            file.writeBytes(ByteArray(0))
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                PackImporter.sha256Hex(file)
            )
        } finally {
            file.delete()
        }
    }
}
