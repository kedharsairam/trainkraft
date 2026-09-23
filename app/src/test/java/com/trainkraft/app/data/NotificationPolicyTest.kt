package com.trainkraft.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision-matrix tests for the background notification policy — the P0 fix
 * for "worker notified on every 10-minute poll".
 */
class NotificationPolicyTest {

    private fun snap(
        delay: Int,
        station: String = "KOTA",
        next: String = "RTM",
        status: String = "Departed from KOTA at 03:20 23-Sep",
        runState: Int = 1,
        arrivedAtDest: Boolean = false,
    ) = PollSnapshot(
        delayMin = delay,
        lastStation = station,
        nextStation = next,
        statusText = status,
        runState = runState,
        arrivedAtDest = arrivedAtDest,
    )

    private fun prior(
        delay: Int?,
        category: NotificationPolicy.DelayCategory?,
        pollAt: Long? = 1L,
        station: String = "BVI",
    ) = TrackedTrainEntity(
        trainNumber = "12952",
        trackedAt = 0L,
        lastDelayMin = delay,
        lastStation = station,
        lastCategory = category?.name,
        lastPollAt = pollAt,
    )

    // ------------------------------------------------------------- baselines

    @Test
    fun `first poll is always a silent baseline`() {
        // No prior row at all.
        assertTrue(
            NotificationPolicy.decide("12952", snap(delay = 40), null)
                is NotificationPolicy.Decision.Silent,
        )
        // Prior row exists but was never polled.
        assertTrue(
            NotificationPolicy.decide("12952", snap(delay = 40), prior(40, null, pollAt = null))
                is NotificationPolicy.Decision.Silent,
        )
    }

    @Test
    fun `station movement alone never notifies`() {
        // The original bug: lastStation is always non-empty while running.
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 0, station = "RTM", next = "NAD"),
            prior(delay = 0, category = NotificationPolicy.DelayCategory.ON_TIME, station = "BVI"),
        )
        assertTrue(decision is NotificationPolicy.Decision.Silent)
    }

    // ------------------------------------------------------- delay categories

    @Test
    fun `on-time to moderate deterioration notifies`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 10),
            prior(delay = 0, category = NotificationPolicy.DelayCategory.ON_TIME),
        )
        assertTrue(decision is NotificationPolicy.Decision.Notify)
        decision as NotificationPolicy.Decision.Notify
        assertEquals("Train 12952 — now 10 min late", decision.title)
        assertFalse(decision.journeyOver)
        assertTrue(decision.body.contains("Was 0 min late"))
    }

    @Test
    fun `moderate to severe deterioration notifies`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 20),
            prior(delay = 10, category = NotificationPolicy.DelayCategory.MODERATE),
        )
        assertTrue(decision is NotificationPolicy.Decision.Notify)
    }

    @Test
    fun `big shift inside severe notifies`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 40),
            prior(delay = 20, category = NotificationPolicy.DelayCategory.SEVERE),
        )
        assertTrue(decision is NotificationPolicy.Decision.Notify)
    }

    @Test
    fun `small shift inside severe stays silent`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 25),
            prior(delay = 20, category = NotificationPolicy.DelayCategory.SEVERE),
        )
        assertTrue(decision is NotificationPolicy.Decision.Silent)
    }

    @Test
    fun `same category drift stays silent`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 12),
            prior(delay = 10, category = NotificationPolicy.DelayCategory.MODERATE),
        )
        assertTrue(decision is NotificationPolicy.Decision.Silent)
    }

    @Test
    fun `recovery to on-time stays silent`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 0),
            prior(delay = 40, category = NotificationPolicy.DelayCategory.SEVERE),
        )
        assertTrue(decision is NotificationPolicy.Decision.Silent)
    }

    @Test
    fun `delay category buckets match the ui`() {
        assertEquals(NotificationPolicy.DelayCategory.ON_TIME, NotificationPolicy.delayCategory(5))
        assertEquals(NotificationPolicy.DelayCategory.MODERATE, NotificationPolicy.delayCategory(6))
        assertEquals(NotificationPolicy.DelayCategory.MODERATE, NotificationPolicy.delayCategory(15))
        assertEquals(NotificationPolicy.DelayCategory.SEVERE, NotificationPolicy.delayCategory(16))
    }

    // ------------------------------------------------------ terminal states

    @Test
    fun `cancellation notifies even on first poll`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 0, status = "Train Cancelled", runState = -1),
            null,
        )
        assertTrue(decision is NotificationPolicy.Decision.Notify)
        decision as NotificationPolicy.Decision.Notify
        assertTrue(decision.title.contains("Cancelled"))
        assertFalse(decision.journeyOver) // cancel is per-day; keep tracking
    }

    @Test
    fun `arrival at destination notifies and stops tracking`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 3, status = "Arrived at MUMBAI CENTRAL(MMCT)", arrivedAtDest = true),
            prior(delay = 3, category = NotificationPolicy.DelayCategory.ON_TIME),
        )
        assertTrue(decision is NotificationPolicy.Decision.Notify)
        assertTrue((decision as NotificationPolicy.Decision.Notify).journeyOver)
    }

    @Test
    fun `run state 2 counts as completed`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 0, status = "Completed", runState = 2),
            prior(delay = 0, category = NotificationPolicy.DelayCategory.ON_TIME),
        )
        assertTrue(decision is NotificationPolicy.Decision.Notify)
        assertTrue((decision as NotificationPolicy.Decision.Notify).journeyOver)
    }

    @Test
    fun `plain completed text counts as completed`() {
        val decision = NotificationPolicy.decide(
            "12952",
            snap(delay = 0, status = "Journey completed", runState = -1),
            prior(delay = 0, category = NotificationPolicy.DelayCategory.ON_TIME),
        )
        assertTrue((decision as NotificationPolicy.Decision.Notify).journeyOver)
    }
}
