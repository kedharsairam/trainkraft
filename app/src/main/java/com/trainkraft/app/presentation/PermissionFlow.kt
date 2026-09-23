package com.trainkraft.app.presentation

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Read-only permission *status* helpers that Phases C/D build on.
 *
 * This object only answers "is it granted right now?" — it owns no UI, no
 * Compose, and no ActivityResult launchers. The future tracking-onboarding
 * screen (Phase C) and travel-mode entry point (Phase D) own the request
 * flows; they consume [missingForTracking] as their single ordered checklist
 * and launch the system dialogs / settings intents themselves.
 */
object PermissionFlow {

    /**
     * Tracking prerequisites in the order the onboarding screen will request
     * them. Order is deliberate — each unlocks the next layer's value:
     * notifications first (the passive alert channel every tracked train
     * needs), then location (foreground GPS powers Phase D travel mode), then
     * exact alarms (Phase C station/destination alarms, the most
     * settings-heavy grant, last so earlier wins aren't blocked behind it).
     */
    enum class TrackingPermission {
        NOTIFICATIONS,
        LOCATION,
        EXACT_ALARM,
    }

    /**
     * Whether post-tracking notifications can be shown. Phase C wiring: gate
     * worker-driven alerts on this; when false the onboarding screen routes
     * to the system notification-settings page (not a runtime dialog —
     * POST_NOTIFICATIONS has no in-app grant path once denied).
     */
    fun hasNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Whether precise foreground location is granted. Phase D wiring: travel
     * mode's GPS entry point checks this before starting updates. No API
     * guard needed: checkSelfPermission exists since API 23 and minSdk is 26;
     * background location is intentionally out of scope (foreground-only
     * travel mode needs no background grant).
     */
    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether exact alarms may be scheduled. Pre-31 (S) SCHEDULE_EXACT_ALARM
     * is granted at install so this returns true; on API 31+ it reflects the
     * runtime Settings grant. Phase C wiring: station/destination alarms call
     * this before AlarmManager.setExact* and fall back to inexact alarms
     * when false.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager?.canScheduleExactAlarms() == true
    }

    /**
     * Ordered subset of [TrackingPermission] not yet granted — the single
     * checklist the future tracking-onboarding screen consumes top to bottom.
     * Empty means tracking is fully unlocked.
     */
    fun missingForTracking(context: Context): List<TrackingPermission> =
        orderMissing(
            notificationsMissing = !hasNotifications(context),
            locationMissing = !hasFineLocation(context),
            exactAlarmMissing = !canScheduleExactAlarms(context),
        )

    /**
     * Pure core of [missingForTracking]: maps three "is it missing?" flags to
     * the ordered checklist. Kept Context-free so it is directly unit-testable
     * with plain JUnit (no Robolectric / Context mocking required); the
     * Context overload above only supplies the flags.
     */
    fun orderMissing(
        notificationsMissing: Boolean,
        locationMissing: Boolean,
        exactAlarmMissing: Boolean,
    ): List<TrackingPermission> = buildList {
        if (notificationsMissing) add(TrackingPermission.NOTIFICATIONS)
        if (locationMissing) add(TrackingPermission.LOCATION)
        if (exactAlarmMissing) add(TrackingPermission.EXACT_ALARM)
    }

    // ------------------------------------------------- Phase C live tracking
    // Additive only: nothing above is touched. Location is EXCLUDED here —
    // Phase C live tracking has no GPS (minute NTES polling + AlarmManager
    // only); location belongs to Phase D travel mode ([TrackingPermission]).

    /**
     * Live-tracking requirements in request order: notifications (the alert
     * channel), exact alarms (punctual station alarms), battery exemption
     * (surviving OEM task killers). No location — see above.
     */
    enum class LiveTrackingRequirement {
        NOTIFICATIONS,
        EXACT_ALARM,
        BATTERY_EXEMPTION,
    }

    /**
     * Plain-language rationale per live-tracking requirement (literal UI
     * copy — the onboarding sheet renders these verbatim).
     */
    val liveTrackingRationales: Map<LiveTrackingRequirement, String> = mapOf(
        LiveTrackingRequirement.NOTIFICATIONS to
            "Notifications let TrainKraft alert you about delays and arrivals.",
        LiveTrackingRequirement.EXACT_ALARM to
            "Exact alarms fire station alerts on time, even in Doze.",
        LiveTrackingRequirement.BATTERY_EXEMPTION to
            "Ignoring battery optimizations keeps minute-level tracking alive on your device.",
    )

    /**
     * Ordered subset of [LiveTrackingRequirement] not yet granted — the
     * checklist the live-tracking onboarding sheet consumes top to bottom.
     * Pure (Context-free) for plain-JUnit testing; callers supply flags via
     * [hasNotifications], [canScheduleExactAlarms] and
     * BatteryExemption.isExempt.
     */
    fun missingLiveTrackingRequirements(
        notificationsMissing: Boolean,
        exactAlarmMissing: Boolean,
        batteryExemptionMissing: Boolean,
    ): List<LiveTrackingRequirement> = buildList {
        if (notificationsMissing) add(LiveTrackingRequirement.NOTIFICATIONS)
        if (exactAlarmMissing) add(LiveTrackingRequirement.EXACT_ALARM)
        if (batteryExemptionMissing) add(LiveTrackingRequirement.BATTERY_EXEMPTION)
    }
}
