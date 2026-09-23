package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the foreground ticker ladder: ≤15 → 30s; ≤60 → 60s; else 120s; null → 60s. */
class TickerLadderTest {

    @Test
    fun `null input ticks at 60s`() {
        assertEquals(60L, tickerIntervalSec(null))
    }

    @Test
    fun `within 15 min ticks at 30s`() {
        assertEquals(30L, tickerIntervalSec(15L))
        assertEquals(30L, tickerIntervalSec(0L))
        assertEquals(30L, tickerIntervalSec(-2L))
        assertEquals(30L, tickerIntervalSec(5L))
    }

    @Test
    fun `within the hour ticks at 60s`() {
        assertEquals(60L, tickerIntervalSec(16L))
        assertEquals(60L, tickerIntervalSec(60L))
    }

    @Test
    fun `beyond the hour ticks at 120s`() {
        assertEquals(120L, tickerIntervalSec(61L))
        assertEquals(120L, tickerIntervalSec(600L))
    }
}
