package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Pure Phase C VM helpers: tier mapper, alarm clamp, predicted-arrival epoch. */
class LiveTrackingStateTest {

    @Test
    fun `tier mapper prefers service over row`() {
        assertEquals(LiveTrackingUiState.OFF, liveTrackingUiState(false, false))
        assertEquals(LiveTrackingUiState.BASELINE, liveTrackingUiState(true, false))
        assertEquals(LiveTrackingUiState.LIVE, liveTrackingUiState(false, true))
        assertEquals(LiveTrackingUiState.LIVE, liveTrackingUiState(true, true))
    }

    @Test
    fun `alarm trigger subtracts lead time`() {
        val predicted = 1_000_000_000L
        // 30 min lead, far future → exact trigger.
        assertEquals(predicted - 30 * 60_000L, alarmTriggerEpoch(predicted, 30, 0L))
    }

    @Test
    fun `stale prediction clamps to now plus 1s`() {
        val now = 5_000_000_000L
        assertEquals(now + 1_000L, alarmTriggerEpoch(now - 60_000L, 15, now))
        assertEquals(now + 1_000L, alarmTriggerEpoch(now, 0, now))
    }

    @Test
    fun `predicted arrival is sched plus delay on the IST date`() {
        val now = istEpoch(2026, Calendar.SEPTEMBER, 23, 10, 0)
        val arrival = predictedArrivalEpochMs("12:00", 20, now)
        assertEquals(istEpoch(2026, Calendar.SEPTEMBER, 23, 12, 20), arrival)
    }

    @Test
    fun `overnight arrival rolls to the next day`() {
        val now = istEpoch(2026, Calendar.SEPTEMBER, 23, 23, 0)
        val arrival = predictedArrivalEpochMs("02:00", 0, now)
        assertEquals(istEpoch(2026, Calendar.SEPTEMBER, 24, 2, 0), arrival)
    }

    @Test
    fun `unparseable clock yields null`() {
        val now = istEpoch(2026, Calendar.SEPTEMBER, 23, 10, 0)
        assertNull(predictedArrivalEpochMs(null, 0, now))
        assertNull(predictedArrivalEpochMs("", 0, now))
        assertNull(predictedArrivalEpochMs("not-a-time", 0, now))
    }

    @Test
    fun `arrival label omits clock when trigger unknown`() {
        assertEquals(
            "Alert 30 min before NDLS",
            arrivalScheduledLabel("NDLS", 30, null),
        )
    }

    private fun istEpoch(year: Int, month: Int, day: Int, hour: Int, min: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(year, month, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
