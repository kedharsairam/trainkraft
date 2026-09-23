package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Phase D travel-mode location rationale copy: it must stay
 * distinct from Go-live (GPS while riding vs minute server checks) and
 * honest about on-device fixes + the Stop path. Plain JUnit.
 */
class TravelModeRationaleTest {

    @Test
    fun `rationale copy is pinned verbatim`() {
        assertEquals(
            "On-board travel mode uses your phone's GPS while you ride — " +
                "live speed, next-stop distance and arrival detection. Fixes stay " +
                "on this phone and stop when you tap Stop. " +
                "(Go-live instead checks the server every minute, without GPS.)",
            PermissionFlow.TRAVEL_MODE_LOCATION_RATIONALE,
        )
    }

    @Test
    fun `rationale names GPS, the on-device boundary and the stop path`() {
        val copy = PermissionFlow.TRAVEL_MODE_LOCATION_RATIONALE
        assertTrue(copy.contains("GPS"))
        assertTrue(copy.contains("stay"))
        assertTrue(copy.contains("Stop"))
    }

    @Test
    fun `rationale stays distinct from Go-live server checks`() {
        val copy = PermissionFlow.TRAVEL_MODE_LOCATION_RATIONALE
        assertTrue(copy.contains("Go-live"))
        assertTrue(copy.contains("server"))
    }
}
