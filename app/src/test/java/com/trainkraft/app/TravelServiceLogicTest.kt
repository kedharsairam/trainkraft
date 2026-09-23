package com.trainkraft.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the service's pure seam: the ongoing-notification copy builder
 * ([travelSummaryFor]) plus the action/ID/cadence constants the UI peer
 * integrates against.
 *
 * Robolectric runner (not because Android APIs are touched — the builder is
 * String-only — but because the helpers live alongside the Service class and
 * the runner keeps class-loading hermetic; same justification as
 * [TrackingServiceTest]). No device needed for any of this.
 */
@RunWith(RobolectricTestRunner::class)
class TravelServiceLogicTest {

    // ------------------------------------------------------------ constants

    @Test
    fun `travel notification ID is distinct from tracking`() {
        assertEquals(1002, TravelService.FOREGROUND_NOTIFICATION_ID)
        assertTrue(TravelService.FOREGROUND_NOTIFICATION_ID != TrackingService.FOREGROUND_NOTIFICATION_ID)
    }

    @Test
    fun `fused cadence is 10s interval 5s fastest`() {
        assertEquals(10_000L, UPDATE_INTERVAL_MS)
        assertEquals(5_000L, FASTEST_INTERVAL_MS)
    }

    // ------------------------------------------------------------ copy

    @Test
    fun `starting session copy waits for GPS`() {
        val s = travelSummaryFor("12951", null, null, null)
        assertEquals("Travel mode · 12951", s.title)
        assertTrue(s.text.contains("waiting"))
    }

    @Test
    fun `moving copy carries speed next-stop and distance`() {
        val s = travelSummaryFor("12951", 62.0, 24.7, "BRC")
        assertTrue(s.text.contains("62 km/h"))
        assertTrue(s.text.contains("BRC"))
        assertTrue(s.text.contains("~24 km"))
        assertTrue(!s.text.contains("min"))
    }

    @Test
    fun `halt copy never shows a bogus count`() {
        // 5 km/h at a halt: "waiting · next BRC", distance suppressed.
        val s = travelSummaryFor("12951", 5.0, 999.0, "BRC")
        assertTrue("got: ${s.text}", s.text.contains("waiting"))
        assertTrue("got: ${s.text}", !s.text.contains("999"))
    }

    @Test
    fun `null speed keeps distance, sub-km reads metres`() {
        val held = travelSummaryFor("12951", null, 24.0, "BRC")
        assertTrue("got: ${held.text}", held.text.contains("waiting"))
        assertTrue("got: ${held.text}", held.text.contains("~24 km"))
        val meters = travelSummaryFor("12951", null, 0.45, "BRC")
        assertTrue("got: ${meters.text}", meters.text.contains("~450 m"))
        // No route at all: waiting for GPS.
        val noroute = travelSummaryFor("12951", null, null, null)
        assertTrue("got: ${noroute.text}", noroute.text.contains("waiting for GPS"))
    }

    @Test
    fun `speed floor matches math contract`() {
        // 7.9 km/h must read waiting — same floor as MIN_MOVING_SPEED_KMH.
        val s = travelSummaryFor("12951", 7.9, 30.0, "BRC")
        assertTrue("got: ${s.text}", s.text.startsWith("waiting"))
    }
}
