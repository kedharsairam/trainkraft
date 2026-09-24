package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertsLogFormatTest {

    @Test
    fun `sub-minute age reads just now`() {
        assertEquals("just now", formatAlertAge(60_000, 60_000))
        assertEquals("just now", formatAlertAge(60_000, 59_999))
        assertEquals("just now", formatAlertAge(119_999, 60_000))
    }

    @Test
    fun `minutes read as m ago`() {
        assertEquals("1m ago", formatAlertAge(120_000, 60_000))
        assertEquals("5m ago", formatAlertAge(360_000, 60_000))
        assertEquals("59m ago", formatAlertAge(60 * 60_000, 60_000))
    }

    @Test
    fun `hours read as h ago`() {
        assertEquals("1h ago", formatAlertAge(61 * 60_000, 60_000))
        assertEquals("3h ago", formatAlertAge(181 * 60_000, 60_000))
        assertEquals("23h ago", formatAlertAge(24 * 60 * 60_000, 60 * 60_000))
    }

    @Test
    fun `days read as d ago`() {
        assertEquals("1d ago", formatAlertAge(25 * 3_600_000, 3_600_000))
        assertEquals("2d ago", formatAlertAge(49 * 3_600_000, 3_600_000))
        // 90 days overflows Int ms — Long literal required.
        assertEquals("90d ago", formatAlertAge(90L * 24 * 3_600_000, 0))
    }

    @Test
    fun `future timestamps clamp to just now`() {
        // Clock skew must never render a negative age.
        assertEquals("just now", formatAlertAge(60_000, 120_000))
    }
}
