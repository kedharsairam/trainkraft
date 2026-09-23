package com.trainkraft.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision-matrix tests for the Phase C additive policy fns
 * ([NotificationPolicy.evaluateApproach] / [evaluateArrival]).
 *
 * These EXTEND the existing policy without touching it: [NotificationPolicy.decide]
 * keeps its delay-deterioration / cancellation / completion contract
 * ([NotificationPolicyTest]); the additive fns only answer "is the user's
 * WATCHED station near?" (approach needs a TARGET — [TrackedTrainEntity.lastStation]
 * is the last SEEN station, so the target lives in the new nullable
 * `watchStationCode` column, user.db v1→v2) and "are we at the destination?"
 * (delegates to `decide`'s completion branch — no duplicated logic).
 *
 * Pure JVM (no Android): plain JUnit, no Robolectric.
 */
class NotificationPolicyApproachTest {

    private fun snap(
        last: String = "KOTA",
        next: String = "RTM",
        status: String = "Departed from KOTA at 03:20 23-Sep",
        runState: Int = 1,
        arrived: Boolean = false,
    ) = PollSnapshot(
        delayMin = 5,
        lastStation = last,
        nextStation = next,
        statusText = status,
        runState = runState,
        arrivedAtDest = arrived,
    )

    // ------------------------------------------------------------ approach guards

    @Test
    fun `no watched station is always silent`() {
        assertTrue(
            NotificationPolicy.evaluateApproach("12951", null, snap(), 5, null)
                is NotificationPolicy.Decision.Silent
        )
        assertTrue(
            NotificationPolicy.evaluateApproach("12951", "  ", snap(), 5, null)
                is NotificationPolicy.Decision.Silent
        )
    }

    @Test
    fun `already-notified station stays silent`() {
        val d = NotificationPolicy.evaluateApproach("12951", "BRC", snap(next = "BRC"), 5, "BRC")
        assertTrue(d is NotificationPolicy.Decision.Silent)
    }

    // ------------------------------------------------------------ approach triggers

    @Test
    fun `next station matching the watch notifies`() {
        val d = NotificationPolicy.evaluateApproach(
            "12951", "BRC", snap(next = "VADODARA JN (BRC)"), minutesUntilArrival = 40,
            priorApproachFor = null,
        )
        assertTrue(d is NotificationPolicy.Decision.Notify)
        d as NotificationPolicy.Decision.Notify
        assertTrue(d.title.contains("12951"))
        assertTrue(d.title.contains("BRC"))
        assertFalse(d.journeyOver)
    }

    @Test
    fun `last station matching the watch notifies`() {
        val d = NotificationPolicy.evaluateApproach(
            "12951", "kota", snap(last = "KOTA", next = "RTM"), minutesUntilArrival = 90,
            priorApproachFor = null,
        )
        assertTrue(d is NotificationPolicy.Decision.Notify)
    }

    @Test
    fun `near ETA notifies even before the station is next`() {
        val d = NotificationPolicy.evaluateApproach(
            "12951", "BRC", snap(last = "ANND", next = "NVS"), minutesUntilArrival = 12,
            priorApproachFor = null,
        )
        assertTrue(d is NotificationPolicy.Decision.Notify)
    }

    @Test
    fun `far ETA with no station match stays silent`() {
        val d = NotificationPolicy.evaluateApproach(
            "12951", "BRC", snap(last = "ANND", next = "NVS"), minutesUntilArrival = 40,
            priorApproachFor = null,
        )
        assertTrue(d is NotificationPolicy.Decision.Silent)
    }

    @Test
    fun `unknown ETA with no station match stays silent`() {
        val d = NotificationPolicy.evaluateApproach(
            "12951", "BRC", snap(), minutesUntilArrival = null, priorApproachFor = null,
        )
        assertTrue(d is NotificationPolicy.Decision.Silent)
    }

    @Test
    fun `alarm-fired mode notifies for the requested station`() {
        // The AlarmManager trigger IS the time condition (user chose
        // minutesBefore): station/time matching is skipped, one-shot dedup
        // via priorApproachFor still applies.
        val d = NotificationPolicy.evaluateApproach(
            "12951", "BRC", snap(), minutesUntilArrival = null, priorApproachFor = null,
            alarmFired = true,
        )
        assertTrue(d is NotificationPolicy.Decision.Notify)
    }

    @Test
    fun `alarm-fired mode respects one-shot dedup`() {
        val d = NotificationPolicy.evaluateApproach(
            "12951", "BRC", snap(), minutesUntilArrival = null, priorApproachFor = "BRC",
            alarmFired = true,
        )
        assertTrue(d is NotificationPolicy.Decision.Silent)
    }

    // ------------------------------------------------------------ arrival

    @Test
    fun `evaluateArrival notifies journey-over on destination arrival`() {
        val d = NotificationPolicy.evaluateArrival("12951", snap(runState = 2, arrived = true))
        assertTrue(d is NotificationPolicy.Decision.Notify)
        d as NotificationPolicy.Decision.Notify
        assertTrue(d.journeyOver)
        assertEquals("Train 12951 — Journey completed", d.title)
    }

    @Test
    fun `evaluateArrival is silent mid-journey`() {
        assertTrue(
            NotificationPolicy.evaluateArrival("12951", snap()) is NotificationPolicy.Decision.Silent
        )
    }

    @Test
    fun `evaluateArrival reuses the shared terminal-state funnel`() {
        // Cancellation also terminates through decide() — same title family,
        // no duplicated completion logic in the additive fn.
        val d = NotificationPolicy.evaluateArrival("12951", snap(status = "Train cancelled"))
        assertTrue(d is NotificationPolicy.Decision.Notify)
    }
}
