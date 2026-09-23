package com.trainkraft.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.trainkraft.app.presentation.PermissionFlow

/**
 * Station/destination alarm scheduling (Phase C).
 *
 * EXACT vs FALLBACK: [scheduleStationAlarm] uses
 * `AlarmManager.setExactAndAllowWhileIdle` only when
 * [PermissionFlow.canScheduleExactAlarms] is true (pre-31 always true —
 * install grant; 31+ reflects the Settings grant). Otherwise it schedules
 * the same trigger via `setAndAllowWhileIdle` (inexact, OS-batched —
 * minutes late under Doze is normal). Both are `RTC_WAKEUP` so they fire
 * with the screen off. The trigger instant + request-code math
 * ([alarmTriggerAtMillis], [alarmRequestCode]) are pure and unit-tested;
 * the AlarmManager call itself is covered under Robolectric via
 * ShadowAlarmManager (exact/fallback branch per grant state).
 *
 * DOZE HONESTY: exact alarms are one of the two Doze-exempt paths this
 * phase uses (the other is the foreground service). When the user denies
 * the exact-alarm grant, the inexact fallback + the 15-min worker baseline
 * are the coverage — an alarm may arrive late, and that is by design,
 * surfaced in onboarding copy (peer's) rather than hidden.
 *
 * Request codes: [alarmRequestCode] (stable hash of train|station) so
 * [cancelAlarm] rebuilds the identical PendingIntent and cancels exactly
 * one alarm; FLAG_UPDATE_CURRENT keeps re-scheduling idempotent.
 */
object AlarmScheduler {

    /** Broadcast action fired by AlarmManager; handled by [AlarmReceiver]. */
    const val ACTION_STATION_ALARM = "com.trainkraft.app.action.STATION_ALARM"

    const val EXTRA_TRAIN_NUMBER = "extra_train_number"
    const val EXTRA_STATION_CODE = "extra_station_code"

    /**
     * Pure trigger math: fire [minutesBefore] minutes ahead of the predicted
     * arrival instant. Coerced to ≥ 0 (never a negative wall-clock); a
     * trigger already in the past fires immediately on schedule — the caller
     * (and the receiver's untracked/completed guards) owns that policy, not
     * this function. [minutesBefore] ≤ 0 means "at arrival".
     */
    fun alarmTriggerAtMillis(predictedArrivalEpochMs: Long, minutesBefore: Int): Long =
        (predictedArrivalEpochMs - minutesBefore.coerceAtLeast(0) * 60_000L).coerceAtLeast(0L)

    /**
     * Stable per (train, station) request code. Uppercase-normalized so
     * "brc" and "BRC" cancel each other; hash collision across distinct
     * pairs is accepted (documented: same 32-bit space as the worker's
     * `trainNumber.hashCode()` alert IDs).
     */
    fun alarmRequestCode(trainNumber: String, stationCode: String): Int =
        "${trainNumber.trim()}|${stationCode.trim().uppercase()}".hashCode()

    fun alarmIntent(context: Context, trainNumber: String, stationCode: String): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_STATION_ALARM)
            .putExtra(EXTRA_TRAIN_NUMBER, trainNumber.trim())
            .putExtra(EXTRA_STATION_CODE, stationCode.trim().uppercase())

    fun alarmPendingIntent(context: Context, trainNumber: String, stationCode: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmRequestCode(trainNumber, stationCode),
            alarmIntent(context, trainNumber, stationCode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Schedules the station alarm; returns the wall-clock trigger previously
     * computed by [alarmTriggerAtMillis]. Never throws: a SecurityException
     * (grant revoked between check and call) degrades to the inexact call,
     * and even that failure is swallowed — the worker baseline still covers
     * the journey, and scheduling must never crash the UI thread caller.
     */
    fun scheduleStationAlarm(
        context: Context,
        trainNumber: String,
        stationCode: String,
        minutesBefore: Int,
        predictedArrivalEpochMs: Long,
    ): Long {
        val triggerAt = alarmTriggerAtMillis(predictedArrivalEpochMs, minutesBefore)
        val operation = alarmPendingIntent(context, trainNumber, stationCode)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return triggerAt
        try {
            if (PermissionFlow.canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    operation,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    operation,
                )
            }
        } catch (_: SecurityException) {
            // Grant flipped mid-call: best-effort inexact fallback.
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (_: Exception) {
            // OEM AlarmManager quirks: scheduling is best-effort, never fatal.
            // The 15-min worker baseline still covers the journey.
        }
        return triggerAt
    }

    /**
     * Cancels one (train, station) alarm. Rebuilds the identical
     * PendingIntent (same request code + FLAG_UPDATE_CURRENT) and cancels
     * both the AlarmManager entry and the PendingIntent itself. No-op when
     * nothing was scheduled. Never throws.
     */
    fun cancelAlarm(context: Context, trainNumber: String, stationCode: String) {
        val operation = alarmPendingIntent(context, trainNumber, stationCode)
        runCatching {
            context.getSystemService(AlarmManager::class.java)?.cancel(operation)
        }
        operation.cancel()
    }
}
