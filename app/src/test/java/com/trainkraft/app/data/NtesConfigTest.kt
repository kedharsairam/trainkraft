package com.trainkraft.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Pins the failure-triggered key-invalidation contract ([NtesConfig]).
 *
 * Background: keys cache up to 7 days without retrying remote, so a
 * server-side rotation bricks live data until the TTL lapses.
 * [NtesConfig.noteSuspectKeys] (called only from [NtesApi]'s decrypt path —
 * never on transport errors or ordinary `AlertMsg` content) must clear both
 * caches so the next [NtesConfig.getKeys] refetches instead of serving the
 * known-bad keys.
 *
 * The remote fetch is deliberately NOT faked here: after invalidation the
 * test only asserts the suspect keys are gone (whatever replaces them —
 * remote, asset snapshot, or [NtesConfig.FALLBACK] — proves the refetch
 * path ran). Network attempts in the sandbox fail fast to null and fall
 * through; on a networked host the live remote keys differ from the
 * test-only suspect values either way.
 */
@RunWith(RobolectricTestRunner::class)
class NtesConfigTest {

    private lateinit var context: Context

    private val suspectKeys = NtesKeys(
        key = "SUSPECTKEY123456",
        iv = "SUSPECTIV1234567",
        sckey = "suspect1234567890abcdef01234567890",
    )

    private fun cacheFile(): File = File(context.filesDir, "ntes-keys-cache.json")

    /** Writes a fresh file-cache entry exactly as NtesConfig does. */
    private fun writeFreshCache(keys: NtesKeys) {
        val obj = JSONObject()
            .put("cachedAt", System.currentTimeMillis())
            .put(
                "keys",
                JSONObject()
                    .put("key", keys.key)
                    .put("iv", keys.iv)
                    .put("sckey", keys.sckey),
            )
        cacheFile().writeText(obj.toString())
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NtesConfig.init(context)
        NtesConfig.noteSuspectKeys()
        cacheFile().delete()
    }

    @Test
    fun `suspect marking clears caches and forces refetch past bad keys`() = runBlocking {
        // Seed both cache layers with known-bad keys: file cache is fresh, and
        // this getKeys() call also warms the in-memory layer with them.
        writeFreshCache(suspectKeys)
        assertEquals(suspectKeys, NtesConfig.getKeys(context))

        // The decrypt-failure signal: must never throw, even back-to-back.
        NtesConfig.noteSuspectKeys()
        NtesConfig.noteSuspectKeys()

        // File cache deleted ...
        assertFalse(cacheFile().exists())

        // ... and the next read cannot serve the suspect keys from any layer:
        // it runs the full order (memory → file → remote → stale → snapshot →
        // FALLBACK) instead of the memory/file fast paths.
        val next = NtesConfig.getKeys(context)
        assertNotEquals(suspectKeys, next)
    }

    @Test
    fun `suspect marking without any cache never throws and still serves keys`() = runBlocking {
        cacheFile().delete()
        NtesConfig.noteSuspectKeys()
        assertNotNull(NtesConfig.getKeys(context))
    }

    @Test
    fun `bundled snapshot asset parses to usable keys`() {
        // Shape of the repo-root file; proves the second-to-last resort in the
        // getKeys() order is valid (a stale/invalid asset must degrade to
        // FALLBACK, never a crash — parseKeys returns null on bad input).
        val text = context.assets.open("ntes-keys.json").bufferedReader().use { it.readText() }
        val keys = NtesConfig.parseKeys(JSONObject(text))
        assertNotNull(keys)
        assertEquals(16, keys!!.key.length)
        assertEquals(16, keys.iv.length)
        assertFalse(keys.sckey.isBlank())
    }
}
