package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessFormattingTest {

    @Test
    fun `cache age under a minute reads just now`() {
        assertEquals("just now", cachedAgeLabel(0))
        assertEquals("just now", cachedAgeLabel(59_000))
    }

    @Test
    fun `cache age minutes hours days`() {
        assertEquals("12m ago", cachedAgeLabel(12 * 60_000L))
        assertEquals("59m ago", cachedAgeLabel(59 * 60_000L))
        assertEquals("3h ago", cachedAgeLabel(3 * 3_600_000L))
        assertEquals("2d ago", cachedAgeLabel(2 * 86_400_000L))
    }

    @Test
    fun `negative age clamps to just now`() {
        assertEquals("just now", cachedAgeLabel(-5_000))
    }

    @Test
    fun `delay on time or early`() {
        assertEquals("On time", formatDelay(0))
        assertEquals("On time", formatDelay(-4))
    }

    @Test
    fun `delay under an hour is minutes`() {
        assertEquals("+6m", formatDelay(6))
        assertEquals("+26m", formatDelay(26))
        assertEquals("+59m", formatDelay(59))
    }

    @Test
    fun `delay over an hour is hours minutes`() {
        assertEquals("+1h 05m", formatDelay(65))
        assertEquals("+2h 00m", formatDelay(120))
        assertEquals("+10h 59m", formatDelay(659))
    }

    @Test
    fun `clock label is HH mm`() {
        // Fixed epoch; expected computed in the same zone the SUT uses.
        val label = clockLabel(1_790_000_000_000L)
        assertTrue("got '$label'", Regex("^\\d{2}:\\d{2}$").matches(label))
    }

    @Test
    fun `tabular figures adds tnum feature`() {
        val base = androidx.compose.ui.text.TextStyle()
        val tab = tabularFigures(base)
        assertEquals("tnum", tab.fontFeatureSettings)
    }
}
