package com.trainkraft.app.data

import java.io.File
import java.security.MessageDigest

/**
 * Imports a pipeline-built intelligence pack (`pack.db`) into [TrainDatabase].
 *
 * Flow: ATTACH the pack file → verify the three contract tables exist (else
 * [ImportResult.MissingTables], no-op) → verify `pack_meta.packVersion`
 * equals [SUPPORTED_PACK_VERSION] (else [ImportResult.UnsupportedVersion],
 * no-op) → `INSERT OR REPLACE ... SELECT` all three tables → stamp local
 * `pack_meta.importedAt` with the current epoch ms → DETACH in `finally`.
 *
 * Safety: missing/empty/unreadable files yield [ImportResult.Failed] and the
 * function never throws — callers (a future download worker / settings UI)
 * can surface the reason directly. A failed import never mutates local pack
 * tables: every validation gate runs before the first INSERT.
 *
 * Manifest check: [sha256Hex] is the pure hashing primitive the future
 * signed-manifest verification will use (hash the downloaded file, compare
 * against the manifest's expected digest, import only on match). That wiring
 * lands with the download channel; Phase A pins the helper with a
 * known-vector test.
 */
class PackImporter(private val db: TrainDatabase) {

    companion object {
        /** Only pack.db files stamped with this `packVersion` are imported. */
        const val SUPPORTED_PACK_VERSION = 1

        /** pack_meta key stamped (epoch ms) after every successful import. */
        const val IMPORTED_AT_KEY = "importedAt"

        /** pack_meta key carrying the pipeline's format version. */
        const val PACK_VERSION_KEY = "packVersion"

        private const val ATTACH_ALIAS = "pack"

        /**
         * Lowercase hex SHA-256 of a file's bytes (stdlib MessageDigest).
         *
         * Pure function: used by Phase A tests now, and by the signed-manifest
         * check when the download channel lands (compare against the manifest
         * digest before calling [PackImporter.importPack]).
         */
        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Result of [importPack].
     *
     * Ok carries pack statistics (not local table totals): [importedTrains]
     * is the count of distinct trainNumbers in the pack's `delay_priors`;
     * [fogEntries] is the pack's `fog_overlays` row count.
     */
    sealed interface ImportResult {
        data class Ok(val importedTrains: Int, val fogEntries: Int) : ImportResult
        data object MissingTables : ImportResult
        data class UnsupportedVersion(val foundVersion: String?) : ImportResult
        data class Failed(val reason: String) : ImportResult
    }

    /**
     * Imports [packFile] (a pipeline-built `pack.db`) into this importer's
     * [TrainDatabase]. See the class KDoc for the gate order and no-op
     * guarantees. Never throws: all failures surface as [ImportResult.Failed].
     */
    fun importPack(packFile: File): ImportResult {
        if (!packFile.isFile) {
            return ImportResult.Failed("pack file not found: ${packFile.path}")
        }
        if (packFile.length() == 0L) {
            return ImportResult.Failed("pack file is empty: ${packFile.path}")
        }
        val sqlite = db.openHelper.writableDatabase
        val escaped = packFile.absolutePath.replace("'", "''")
        var attached = false
        try {
            try {
                sqlite.execSQL("ATTACH DATABASE '$escaped' AS `$ATTACH_ALIAS`")
            } catch (e: Exception) {
                return ImportResult.Failed("ATTACH failed: ${e.message}")
            }
            attached = true

            if (!hasPackTables(sqlite)) {
                return ImportResult.MissingTables
            }

            val version = packVersion(sqlite)
            if (version?.toIntOrNull() != SUPPORTED_PACK_VERSION) {
                return ImportResult.UnsupportedVersion(version)
            }

            val importedTrains = countDistinctTrains(sqlite)
            val fogEntries = countFogEntries(sqlite)

            sqlite.execSQL("INSERT OR REPLACE INTO `delay_priors` SELECT * FROM `$ATTACH_ALIAS`.`delay_priors`")
            sqlite.execSQL("INSERT OR REPLACE INTO `fog_overlays` SELECT * FROM `$ATTACH_ALIAS`.`fog_overlays`")
            sqlite.execSQL("INSERT OR REPLACE INTO `pack_meta` SELECT * FROM `$ATTACH_ALIAS`.`pack_meta`")
            sqlite.execSQL(
                "INSERT OR REPLACE INTO `pack_meta` (`key`, `value`) VALUES ('$IMPORTED_AT_KEY', '${System.currentTimeMillis()}')"
            )

            return ImportResult.Ok(importedTrains, fogEntries)
        } catch (e: Exception) {
            return ImportResult.Failed(e.message ?: e.toString())
        } finally {
            if (attached) {
                try {
                    sqlite.execSQL("DETACH DATABASE `$ATTACH_ALIAS`")
                } catch (_: Exception) {
                    // Best-effort: the import result already decided.
                }
            }
        }
    }

    /** True when all three contract tables exist in the attached pack. */
    private fun hasPackTables(sqlite: androidx.sqlite.db.SupportSQLiteDatabase): Boolean {
        sqlite.query(
            "SELECT COUNT(*) FROM `$ATTACH_ALIAS`.sqlite_master " +
                "WHERE type = 'table' AND name IN ('delay_priors', 'fog_overlays', 'pack_meta')"
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) == 3
        }
    }

    /** The pack's `packVersion` value, or null when the key is absent. */
    private fun packVersion(sqlite: androidx.sqlite.db.SupportSQLiteDatabase): String? {
        sqlite.query(
            "SELECT `value` FROM `$ATTACH_ALIAS`.`pack_meta` WHERE `key` = '$PACK_VERSION_KEY'"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    /** Distinct trainNumbers in the pack's `delay_priors` (for [ImportResult.Ok]). */
    private fun countDistinctTrains(sqlite: androidx.sqlite.db.SupportSQLiteDatabase): Int {
        sqlite.query("SELECT COUNT(DISTINCT `trainNumber`) FROM `$ATTACH_ALIAS`.`delay_priors`").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    /** Row count of the pack's `fog_overlays` (for [ImportResult.Ok]). */
    private fun countFogEntries(sqlite: androidx.sqlite.db.SupportSQLiteDatabase): Int {
        sqlite.query("SELECT COUNT(*) FROM `$ATTACH_ALIAS`.`fog_overlays`").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }
}
