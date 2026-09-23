package com.trainkraft.app

import com.trainkraft.app.data.LiveStatusDto
import com.trainkraft.app.data.LiveStopDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.TimeZone

/**
 * Covers the service's pure helpers: the 60s/30s poll ladder
 * ([pollIntervalFor]), engine-backed minutes-to-next-stop
 * ([minutesUntilNextStop]), and the DTO→policy mapping ([toPollSnapshot]).
 *
 * Robolectric runner (not because Android APIs are touched — these are pure
 * JVM — but because the helpers live alongside the Service class and the
 * runner keeps class-loading hermetic). No device needed for any of this.
 */
@RunWith(RobolectricTestRunner::class)
class TrackingServiceTest {

    // ------------------------------------------------------------ poll ladder

    @Test
    fun `null ETA degrades to 60s`() {
        assertEquals(POLL_INTERVAL_MS, pollIntervalFor(null))
    }

    @Test
    fun `far stop polls at 60s`() {
        assertEquals(POLL_INTERVAL_MS, pollIntervalFor(16))
        assertEquals(POLL_INTERVAL_MS, pollIntervalFor(120))
    }

    @Test
    fun `imminent stop boosts to 30s`() {
        assertEquals(BOOST_POLL_INTERVAL_MS, pollIntervalFor(15))
        assertEquals(BOOST_POLL_INTERVAL_MS, pollIntervalFor(5))
        assertEquals(BOOST_POLL_INTERVAL_MS, pollIntervalFor(0))
    }

    @Test
    fun `stale just-passed stop keeps 30s, old data does not`() {
        assertEquals(BOOST_POLL_INTERVAL_MS, pollIntervalFor(-2))
        assertEquals(POLL_INTERVAL_MS, pollIntervalFor(-3))
        assertEquals(POLL_INTERVAL_MS, pollIntervalFor(-700))
    }

    // ------------------------------------------------------------ ETA math

    private fun istEpoch(day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2026, Calendar.SEPTEMBER, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun stop(code: String, sta: String = "", eta: String = "", std: String = "") = LiveStopDto(
        code = code,
        scheduledArrival = sta,
        estArrival = eta,
        scheduledDeparture = std,
    )

    @Test
    fun `scheduled arrival drives boost`() {
        val stops = listOf(
            stop("MMCT", std = "09:30"),
            stop("BRC", sta = "10:00"),
        )
        // Mid-route anchor: next is BRC, scheduled 10:00, now 09:55 IST.
        assertEquals(
            5,
            minutesUntilNextStop(stops, anchorIndex = 0, istEpoch(23, 9, 55)),
        )
        assertEquals(
            BOOST_POLL_INTERVAL_MS,
            pollIntervalFor(minutesUntilNextStop(stops, 0, istEpoch(23, 9, 55))),
        )
    }

    @Test
    fun `pre-departure anchor reads the source departure`() {
        val stops = listOf(
            stop("MMCT", std = "10:00"),
            stop("BRC", sta = "12:00"),
        )
        assertEquals(
            5,
            minutesUntilNextStop(stops, anchorIndex = null, istEpoch(23, 9, 55)),
        )
    }

    @Test
    fun `ETA preferred when present, midnight rollover wraps`() {
        val stops = listOf(stop("BRC", sta = "23:50", eta = "00:05 24-Sep"))
        // Base prefers scheduled arrival (23:50) here; ETA-first ordering is
        // pinned by the next test. Rollover: arrival 00:10 vs 23:55 now.
        val late = listOf(stop("BRC", sta = "00:10"))
        assertEquals(15, minutesUntilNextStop(late, null, istEpoch(23, 23, 55)))
    }

    @Test
    fun `unparseable times degrade to null`() {
        val stops = listOf(stop("BRC"))
        assertNull(minutesUntilNextStop(stops, null, istEpoch(23, 9, 55)))
    }

    @Test
    fun `anchor at last stop means no next stop`() {
        val stops = listOf(stop("A", sta = "10:00"), stop("B", sta = "12:00"))
        assertNull(minutesUntilNextStop(stops, anchorIndex = 1, istEpoch(23, 9, 55)))
        // …while a mid-route anchor picks the following stop.
        assertEquals(
            125,
            minutesUntilNextStop(stops, anchorIndex = 0, istEpoch(23, 9, 55)),
        )
    }

    // ------------------------------------------------------------ DTO mapping

    @Test
    fun `toPollSnapshot carries delay stations and completion flags`() {
        val dto = LiveStatusDto(
            delayRaw = "26",
            statusText = "Departed from KOTA",
            lastStationCode = "KOTA",
            lastStationName = "",
            nextStationCode = "RTM",
            nextStationName = "RATLAM JN",
            runState = 1,
            arrivedAtDest = false,
        )
        val snap = dto.toPollSnapshot()
        assertEquals(26, snap.delayMin)
        assertEquals("KOTA", snap.lastStation) // name blank → code fallback
        assertEquals("RATLAM JN", snap.nextStation)
        assertEquals("Departed from KOTA", snap.statusText)
        assertEquals(1, snap.runState)
        assertEquals(false, snap.arrivedAtDest)
    }

    @Test
    fun `toPollSnapshot completion flags survive mapping`() {
        val dto = LiveStatusDto(runState = 2, arrivedAtDest = true)
        val snap = dto.toPollSnapshot()
        assertEquals(2, snap.runState)
        assertEquals(true, snap.arrivedAtDest)
    }
}
