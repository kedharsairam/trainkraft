package com.trainkraft.app.presentation

import com.trainkraft.app.data.FogOverlayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Published fog-program notices are facts, not forecasts: the overlay says
 * what the railway published (cancelled / revised / reduced + dates), never
 * what will happen to this run.
 */
class FogNoticeTest {

    private fun entry(action: String) = FogOverlayEntity(
        trainNumber = "12523",
        action = action,
        fromDate = "2025-12-01",
        toDate = "2026-02-28",
        season = "2025-26",
    )

    @Test
    fun `null entry means no notice`() {
        assertNull(fogNoticeLabel(null, running = true))
        assertNull(fogNoticeLabel(null, running = false))
    }

    @Test
    fun `cancelled on a running train frames verify-before-travel`() {
        assertEquals(
            "Fog program lists this train cancelled 2025-12-01 – 2026-02-28 — verify before travel",
            fogNoticeLabel(entry("CANCELLED"), running = true),
        )
    }

    @Test
    fun `cancelled otherwise states the program fact`() {
        assertEquals(
            "Fog program: cancelled 2025-12-01 – 2026-02-28",
            fogNoticeLabel(entry("cancelled"), running = false),
        )
    }

    @Test
    fun `revised timing and reduced frequency state facts`() {
        assertEquals(
            "Fog timetable in effect 2025-12-01 – 2026-02-28",
            fogNoticeLabel(entry("REVISED_TIMING"), running = true),
        )
        assertEquals(
            "Reduced frequency 2025-12-01 – 2026-02-28 (fog program)",
            fogNoticeLabel(entry("REDUCED_FREQ"), running = false),
        )
    }

    @Test
    fun `unknown action yields nothing`() {
        assertNull(fogNoticeLabel(entry("DELAYED"), running = true))
    }
}
