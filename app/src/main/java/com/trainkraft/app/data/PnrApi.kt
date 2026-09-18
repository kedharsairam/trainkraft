package com.trainkraft.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * PNR status API client using indianrail.gov.in.
 *
 * Flow:
 *   1. fetchCaptcha() → initializes session, returns captcha Bitmap
 *   2. queryPnr(pnr, captcha) → submits, returns parsed JSON
 *
 * Session is maintained via OkHttp cookie jar.
 */
object PnrApi {

    data class PnrResult(
        val pnrNumber: String,
        val trainNumber: String,
        val trainName: String,
        val dateOfJourney: String,
        val sourceStation: String,
        val destinationStation: String,
        val boardingPoint: String,
        val reservationUpto: String,
        val journeyClass: String,
        val chartStatus: String,
        val quota: String,
        val passengers: List<PnrPassenger>,
        val rawJson: String,
    )

    data class PnrPassenger(
        val serialNumber: Int,
        val bookingStatus: String,
        val currentStatus: String,
        val bookingCoachId: String,
        val currentCoachId: String,
        val bookingBerthNo: Int,
        val currentBerthNo: Int,
    )

    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host].orEmpty()
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:151.0) Gecko/20100101 Firefox/151.0"

    /** Reset session cookies. */
    fun resetSession() {
        cookieStore.clear()
    }

    /**
     * Initialize session and fetch captcha image.
     * Must be called before queryPnr().
     */
    suspend fun fetchCaptcha(): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            resetSession()

            // Step 1: Initialize session by loading PNR page
            val initReq = Request.Builder()
                .url("https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html?locale=en")
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            client.newCall(initReq).execute().close()

            // Step 2: Fetch captcha image
            val ts = System.currentTimeMillis()
            val captchaReq = Request.Builder()
                .url("https://www.indianrail.gov.in/enquiry/captchaDraw.png?${ts}=")
                .header("User-Agent", UA)
                .header("Accept", "image/avif,image/webp,*/*")
                .header("Referer", "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html?locale=en")
                .build()

            val response = client.newCall(captchaReq).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) return@withContext Result.success(bitmap)
                }
            }
            Result.failure(Exception("Failed to load captcha (${response.code})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh captcha without resetting session.
     */
    suspend fun refreshCaptcha(): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val ts = System.currentTimeMillis()
            val req = Request.Builder()
                .url("https://www.indianrail.gov.in/enquiry/captchaDraw.png?${ts}=")
                .header("User-Agent", UA)
                .header("Accept", "image/avif,image/webp,*/*")
                .header("Referer", "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html?locale=en")
                .build()

            val response = client.newCall(req).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) return@withContext Result.success(bitmap)
                }
            }
            Result.failure(Exception("Failed to refresh captcha (${response.code})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Query PNR status with captcha answer.
     */
    suspend fun queryPnr(pnr: String, captchaAnswer: String): Result<PnrResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.indianrail.gov.in/enquiry/CommonCaptcha" +
                    "?inputCaptcha=$captchaAnswer" +
                    "&inputPnrNo=$pnr" +
                    "&inputPage=PNR" +
                    "&language=en"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "*/*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html?locale=en")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Server error (${response.code})"))
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Empty response from server"))
            }

            // Detect error conditions
            if (body.contains("errorMessage")) {
                val json = JSONObject(body)
                val errMsg = json.optString("errorMessage", "")
                when {
                    errMsg.contains("Session out", true) -> {
                        resetSession()
                        return@withContext Result.failure(Exception("Session expired. Please try again."))
                    }
                    errMsg.contains("Captcha not matched", true) -> {
                        return@withContext Result.failure(CaptchaMismatchException())
                    }
                    errMsg.contains("Flushed Pnr", true) -> {
                        return@withContext Result.failure(Exception("PNR not found or not yet generated."))
                    }
                    errMsg.isNotBlank() -> {
                        return@withContext Result.failure(Exception(errMsg))
                    }
                }
            }

            // Parse successful response
            val json = JSONObject(body)
            val pnr = json.optString("pnrNumber", "")
            if (pnr.isBlank()) {
                return@withContext Result.failure(Exception("Invalid response. Please try again."))
            }

            val passengers = mutableListOf<PnrPassenger>()
            val passengerArray = json.optJSONArray("passengerList") ?: JSONArray()
            for (i in 0 until passengerArray.length()) {
                val p = passengerArray.getJSONObject(i)
                passengers.add(
                    PnrPassenger(
                        serialNumber = p.optInt("passengerSerialNumber", i + 1),
                        bookingStatus = p.optString("bookingStatusDetails", ""),
                        currentStatus = p.optString("currentStatusDetails", ""),
                        bookingCoachId = p.optString("bookingCoachId", ""),
                        currentCoachId = p.optString("currentCoachId", ""),
                        bookingBerthNo = p.optInt("bookingBerthNo", 0),
                        currentBerthNo = p.optInt("currentBerthNo", 0),
                    ),
                )
            }

            val result = PnrResult(
                pnrNumber = pnr,
                trainNumber = json.optString("trainNumber", ""),
                trainName = json.optString("trainName", ""),
                dateOfJourney = json.optString("dateOfJourney", ""),
                sourceStation = json.optString("sourceStation", ""),
                destinationStation = json.optString("destinationStation", ""),
                boardingPoint = json.optString("boardingPoint", ""),
                reservationUpto = json.optString("reservationUpto", ""),
                journeyClass = json.optString("journeyClass", ""),
                chartStatus = json.optString("chartStatus", ""),
                quota = json.optString("quota", ""),
                passengers = passengers,
                rawJson = body,
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Exception thrown when captcha doesn't match — caller should refresh captcha. */
    class CaptchaMismatchException : Exception("Captcha didn't match. Please try again.")
}
