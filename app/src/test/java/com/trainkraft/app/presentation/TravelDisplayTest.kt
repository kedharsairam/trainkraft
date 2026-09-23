package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure Phase D travel-screen core: ETA/speed/distance
 * formatting, the GPS-stale boundary, leg-progress guards and the
 * [mapTravelDisplay] state mapping (live / stale-greyed / arrival /
 * prediction-fallback / bare waiting). Plain JUnit — no Android needed.
 */
class TravelDisplayTest {

    // ------------------------------------------------------- ETA formatting

    @Test
    fun `null ETA reads waiting, never a bogus count`() {
        assertEquals("waiting", formatTravelEta(null))
    }

    @Test
    fun `ETA carries tilde prefix and truncates like the service copy`() {
        assertEquals("~8 min", formatTravelEta(8.9))
        assertEquals("~12 min", formatTravelEta(12.0))
    }

    @Test
    fun `sub-minute ETA clamps to 1 min, never 0`() {
        assertEquals("~1 min", formatTravelEta(0.4))
    }

    // ----------------------------------------------------- speed formatting

    @Test
    fun `null speed reads waiting`() {
        assertEquals("waiting", formatTravelSpeed(null))
    }

    @Test
    fun `speed truncates to whole km per h`() {
        assertEquals("87 km/h", formatTravelSpeed(87.9))
        assertEquals("0 km/h", formatTravelSpeed(0.2))
    }

    // -------------------------------------------------- distance formatting

    @Test
    fun `null distance hides, never guesses`() {
        assertNull(formatRemainingKm(null))
    }

    @Test
    fun `distance carries tilde, metres below one km`() {
        assertEquals("~12 km", formatRemainingKm(12.6))
        assertEquals("~450 m", formatRemainingKm(0.45))
    }

    @Test
    fun `non-finite or negative distance hides`() {
        assertNull(formatRemainingKm(Double.NaN))
        assertNull(formatRemainingKm(-1.0))
    }

    // -------------------------------------------------------- stale boundary

    @Test
    fun `no fix is not stale — it is the separate fallback state`() {
        assertFalse(isGpsStale(null))
    }

    @Test
    fun `stale boundary is 30s exclusive`() {
        assertFalse(isGpsStale(30_000L))
        assertTrue(isGpsStale(30_001L))
    }

    // ------------------------------------------------------ leg progress

    @Test
    fun `leg progress needs a known leg of at least 1 km`() {
        assertEquals(1 to 5, legProgressKm(1.4, 5.8))
        assertNull(legProgressKm(null, 5.0))
        assertNull(legProgressKm(1.0, null))
        assertNull(legProgressKm(0.4, 0.8))
    }

    @Test
    fun `leg progress clamps covered into range`() {
        assertEquals(5 to 5, legProgressKm(9.0, 5.0))
        assertEquals(0 to 5, legProgressKm(-2.0, 5.0))
    }

    // ------------------------------------------------------ state mapping

    private fun live(
        stale: Boolean = false,
        speed: Double? = 87.4,
        eta: Double? = 8.2,
        remaining: Double? = 12.6,
    ) = mapTravelDisplay(
        hasFix = true,
        stale = stale,
        speedKmh = speed,
        nextCode = "NDLS",
        nextName = "New Delhi",
        remainingKm = remaining,
        etaMin = eta,
        legCoveredKm = 3.0,
        legTotalKm = 15.0,
        arrivedCode = null,
        fallbackStopLabel = "NDLS · New Delhi",
        fallbackBasisLabel = "typical pattern",
    )

    @Test
    fun `fresh fix is live with one announcement`() {
        val d = live()
        assertEquals(TravelGpsBadge.LIVE, d.badge)
        assertFalse(d.dimmed)
        assertEquals("87 km/h", d.speedText)
        assertEquals("NDLS · New Delhi", d.nextStopText)
        assertEquals("~12 km · ~8 min", d.detailText)
        assertNull(d.basisLabel)
        assertEquals(3 to 15, d.progress)
        assertNull(d.arrivedCode)
        assertEquals("87 km/h. Next stop NDLS · New Delhi. ~12 km · ~8 min.", d.announcement)
    }

    @Test
    fun `stale fix is searching with greyed last values`() {
        val d = live(stale = true)
        assertEquals(TravelGpsBadge.SEARCHING, d.badge)
        assertTrue(d.dimmed)
        // Last values still shown (greyed) — never live-presented as fresh.
        assertEquals("87 km/h", d.speedText)
        assertEquals("~12 km · ~8 min", d.detailText)
        assertTrue(d.announcement.startsWith("GPS searching. Last known values. "))
    }

    @Test
    fun `withheld ETA reads waiting beside a known distance`() {
        val d = live(speed = null, eta = null)
        assertEquals("waiting", d.speedText)
        assertEquals("~12 km · waiting", d.detailText)
    }

    @Test
    fun `advisory arrival wins over live numbers`() {
        val d = mapTravelDisplay(
            hasFix = true,
            stale = false,
            speedKmh = 12.0,
            nextCode = null,
            nextName = null,
            remainingKm = 0.2,
            etaMin = null,
            legCoveredKm = null,
            legTotalKm = null,
            arrivedCode = "NDLS",
            fallbackStopLabel = null,
            fallbackBasisLabel = null,
        )
        assertEquals("NDLS", d.arrivedCode)
        assertEquals("NDLS", d.nextStopText)
        assertTrue(d.detailText.startsWith("Arrived"))
        assertTrue(d.announcement.contains("Advisory GPS estimate"))
    }

    @Test
    fun `no fix falls back to engine prediction plus basis`() {
        val d = mapTravelDisplay(
            hasFix = false,
            stale = false,
            speedKmh = null,
            nextCode = null,
            nextName = null,
            remainingKm = null,
            etaMin = null,
            legCoveredKm = null,
            legTotalKm = null,
            arrivedCode = null,
            fallbackStopLabel = "NDLS · New Delhi",
            fallbackBasisLabel = "typical pattern",
        )
        assertEquals(TravelGpsBadge.WAITING, d.badge)
        assertFalse(d.dimmed)
        assertEquals("waiting", d.speedText)
        assertEquals("NDLS · New Delhi", d.nextStopText)
        assertEquals("typical pattern", d.basisLabel)
        assertTrue(d.detailText.contains("server data"))
        assertTrue(d.announcement.startsWith("Waiting for GPS."))
    }

    @Test
    fun `no fix without fallback is bare waiting`() {
        val d = mapTravelDisplay(
            hasFix = false,
            stale = false,
            speedKmh = null,
            nextCode = null,
            nextName = null,
            remainingKm = null,
            etaMin = null,
            legCoveredKm = null,
            legTotalKm = null,
            arrivedCode = null,
            fallbackStopLabel = null,
            fallbackBasisLabel = null,
        )
        assertEquals(TravelGpsBadge.WAITING, d.badge)
        assertNull(d.nextStopText)
        assertEquals("Waiting for GPS…", d.detailText)
        assertEquals("Waiting for GPS.", d.announcement)
    }
}
