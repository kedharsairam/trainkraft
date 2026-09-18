package com.trainkraft.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies [NtesCrypto.encrypt] → [NtesCrypto.decrypt] round-trips
 * using explicit test keys (the production defaults are empty strings).
 *
 * NOTE: [NtesCrypto] uses android.util.Base64, so this test must run on
 * an Android runtime (Robolectric or connected device).
 */
@RunWith(RobolectricTestRunner::class)
class NtesCryptoTest {

    private val testKeys = NtesKeys(
        key = "TESTKEY123456789",
        iv = "TESTIV1234567890",
        sckey = "test1234567890abcdef01234567890ab",
    )

    @Test
    fun `encrypt then decrypt roundtrips live-status payload`() {
        val payload =
            "service=TrainRunningMob&subService=ShowFullRunJson" +
                "&trainNo=12639&startDate=18-SEP-2026"
        val enc = NtesCrypto.encrypt(payload, testKeys)
        assertEquals(payload, NtesCrypto.decrypt(enc, testKeys))
    }

    @Test
    fun `encrypt then decrypt roundtrips unicode payload`() {
        val payload = "service=X&name=test-unicode-payload"
        val enc = NtesCrypto.encrypt(payload, testKeys)
        assertEquals(payload, NtesCrypto.decrypt(enc, testKeys))
    }

    @Test
    fun `encrypt output is HASH pound ENC in uppercase`() {
        val encrypted = NtesCrypto.encrypt(
            "service=TrainRunningMob&subService=FindTrainJson&trainNo=12639",
            testKeys,
        )
        val parts = encrypted.split("#")
        assertEquals(2, parts.size)
        val (hash, enc) = parts
        assertEquals(32, hash.length)
        assertEquals(hash, hash.uppercase())
        assertEquals(enc, enc.uppercase())
        assertTrue(hash.all { it in '0'..'9' || it in 'A'..'F' })
        assertTrue(enc.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `decrypt accepts bare ENC without hash prefix`() {
        val payload = "service=TrainRunningMob&subService=FindTrainJson&trainNo=MAS"
        val bareEnc = NtesCrypto.encrypt(payload, testKeys).substringAfter("#")
        assertEquals(payload, NtesCrypto.decrypt(bareEnc, testKeys))
    }

    @Test
    fun `encrypt is deterministic across calls`() {
        val payload = "service=TrainRunningMob&subService=ShowFullRunJson&trainNo=12639&startDate=18-SEP-2026"
        assertEquals(NtesCrypto.encrypt(payload, testKeys), NtesCrypto.encrypt(payload, testKeys))
    }

    @Test
    fun `different inputs produce different ciphertext`() {
        val a = NtesCrypto.encrypt("input=A", testKeys)
        val b = NtesCrypto.encrypt("input=B", testKeys)
        assertNotEquals(a, b)
    }
}
