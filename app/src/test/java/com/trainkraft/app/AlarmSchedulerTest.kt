package com.trainkraft.app

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Covers [AlarmScheduler]: pure trigger-time math + request codes with plain
 * asserts, and the schedule/cancel wiring under Robolectric's
 * ShadowAlarmManager (one RTC_WAKEUP entry registered, exact entry removed
 * on cancel; PendingIntent targets [AlarmReceiver] with train+station
 * extras).
 *
 * COVERAGE GAP (documented, device-session item): Robolectric records the
 * trigger instant but NOT whether `setExactAndAllowWhileIdle` vs
 * `setAndAllowWhileIdle` was used, so the exact-vs-fallback dispatch itself
 * is verified only via the grant probe below plus code review. What IS
 * pinned: [com.trainkraft.app.presentation.PermissionFlow.canScheduleExactAlarms]
 * flips with the shadow grant state (both branches exercised), and both
 * states register a usable alarm. On-device verification (exact fire timing
 * under Doze, ColorOS behavior) needs the device session — phone off USB.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun alarmManager(): AlarmManager =
        context.getSystemService(AlarmManager::class.java)!!

    // ------------------------------------------------------------ pure math

    @Test
    fun `trigger subtracts minutesBefore from predicted arrival`() {
        assertEquals(1_000_000L, AlarmScheduler.alarmTriggerAtMillis(1_600_000L, 10))
    }

    @Test
    fun `zero minutesBefore fires at arrival`() {
        assertEquals(1_600_000L, AlarmScheduler.alarmTriggerAtMillis(1_600_000L, 0))
    }

    @Test
    fun `negative minutesBefore treated as at-arrival`() {
        assertEquals(1_600_000L, AlarmScheduler.alarmTriggerAtMillis(1_600_000L, -5))
    }

    @Test
    fun `trigger never goes negative`() {
        assertEquals(0L, AlarmScheduler.alarmTriggerAtMillis(100_000L, 10))
    }

    @Test
    fun `request code is stable and case-insensitive on station`() {
        val a = AlarmScheduler.alarmRequestCode("12951", "BRC")
        assertEquals(a, AlarmScheduler.alarmRequestCode("12951", "brc"))
        assertEquals(a, AlarmScheduler.alarmRequestCode(" 12951 ", "BRC"))
    }

    @Test
    fun `alarm intent carries action train and uppercased station`() {
        val intent = AlarmScheduler.alarmIntent(context, "12951", "brc")
        assertEquals(AlarmScheduler.ACTION_STATION_ALARM, intent.action)
        assertEquals("12951", intent.getStringExtra(AlarmScheduler.EXTRA_TRAIN_NUMBER))
        assertEquals("BRC", intent.getStringExtra(AlarmScheduler.EXTRA_STATION_CODE))
        assertEquals(AlarmReceiver::class.java.name, intent.component?.className)
    }

    // ------------------------------------------------------------ scheduler wiring

    @Test
    fun `exact-grant state is observable through PermissionFlow`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(
            com.trainkraft.app.presentation.PermissionFlow.canScheduleExactAlarms(context)
        )
        // API 31+ only: pre-S grants exact alarms at install, so the grant
        // state is unobservable below S (always true) by design.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ShadowAlarmManager.setCanScheduleExactAlarms(false)
            assertEquals(
                false,
                com.trainkraft.app.presentation.PermissionFlow.canScheduleExactAlarms(context),
            )
            // Leave granted for other tests.
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
    }

    @Test
    fun `schedule registers one RTC_WAKEUP alarm at the trigger instant`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val trigger = AlarmScheduler.scheduleStationAlarm(context, "12951", "BRC", 10, 1_600_000L)
        assertEquals(1_000_000L, trigger)

        val scheduled = shadowOf(alarmManager()).scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled[0].type)
        assertEquals(1_000_000L, scheduled[0].triggerAtTime)

        AlarmScheduler.cancelAlarm(context, "12951", "BRC")
        assertTrue(shadowOf(alarmManager()).scheduledAlarms.isEmpty())
    }

    @Test
    fun `fallback grant state still registers a usable alarm`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        try {
            val trigger = AlarmScheduler.scheduleStationAlarm(context, "12952", "KOTA", 15, 2_000_000L)
            assertEquals(1_100_000L, trigger)
            val scheduled = shadowOf(alarmManager()).scheduledAlarms
            assertEquals(1, scheduled.size)
            assertEquals(AlarmManager.RTC_WAKEUP, scheduled[0].type)
        } finally {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            AlarmScheduler.cancelAlarm(context, "12952", "KOTA")
        }
    }

    @Test
    fun `cancel is scoped to one train-station pair`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        AlarmScheduler.scheduleStationAlarm(context, "12951", "BRC", 10, 1_600_000L)
        AlarmScheduler.scheduleStationAlarm(context, "12951", "KOTA", 10, 1_600_000L)
        assertEquals(2, shadowOf(alarmManager()).scheduledAlarms.size)

        AlarmScheduler.cancelAlarm(context, "12951", "BRC")
        assertEquals(1, shadowOf(alarmManager()).scheduledAlarms.size)

        AlarmScheduler.cancelAlarm(context, "12951", "KOTA")
        assertTrue(shadowOf(alarmManager()).scheduledAlarms.isEmpty())
    }
}
