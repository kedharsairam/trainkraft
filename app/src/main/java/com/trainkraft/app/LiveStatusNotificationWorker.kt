package com.trainkraft.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trainkraft.app.data.NtesApi
import com.trainkraft.app.data.NtesConfig
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Background worker that polls NTES live status every 10 minutes
 * and posts a notification when the train is delayed or approaching a station.
 *
 * Usage:
 *   LiveStatusNotification.start(context, "12952")
 *   LiveStatusNotification.stop(context)
 */
class LiveStatusNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "live_status_updates"
        private const val WORK_NAME_PREFIX = "live_status_"
        private const val TRAIN_NUMBER_KEY = "train_number"

        fun start(context: Context, trainNumber: String) {
            createNotificationChannel(context)
            val request = PeriodicWorkRequestBuilder<LiveStatusNotificationWorker>(
                10, TimeUnit.MINUTES,
            )
                .setInputData(workDataOf(TRAIN_NUMBER_KEY to trainNumber))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PREFIX + trainNumber,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        fun stop(context: Context, trainNumber: String) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME_PREFIX + trainNumber)
        }

        private fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Train Status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Periodic live status updates for tracked trains"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        val trainNumber = inputData.getString(TRAIN_NUMBER_KEY) ?: return Result.failure()

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success() // Can't post, but don't fail
            }
        }

        return try {
            val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            val date = dateFormat.format(Date()).uppercase(Locale.ENGLISH)
            val keys = NtesConfig.getKeys(applicationContext)
            val result = NtesApi.liveStatus(trainNumber, date, keys)

            result.onSuccess { json ->
                val parsed = JSONObject(json)
                val status = parsed.optString("CPOS", parsed.optString("LASTUPD", ""))
                val delay = parsed.optString("LDEL", "0").toIntOrNull() ?: 0
                val lastStation = parsed.optString("LSTNN", parsed.optString("LSTN", ""))
                val nextStation = parsed.optString("NSTNN", parsed.optString("NSTN", ""))

                // Only notify if delayed or at a station
                if (delay > 0 || lastStation.isNotEmpty()) {
                    val title = if (delay > 0) {
                        "Train $trainNumber — ${delay}min late"
                    } else {
                        "Train $trainNumber"
                    }
                    val body = buildString {
                        if (lastStation.isNotEmpty()) append("At $lastStation")
                        if (nextStation.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("Next: $nextStation")
                        }
                        if (isEmpty()) append(status)
                    }
                    postNotification(trainNumber, title, body)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.success() // Don't retry — network may be unavailable
        }
    }

    private fun postNotification(trainNumber: String, title: String, body: String) {
        // Check permission before posting (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val notificationId = trainNumber.hashCode()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }
}
