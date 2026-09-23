package com.trainkraft.app.presentation

import com.trainkraft.app.presentation.PermissionFlow.TrackingPermission
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the pure core of [PermissionFlow.missingForTracking] via
 * [PermissionFlow.orderMissing]. The Context-taking overload only supplies
 * booleans to this function, so ordering/semantics are fully pinned here
 * with plain JUnit — no Robolectric or Context mocking required.
 */
class PermissionFlowTest {

    @Test
    fun `nothing missing yields empty checklist`() {
        assertEquals(
            emptyList<TrackingPermission>(),
            PermissionFlow.orderMissing(
                notificationsMissing = false,
                locationMissing = false,
                exactAlarmMissing = false,
            ),
        )
    }

    @Test
    fun `all missing preserves checklist order notifications location exact alarm`() {
        assertEquals(
            listOf(
                TrackingPermission.NOTIFICATIONS,
                TrackingPermission.LOCATION,
                TrackingPermission.EXACT_ALARM,
            ),
            PermissionFlow.orderMissing(
                notificationsMissing = true,
                locationMissing = true,
                exactAlarmMissing = true,
            ),
        )
    }

    @Test
    fun `each flag maps to exactly its own entry`() {
        assertEquals(
            listOf(TrackingPermission.NOTIFICATIONS),
            PermissionFlow.orderMissing(true, false, false),
        )
        assertEquals(
            listOf(TrackingPermission.LOCATION),
            PermissionFlow.orderMissing(false, true, false),
        )
        assertEquals(
            listOf(TrackingPermission.EXACT_ALARM),
            PermissionFlow.orderMissing(false, false, true),
        )
    }

    @Test
    fun `partial gaps keep relative order`() {
        assertEquals(
            listOf(TrackingPermission.NOTIFICATIONS, TrackingPermission.EXACT_ALARM),
            PermissionFlow.orderMissing(true, false, true),
        )
        assertEquals(
            listOf(TrackingPermission.LOCATION, TrackingPermission.EXACT_ALARM),
            PermissionFlow.orderMissing(false, true, true),
        )
        assertEquals(
            listOf(TrackingPermission.NOTIFICATIONS, TrackingPermission.LOCATION),
            PermissionFlow.orderMissing(true, true, false),
        )
    }
}
