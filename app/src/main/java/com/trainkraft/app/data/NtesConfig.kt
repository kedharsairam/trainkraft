package com.trainkraft.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Remote-configurable NTES crypto keys.
 *
 * Remote file (`ntes-keys.json`) format:
 * ```json
 * {
 *   "key": "***REMOVED***",
 *   "iv": "***REMOVED***",
 *   "sckey": "***REMOVED***",
 *   "endpoint": "https://enquiry.indianrail.gov.in/crisns/AppServAnd",
 *   "version": 1
 * }
 * ```
 * - `key` / `iv`: 16 ASCII chars each (AES-128 key + CBC IV).
 * - `sckey`: hex string used in the MD5(data + sckey) integrity hash.
 * - `endpoint`: informational only (the app keeps using its baked-in URL).
 * - `version`: informational integer, bump when rotating keys.
 *
 * Missing/blank fields fall back to the hardcoded defaults per-field.
 */
data class NtesKeys(
    val key: String = NtesCrypto.KEY,
    val iv: String = NtesCrypto.IV,
    val sckey: String = NtesCrypto.SCKEY,
)

object NtesConfig {

    const val REMOTE_URL =
        "https://raw.githubusercontent.com/kedharsairam/trainkraft/main/ntes-keys.json"

    private const val CACHE_FILE_NAME = "ntes-keys-cache.json"

    /** 7-day cache TTL. */
    const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    /** Hardcoded fallback — always available, used when fetch/cache miss. */
    val FALLBACK = NtesKeys()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var memoryCache: NtesKeys? = null

    @Volatile
    private var memoryCacheAt: Long = 0L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Optional: call from Application.onCreate so [getKeys] can file-cache. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Returns effective keys: fresh file cache (< 7 days) else remote fetch
     * (caching success) else stale cache else [FALLBACK]. Never throws.
     */
    suspend fun getKeys(): NtesKeys {
        val context = appContext
        return if (context != null) getKeys(context) else fetchRemote() ?: FALLBACK
    }

    /** Same as [getKeys] but with an explicit [Context] for the file cache. */
    suspend fun getKeys(context: Context): NtesKeys {
        val app = context.applicationContext
        if (appContext == null) appContext = app

        // In-memory fast path.
        val mem = memoryCache
        if (mem != null && System.currentTimeMillis() - memoryCacheAt < CACHE_TTL_MS) {
            return mem
        }

        // Fresh file cache.
        val cached = readCache(app)
        if (cached != null && System.currentTimeMillis() - cached.second < CACHE_TTL_MS) {
            memoryCache = cached.first
            memoryCacheAt = cached.second
            return cached.first
        }

        // Remote fetch.
        val remote = fetchRemote()
        if (remote != null) {
            val now = System.currentTimeMillis()
            memoryCache = remote
            memoryCacheAt = now
            writeCache(app, remote, now)
            return remote
        }

        // Stale cache still beats hardcoded when the network is down.
        if (cached != null) {
            memoryCache = cached.first
            memoryCacheAt = cached.second
            return cached.first
        }

        return FALLBACK
    }

    /** Best-effort network fetch; null on any failure. */
    private suspend fun fetchRemote(): NtesKeys? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(REMOTE_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) return@runCatching null
                parseKeys(JSONObject(text))
            }
        }.getOrNull()
    }

    /** Lenient parse: accepts lower/upper-case field names, falls back per-field. */
    internal fun parseKeys(obj: JSONObject): NtesKeys? {
        fun field(vararg names: String): String? {
            for (name in names) {
                val value = obj.optString(name, "").trim()
                if (value.isNotEmpty()) return value
            }
            return null
        }
        val key = field("key", "KEY") ?: FALLBACK.key
        val iv = field("iv", "IV") ?: FALLBACK.iv
        val sckey = field("sckey", "SCKEY", "scKey", "sckey".uppercase()) ?: FALLBACK.sckey
        if (key.length != 16 || iv.length != 16) return null
        if (sckey.isBlank()) return null
        return NtesKeys(key = key, iv = iv, sckey = sckey)
    }

    private fun cacheFile(context: Context): File =
        File(context.filesDir, CACHE_FILE_NAME)

    private fun readCache(context: Context): Pair<NtesKeys, Long>? {
        return runCatching {
            val file = cacheFile(context)
            if (!file.exists()) return null
            val obj = JSONObject(file.readText())
            val keysObj = if (obj.has("keys")) obj.getJSONObject("keys") else obj
            val keys = parseKeys(keysObj) ?: return null
            val cachedAt = obj.optLong("cachedAt", file.lastModified())
            keys to cachedAt
        }.getOrNull()
    }

    private fun writeCache(context: Context, keys: NtesKeys, now: Long) {
        runCatching {
            val obj = JSONObject()
                .put("cachedAt", now)
                .put(
                    "keys",
                    JSONObject()
                        .put("key", keys.key)
                        .put("iv", keys.iv)
                        .put("sckey", keys.sckey),
                )
            cacheFile(context).writeText(obj.toString())
        }
    }
}
