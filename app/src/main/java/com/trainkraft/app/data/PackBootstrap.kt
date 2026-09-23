package com.trainkraft.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.trainkraft.app.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase B pack bootstrap (Agent-B-UI).
 *
 * Bundled intelligence packs ship as `assets/pack.db` (built by
 * `tools/packs/build_pack.py`). On first use, [ensureImported] copies the
 * asset to a temp file, compares its `pack_meta` (packVersion/generatedAt)
 * against the local `pack_meta` (+ local `importedAt` stamp), and imports via
 * [PackImporter] only when the asset is newer. Every failure is silent (DEBUG
 * log only) and surfaces as [BootstrapStatus] — callers fall back to
 * pack-absent behavior (empty priors, no fog overlay, no vintage caption).
 *
 * TRIGGER: `AppContainer.database`'s lazy initializer — the earliest safe
 * point that already touches DBs, off the `Application.onCreate` critical
 * path, firing once per process (fire-and-forget coroutine; the lazy
 * initializer cannot suspend). Process-once memo below makes repeat calls
 * free.
 */
object PackBootstrap {

    /** Asset name of the bundled intelligence pack (constant per contract). */
    const val PACK_ASSET_NAME = "pack.db"

    private const val TAG = "PackBootstrap"

    /** First-attempt memo: the asset is immutable at runtime, so one attempt per process is enough. */
    private val memo = AtomicReference<BootstrapStatus?>(null)

    /**
     * Copies `assets/pack.db` to a temp file and imports it when newer than
     * the local pack tables. Never throws: every failure mode resolves to a
     * [BootstrapStatus] value. Safe to call from any thread (does its own IO
     * dispatch); silent except for DEBUG logs.
     *
     * @param importFn injectable import seam for tests — production passes the
     * real [PackImporter.importPack]; tests pass a fake and never touch SQLite.
     */
    suspend fun ensureImported(
        context: Context,
        db: TrainDatabase,
        importFn: (File) -> PackImporter.ImportResult = { file -> PackImporter(db).importPack(file) },
    ): BootstrapStatus {
        memo.get()?.let { return it }
        val status = withContext(Dispatchers.IO) {
            try {
                runBootstrap(context, db, importFn)
            } catch (e: Exception) {
                debugLog("bootstrap failed: ${e.message}")
                BootstrapStatus.Failed(e.message ?: e.toString())
            }
        }
        // Cache everything except Failed so a transient failure (e.g. locked
        // DB) can succeed on the next ViewModel creation.
        if (status !is BootstrapStatus.Failed) memo.compareAndSet(null, status)
        return status
    }

    /** Test-only reset for the process-once memo. */
    internal fun resetForTests() {
        memo.set(null)
    }

    private suspend fun runBootstrap(
        context: Context,
        db: TrainDatabase,
        importFn: (File) -> PackImporter.ImportResult,
    ): BootstrapStatus {
        val tmp: File
        try {
            tmp = File(context.filesDir, "pack_import.db")
            context.assets.open(PACK_ASSET_NAME).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: java.io.FileNotFoundException) {
            // No bundled pack (normal for builds without the pipeline artifact).
            return BootstrapStatus.NoPackAsset
        } catch (e: Exception) {
            debugLog("asset copy failed: ${e.message}")
            return BootstrapStatus.Failed(e.message ?: e.toString())
        }

        val assetMeta = readPackMeta(tmp)
        if (assetMeta == null) {
            debugLog("asset pack_meta unreadable")
            return BootstrapStatus.Failed("pack asset unreadable")
        }
        try {
            val localMeta = try {
                db.packDao().metaAll()
            } catch (e: Exception) {
                debugLog("local meta read failed: ${e.message}")
                return BootstrapStatus.Failed(e.message ?: e.toString())
            }
            if (!shouldImportPack(
                    assetVersion = assetMeta[PackImporter.PACK_VERSION_KEY],
                    assetGeneratedAt = assetMeta["generatedAt"],
                    localVersion = localMeta[PackImporter.PACK_VERSION_KEY],
                    localGeneratedAt = localMeta["generatedAt"],
                    localImportedAt = localMeta[PackImporter.IMPORTED_AT_KEY],
                )
            ) {
                return BootstrapStatus.Current
            }
            val status = when (val result = importFn(tmp)) {
                is PackImporter.ImportResult.Ok ->
                    BootstrapStatus.Imported(result.importedTrains, result.fogEntries)
                is PackImporter.ImportResult.MissingTables ->
                    BootstrapStatus.Failed("pack asset missing contract tables")
                is PackImporter.ImportResult.UnsupportedVersion ->
                    BootstrapStatus.Failed("unsupported pack version ${result.foundVersion}")
                is PackImporter.ImportResult.Failed ->
                    BootstrapStatus.Failed(result.reason)
            }
            return status
        } finally {
            // The temp copy served its purpose — don't litter ~2MB per
            // install. Deletion failure is cosmetic; the next bootstrap
            // overwrites the file.
            runCatching { tmp.delete() }
        }
    }

    /**
     * Reads `pack_meta` from a pack.db file on disk (the just-copied asset).
     * Null when the file isn't a readable pack — the caller treats that as a
     * failed bootstrap, never as an empty pack.
     */
    internal fun readPackMeta(packFile: File): Map<String, String>? {
        var sqlite: SQLiteDatabase? = null
        return try {
            sqlite = SQLiteDatabase.openDatabase(
                packFile.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY,
            )
            val out = mutableMapOf<String, String>()
            sqlite.rawQuery("SELECT `key`, `value` FROM `pack_meta`", null).use { cursor ->
                while (cursor.moveToNext()) {
                    out[cursor.getString(0)] = cursor.getString(1)
                }
            }
            out
        } catch (e: Exception) {
            debugLog("readPackMeta failed: ${e.message}")
            null
        } finally {
            try {
                sqlite?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun debugLog(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }
}

/**
 * Pure import decision: import when the local pack was never stamped
 * ([localImportedAt] blank — fresh install, pre-pack DB, or wiped tables), or
 * when the asset's version/generation differs from local. Never throws.
 */
fun shouldImportPack(
    assetVersion: String?,
    assetGeneratedAt: String?,
    localVersion: String?,
    localGeneratedAt: String?,
    localImportedAt: String?,
): Boolean {
    if (localImportedAt.isNullOrBlank()) return true
    if (assetVersion != null && assetVersion != localVersion) return true
    if (assetGeneratedAt != null && assetGeneratedAt != localGeneratedAt) return true
    return false
}

/** Result of [PackBootstrap.ensureImported]. */
sealed interface BootstrapStatus {
    /** Fresh import: [trains] distinct trains, [fog] overlay rows (pack stats, not local totals). */
    data class Imported(val trains: Int, val fog: Int) : BootstrapStatus

    /** Local pack already matches the asset — no work done. */
    data object Current : BootstrapStatus

    /** No `pack.db` asset bundled — normal, silent. */
    data object NoPackAsset : BootstrapStatus

    /** Anything else (copy/DB/import failure) — silent, pack-absent fallback applies. */
    data class Failed(val reason: String) : BootstrapStatus
}
