package com.trainkraft.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of [TravelMath] — no Android, no Robolectric (the
 * under-test file imports kotlin.math only, so plain JUnit suffices).
 */
class TravelMathTest {

    // ------------------------------------------------------------ haversine

    @Test
    fun `haversine of identical points is zero`() {
        assertEquals(0.0, haversineKm(18.9, 72.8, 18.9, 72.8), 1e-9)
    }

    @Test
    fun `haversine MMCT to BRC is sane rail scale`() {
        // Mumbai Central (18.97, 72.82) → Vadodara (22.31, 73.18): ~375 km.
        val km = haversineKm(18.97, 72.82, 22.31, 73.18)
        assertTrue("expected ~375 km, got $km", km in 350.0..400.0)
    }

    @Test
    fun `haversine 800m arrival radius converts correctly`() {
        // ~0.0072° latitude ≈ 800 m.
        val m = haversineKm(18.97, 72.82, 18.9772, 72.82) * 1000.0
        assertTrue("expected ~800 m, got $m", m in 750.0..850.0)
    }

    // ------------------------------------------------------------ smoothing

    @Test
    fun `median of empty is null`() {
        assertNull(median(emptyList()))
    }

    @Test
    fun `median resists a single spike`() {
        // Sorted: 59, 60, 60.5, 61, 200 → median 60.5, spike ignored.
        assertEquals(60.5, median(listOf(60.0, 61.0, 59.0, 200.0, 60.5))!!, 1e-9)
    }

    @Test
    fun `smoothed speed rejects fixes worse than 50m`() {
        val fixes = listOf(
            FixSample(60.0, 10f),
            FixSample(500.0, 200f), // junk — rejected
            FixSample(62.0, 12f),
        )
        assertEquals(61.0, smoothedSpeedKmh(fixes)!!, 1e-9)
    }

    @Test
    fun `smoothed speed uses only the last five qualifying fixes`() {
        val fixes = (1..7).map { FixSample(it * 10.0, 10f) } // 10..70
        // Last five: 30,40,50,60,70 → median 50.
        assertEquals(50.0, smoothedSpeedKmh(fixes)!!, 1e-9)
    }

    @Test
    fun `smoothed speed is null when nothing qualifies`() {
        assertNull(smoothedSpeedKmh(emptyList()))
        assertNull(smoothedSpeedKmh(listOf(FixSample(60.0, 51f))))
    }

    @Test
    fun `accuracy exactly 50m still qualifies`() {
        assertEquals(60.0, smoothedSpeedKmh(listOf(FixSample(60.0, 50f)))!!, 1e-9)
    }

    // ------------------------------------------------------------ ETA

    @Test
    fun `ETA divides distance by speed`() {
        // 30 km at 60 km/h → 30 min.
        assertEquals(30.0, liveEtaMin(30.0, 60.0)!!, 1e-9)
    }

    @Test
    fun `ETA below 8 kmh is null — stationary reads waiting`() {
        assertNull(liveEtaMin(30.0, 7.9))
        assertNull(liveEtaMin(30.0, 0.0))
    }

    @Test
    fun `ETA at exactly 8 kmh is valid`() {
        // 8 km in 8 km/h → 60 min.
        assertEquals(60.0, liveEtaMin(8.0, 8.0)!!, 1e-9)
    }

    @Test
    fun `ETA rejects non-positive and non-finite input`() {
        assertNull(liveEtaMin(0.0, 60.0))
        assertNull(liveEtaMin(-5.0, 60.0))
        assertNull(liveEtaMin(Double.NaN, 60.0))
        assertNull(liveEtaMin(30.0, Double.POSITIVE_INFINITY))
    }

    // ------------------------------------------------------------ arrival

    @Test
    fun `arrival fires inside 800m`() {
        assertTrue(arrivedWithinM(18.97, 72.82, 18.972, 72.822))
    }

    @Test
    fun `arrival does not fire kilometres out`() {
        assertFalse(arrivedWithinM(18.97, 72.82, 19.10, 72.90))
    }

    @Test
    fun `arrival honors a custom radius`() {
        // ~400 m away: inside default, outside a 100 m radius.
        assertTrue(arrivedWithinM(18.97, 72.82, 18.9736, 72.82))
        assertFalse(arrivedWithinM(18.97, 72.82, 18.9736, 72.82, radiusM = 100.0))
    }

    // ------------------------------------------------------------ next stop

    @Test
    fun `next stop follows route order`() {
        val route = listOf("MMCT", "BRC", "KOTA", "NDLS")
        assertEquals("BRC", nextStopAfter("MMCT", route))
        assertEquals("NDLS", nextStopAfter("KOTA", route))
    }

    @Test
    fun `next stop is null at the destination or for unknown codes`() {
        val route = listOf("MMCT", "BRC", "NDLS")
        assertNull(nextStopAfter("NDLS", route))
        assertNull(nextStopAfter("BZA", route))
        assertNull(nextStopAfter("", route))
        assertNull(nextStopAfter("MMCT", emptyList()))
    }
}
