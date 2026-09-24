package com.trainkraft.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the travel-notification speed line in [travelSummaryFor]: a fresh,
 * moving GPS fix leads with its speed even when no route/next-stop is known
 * yet (previously that state read "waiting for GPS", hiding a live number).
 *
 * Waiting / halt / stale semantics are pinned unchanged here (mirroring
 * [TravelServiceLogicTest], which is not modified): halted speeds still read
 * "waiting" with no distance, and a stale (null-speed) fix with no route
 * still reads "waiting for GPS".
 *
 * Robolectric runner for the same class-loading reason as
 * [TravelServiceLogicTest] (helpers live alongside the Service class).
 */
@RunWith(RobolectricTestRunner::class)
class TravelSpeedLineTest {

    @Test
    fun `moving without route leads with GPS speed`() {
        val s = travelSummaryFor("12951", 87.0, null, null)
        assertEquals("87 km/h · tap for details", s.text)
        assertTrue("got: ${s.text}", !s.text.contains("waiting for GPS"))
    }

    @Test
    fun `moving with distance but no next stop still leads with speed`() {
        val s = travelSummaryFor("12951", 87.0, 12.4, null)
        assertEquals("87 km/h · tap for details", s.text)
    }

    @Test
    fun `full moving copy with next stop and distance is unchanged`() {
        val s = travelSummaryFor("12951", 87.0, 12.4, "KMT")
        assertEquals("87 km/h · ~12 km to KMT · tap for details", s.text)
    }

    @Test
    fun `stale fix without route still waits for GPS`() {
        val s = travelSummaryFor("12951", null, null, null)
        assertTrue("got: ${s.text}", s.text.contains("waiting for GPS"))
    }

    @Test
    fun `halt without route reads waiting`() {
        val s = travelSummaryFor("12951", 3.0, null, null)
        assertTrue("got: ${s.text}", s.text.startsWith("waiting"))
        assertTrue("got: ${s.text}", !s.text.contains("km/h"))
    }

    @Test
    fun `halt with next stop still suppresses distance`() {
        val s = travelSummaryFor("12951", 5.0, 999.0, "BRC")
        assertTrue("got: ${s.text}", s.text.contains("waiting"))
        assertTrue("got: ${s.text}", !s.text.contains("999"))
    }
}
