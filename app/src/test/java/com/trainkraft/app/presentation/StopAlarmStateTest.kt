package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase E per-stop alarm state: pure schedule-epoch math + map mapping. */
class StopAlarmStateTest {

    // ---- stopAlarmEpochs ----

    @Test
    fun `future prediction fires lead minutes before arrival`() {
        val now = 1_000_000_000L
        val predicted = now + 60 * 60_000L
        val (schedulePredicted, triggerAt) = stopAlarmEpochs(predicted, 10, now)
        assertEquals(predicted - 10 * 60_000L, triggerAt)
        // Round-trips through the scheduler's own trigger math exactly.
        assertEquals(
            triggerAt,
            com.trainkraft.app.AlarmScheduler.alarmTriggerAtMillis(schedulePredicted, 10),
        )
    }

    @Test
    fun `stale prediction clamps to now plus one second`() {
        val now = 1_000_000_000L
        val (schedulePredicted, triggerAt) = stopAlarmEpochs(now - 60_000L, 10, now)
        assertEquals(now + 1_000L, triggerAt)
        assertEquals(
            triggerAt,
            com.trainkraft.app.AlarmScheduler.alarmTriggerAtMillis(schedulePredicted, 10),
        )
    }

    @Test
    fun `reuses the approach lead constant`() {
        assertEquals(10, APPROACH_LEAD_MIN)
    }

    // ---- updateStopAlarmState ----

    @Test
    fun `arming normalizes the key to uppercase trimmed`() {
        val next = updateStopAlarmState(emptyMap(), " brc ", 123L)
        assertEquals(mapOf("BRC" to 123L), next)
    }

    @Test
    fun `re-arming replaces the trigger`() {
        val armed = updateStopAlarmState(emptyMap(), "BRC", 100L)
        assertEquals(mapOf("BRC" to 200L), updateStopAlarmState(armed, "brc", 200L))
    }

    @Test
    fun `disarming removes only that station`() {
        val armed = mapOf("BRC" to 100L, "NDLS" to 200L)
        assertEquals(mapOf("NDLS" to 200L), updateStopAlarmState(armed, "brc", null))
    }

    @Test
    fun `disarming an unarmed station is a no-op`() {
        val armed = mapOf("BRC" to 100L)
        assertTrue(updateStopAlarmState(armed, "NDLS", null) == armed)
    }

    @Test
    fun `blank codes never touch the map`() {
        val armed = mapOf("BRC" to 100L)
        assertTrue(updateStopAlarmState(armed, "   ", 999L) == armed)
        assertTrue(updateStopAlarmState(armed, "", null) == armed)
    }

    @Test
    fun `display prefers the timed trigger`() {
        val d = resolveStopAlarmDisplay(123L, "BRC", "BRC")
        assertTrue(d.armed)
        assertEquals(123L, d.triggerAt)
    }

    @Test
    fun `display renders timeless armed from persisted watch`() {
        val d = resolveStopAlarmDisplay(null, "brc", "BRC")
        assertTrue(d.armed)
        assertEquals(null, d.triggerAt)
    }

    @Test
    fun `display ignores blank or mismatched watch`() {
        assertTrue(!resolveStopAlarmDisplay(null, "", "BRC").armed)
        assertTrue(!resolveStopAlarmDisplay(null, null, "BRC").armed)
        assertTrue(!resolveStopAlarmDisplay(null, "NDLS", "BRC").armed)
        assertTrue(!resolveStopAlarmDisplay(null, "BRC", "").armed)
    }

    @Test
    fun `display unarms a station whose one-shot already fired`() {
        val d = resolveStopAlarmDisplay(null, "BRC", "BRC", notifiedStation = "BRC")
        assertTrue(!d.armed)
    }

    @Test
    fun `display keeps other stations armed after a fire elsewhere`() {
        val d = resolveStopAlarmDisplay(null, "KOTA", "KOTA", notifiedStation = "BRC")
        assertTrue(d.armed)
    }
}
