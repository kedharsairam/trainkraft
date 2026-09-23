package com.trainkraft.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization exemption helpers (Phase C onboarding).
 *
 * WHY: the minute-level [TrackingService] loop and exact station alarms only
 * survive on OEM skins (notably ColorOS) when the app is exempt from battery
 * optimizations. This object answers "are we exempt?" ([isExempt]) and builds
 * the one-tap Settings intent ([exemptionIntent]) the onboarding screen
 * fires. The grant itself is a user action in Settings — there is no
 * programmatic path.
 *
 * NO MANIFEST PERMISSION EXISTS OR IS NEEDED: requesting the exemption is a
 * plain `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent with a
 * `package:` URI for our own package. Deliberately NOT declared:
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` must never appear in the manifest
 * (Play policy flags it; the manifest comment already records this). This is
 * the documented, policy-safe route — the same one the manifest's Phase C
 * comment prescribes.
 *
 * DOZE HONESTY: denial is fully supported, not an error — the 15-min worker
 * baseline + inexact-alarm fallback keep covering the journey, just slower.
 * Onboarding copy (peer's) must present the exemption as "more punctual
 * alerts", never as required. ColorOS kill behavior with/without the
 * exemption is a device-session field-test item (phone off USB: unverified).
 */
object BatteryExemption {

    /** `package:` URI scheme used by the exemption Settings intent. */
    const val PACKAGE_URI_SCHEME = "package"

    /**
     * True when the OS already ignores battery optimizations for us
     * (user granted the exemption, or the OEM default exempts us).
     */
    fun isExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { powerManager.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    /**
     * One-tap intent targeting the system "don't optimize" screen for
     * [packageName]. Pure intent-builder (no Context) so it is unit-testable
     * under Robolectric; the Context overload supplies our own package.
     */
    fun exemptionIntent(packageName: String): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts(PACKAGE_URI_SCHEME, packageName, null),
        )

    fun exemptionIntent(context: Context): Intent =
        exemptionIntent(context.packageName)
}
