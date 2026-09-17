package com.trainkraft.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client for the NTES AppServAnd endpoint.
 *
 * Mirrors the verified Python implementation
 * (train-status-deep/ntes-client/ntes/client.py, Sep 2026):
 * POST https://enquiry.indianrail.gov.in/crisns/AppServAnd with
 * body {"jsonIn": "<NtesCrypto.encrypt(payload)>"},
 * Content-Type application/json and the Dalvik User-Agent; the reply is
 * either {"jsonIn": "<encrypted>"} or {"AlertMsg": "..."}.
 *
 * All functions return the raw decrypted JSON string on success and a
 * failed [Result] carrying the server / transport error otherwise.
 */
object NtesApi {

    private const val TAG = "NtesApi"
    private const val BASE_URL = "https://enquiry.indianrail.gov.in/crisns/AppServAnd"
    private const val USER_AGENT = "Dalvik/2.1.0 (Linux; Android 11)"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val ALERT_KEYS = arrayOf("AlertMsg", "alertMsg", "AlertMsgHindi", "alertMsgHindi")

    /** Extracts a server-side error message, if the decoded JSON carries one. */
    private fun extractError(decoded: String): String? {
        val trimmed = decoded.trimStart()
        if (!trimmed.startsWith("{")) return null
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        for (key in ALERT_KEYS) {
            val msg = obj.optString(key, "").trim()
            if (msg.isNotEmpty()) return msg
        }
        return null
    }

    private suspend fun request(payload: String, keys: NtesKeys = NtesKeys()): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            android.util.Log.d(TAG, "Request payload: $payload")
            val bodyJson = JSONObject().put("jsonIn", NtesCrypto.encrypt(payload, keys)).toString()
            android.util.Log.d(TAG, "POST $BASE_URL")
            val request = Request.Builder()
                .url(BASE_URL)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                android.util.Log.d(TAG, "HTTP ${response.code}")
                val text = response.body?.string().orEmpty()
                android.util.Log.d(TAG, "Response length: ${text.length}")
                if (text.isBlank()) throw IllegalStateException("empty response")
                val data = JSONObject(text)

                // {"jsonIn": "<encrypted>"} → decrypt; anything else (e.g. a
                // top-level {"AlertMsg": ...}) is used as-is, like Python's _decode.
                val decoded = if (data.has("jsonIn")) {
                    NtesCrypto.decrypt(data.getString("jsonIn"), keys)
                } else {
                    text
                }

                extractError(decoded)?.let { throw IllegalStateException(it) }
                decoded
            }
        }
    }

    /**
     * Live running status for [trainNumber] on [date] (format DD-MMM-YYYY,
     * e.g. "18-SEP-2026").
     *
     * @param keys crypto keys (remote-configurable via [NtesConfig]);
     * defaults to the hardcoded constants.
     */
    suspend fun liveStatus(
        trainNumber: String,
        date: String,
        keys: NtesKeys = NtesKeys(),
    ): Result<String> =
        request(
            "service=TrainRunningMob&subService=ShowFullRunJson" +
                "&trainNo=$trainNumber&startDate=$date",
            keys,
        )

    /**
     * Train search / autocomplete for a number-or-name [query].
     *
     * @param keys crypto keys (remote-configurable via [NtesConfig]);
     * defaults to the hardcoded constants.
     */
    suspend fun searchTrains(query: String, keys: NtesKeys = NtesKeys()): Result<String> =
        request(
            "service=TrainRunningMob&subService=FindTrainJson&trainNo=$query",
            keys,
        )
}
