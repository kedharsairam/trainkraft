package com.trainkraft.app.presentation

import android.provider.Settings
import com.trainkraft.app.BatteryExemption
import com.trainkraft.app.presentation.PermissionFlow.LiveTrackingRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Onboarding sheet pure logic: shown-gate, titles, and settings intents. */
@RunWith(RobolectricTestRunner::class)
class OnboardingSheetLogicTest {

    @Test
    fun `sheet shows only before the flag is set`() {
        assertTrue(shouldShowLiveTrackingOnboarding(false))
        assertFalse(shouldShowLiveTrackingOnboarding(true))
    }

    @Test
    fun `every requirement has a title`() {
        LiveTrackingRequirement.entries.forEach {
            assertTrue(requirementTitle(it).isNotBlank())
        }
    }

    @Test
    fun `notification row opens app notification settings`() {
        val intent = requirementSettingsIntent("com.trainkraft.app", LiveTrackingRequirement.NOTIFICATIONS)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("com.trainkraft.app", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `exact-alarm row opens the schedule-exact-alarm screen`() {
        val intent = requirementSettingsIntent("com.trainkraft.app", LiveTrackingRequirement.EXACT_ALARM)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
    }

    @Test
    fun `battery row uses the exemption intent`() {
        val intent = requirementSettingsIntent("com.pkg", LiveTrackingRequirement.BATTERY_EXEMPTION)
        val expected = BatteryExemption.exemptionIntent("com.pkg")
        assertEquals(expected.action, intent.action)
        assertEquals(expected.data, intent.data)
    }
}
