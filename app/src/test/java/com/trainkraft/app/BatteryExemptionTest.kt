package com.trainkraft.app

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Covers [BatteryExemption]: the pure Settings-intent builder (action +
 * `package:` URI, both overloads) and [BatteryExemption.isExempt] against
 * Robolectric's ShadowPowerManager in both grant states.
 */
@RunWith(RobolectricTestRunner::class)
class BatteryExemptionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `exemption intent targets the ignore-optimizations screen for the package`() {
        val intent = BatteryExemption.exemptionIntent("com.trainkraft.app")
        assertEquals(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            intent.action,
        )
        assertEquals("package", intent.data?.scheme)
        assertEquals("com.trainkraft.app", intent.data?.schemeSpecificPart)
    }

    @Test
    fun `context overload uses our own package`() {
        val intent = BatteryExemption.exemptionIntent(context)
        assertEquals(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            intent.action,
        )
        assertEquals(context.packageName, intent.data?.schemeSpecificPart)
    }

    @Test
    fun `isExempt reflects the OS grant state`() {
        val powerManager = context.getSystemService(PowerManager::class.java)!!
        shadowOf(powerManager).setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(BatteryExemption.isExempt(context))
        shadowOf(powerManager).setIgnoringBatteryOptimizations(context.packageName, false)
        assertFalse(BatteryExemption.isExempt(context))
    }
}
