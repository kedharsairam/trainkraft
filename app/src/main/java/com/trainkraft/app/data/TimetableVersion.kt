package com.trainkraft.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

/**
 * Offline-timetable versioning (Agent-V3 line).
 *
 * The bundled `trains.db` is a static GTFS snapshot (vintage Aug 2026 — see
 * `timetable-version.json` `_comment` for provenance). This checker answers
 * one question on demand from TimetableScreen: is a newer snapshot published
 * at [REMOTE_URL]?
 *
 * No caching layer by design: versions change monthly at most, the check runs
 * only when the user taps "Check for updates", and a cache would suppress
 * exactly the signal this screen exists to show. No background polling —
 * timetable refreshes ride app updates (the app never downloads timetable
 * data silently), so there is nothing to poll for.
 *
 * All entry points are best-effort and never throw: offline or malformed
 * input yields null / false and the screen says so plainly.
 */
data class TimetableVersion(
    /** Snapshot vintage as YYYY-MM, e.g. "2026-08". */
    val version: String,
    /** ISO build date as shipped, e.g. "2026-08-15". */
    val generatedAt: String,
    /** Row count of the `trains` table in the matching trains.db. */
    val trains: Int,
)

object TimetableVersionChecker {

    const val REMOTE_URL =
        "https://raw.githubusercontent.com/kedharsairam/trainkraft/master/timetable-version.json"

    const val RELEASES_URL = "https://github.com/kedharsairam/trainkraft/releases"

    /** Bundled snapshot asset, kept in sync with the repo-root file each release. */
    const val ASSET_NAME = "timetable-version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10.seconds)
        .readTimeout(10.seconds)
        .callTimeout(15.seconds)
        .build()

    private val VersionRegex = Regex("""^(\d{4})-(0[1-9]|1[0-2])$""")

    /** True only for strict YYYY-MM with a real calendar month. Never throws. */
    fun isValidVersion(version: String): Boolean =
        runCatching { VersionRegex.matches(version.trim()) }.getOrDefault(false)

    /**
     * True when [remote] is strictly newer than [local], compared
     * lexicographically as YYYY-MM (valid versions sort chronologically).
     * Any malformed or blank side returns false. Never throws.
     */
    fun isUpdateAvailable(local: String, remote: String): Boolean {
        val l = local.trim()
        val r = remote.trim()
        if (!isValidVersion(l) || !isValidVersion(r)) return false
        return r > l
    }

    /**
     * Lenient parse of the version-file shape
     * (`version` / `generatedAt` / `trains`; `_comment` ignored).
     * Null on any missing/invalid field or malformed JSON. Never throws.
     */
    fun parse(text: String): TimetableVersion? {
        return runCatching {
            val obj = JSONObject(text)
            val version = obj.optString("version", "").trim()
            val generatedAt = obj.optString("generatedAt", "").trim()
            val trains = obj.optInt("trains", -1)
            if (!isValidVersion(version)) return null
            if (generatedAt.isEmpty()) return null
            if (trains < 0) return null
            TimetableVersion(version = version, generatedAt = generatedAt, trains = trains)
        }.getOrNull()
    }

    /**
     * Reads the bundled asset snapshot. Null when the asset is missing or
     * invalid — the screen then shows a graceful missing-asset state.
     * Never throws.
     */
    fun readLocal(context: Context): TimetableVersion? {
        return runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                parse(reader.readText())
            }
        }.getOrNull()
    }

    /**
     * Best-effort fetch of the repo-root version file; null on any failure
     * (offline, timeout, non-2xx, blank or malformed body). Never throws.
     * [url] is injectable so tests can point at an unreachable host.
     */
    suspend fun fetchRemote(url: String = REMOTE_URL): TimetableVersion? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val text = response.body?.string().orEmpty()
                    if (text.isBlank()) return@runCatching null
                    parse(text)
                }
            }.getOrNull()
        }

    /**
     * Display label for a version: "2026-10" → "Oct 2026".
     * Malformed input passes through unchanged (never throws, never blanks).
     */
    fun monthLabel(version: String): String {
        val trimmed = version.trim()
        val match = runCatching { VersionRegex.matchEntire(trimmed) }.getOrNull()
            ?: return version
        val month = match.groupValues[2].toIntOrNull() ?: return version
        if (month !in 1..12) return version
        return "${MonthShortNames[month - 1]} ${match.groupValues[1]}"
    }

    private val MonthShortNames = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
