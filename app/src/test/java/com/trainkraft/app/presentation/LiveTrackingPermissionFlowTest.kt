package com.trainkraft.app.presentation

import com.trainkraft.app.presentation.PermissionFlow.LiveTrackingRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Additive live-tracking checklist: order, mapping, and rationale coverage. */
class LiveTrackingPermissionFlowTest {

    @Test
    fun `nothing missing yields empty checklist`() {
        assertEquals(
            emptyList<LiveTrackingRequirement>(),
            PermissionFlow.missingLiveTrackingRequirements(false, false, false),
        )
    }

    @Test
    fun `all missing preserves order notifications exact-alarm battery`() {
        assertEquals(
            listOf(
                LiveTrackingRequirement.NOTIFICATIONS,
                LiveTrackingRequirement.EXACT_ALARM,
                LiveTrackingRequirement.BATTERY_EXEMPTION,
            ),
            PermissionFlow.missingLiveTrackingRequirements(true, true, true),
        )
    }

    @Test
    fun `each flag maps to exactly its own entry`() {
        assertEquals(
            listOf(LiveTrackingRequirement.NOTIFICATIONS),
            PermissionFlow.missingLiveTrackingRequirements(true, false, false),
        )
        assertEquals(
            listOf(LiveTrackingRequirement.EXACT_ALARM),
            PermissionFlow.missingLiveTrackingRequirements(false, true, false),
        )
        assertEquals(
            listOf(LiveTrackingRequirement.BATTERY_EXEMPTION),
            PermissionFlow.missingLiveTrackingRequirements(false, false, true),
        )
    }

    @Test
    fun `every requirement has a non-blank rationale`() {
        LiveTrackingRequirement.entries.forEach { requirement ->
            val rationale = PermissionFlow.liveTrackingRationales[requirement]
            assertTrue(
                "missing rationale for $requirement",
                !rationale.isNullOrBlank(),
            )
        }
    }

    @Test
    fun `location is excluded from live tracking`() {
        assertTrue(
            LiveTrackingRequirement.entries.none { it.name == "LOCATION" },
        )
    }
}
