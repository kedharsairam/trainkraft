package com.trainkraft.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [NtesCrypto.encrypt] → [NtesCrypto.decrypt] round-trips,
 * mirroring the Python `NTESCrypto.build` / `NTESCrypto.decode` contract.
 *
 * NOTE: [NtesCrypto] uses android.util.Base64, so this test must run on an
 * Android runtime (e.g. `./gradlew :app:connectedAndroidTest` or Robolectric).
 * It compiles as a plain unit test but will fail with "Stub!" if executed on
 * a local JVM without an Android framework stub.
 */
class NtesCryptoTest {

    @Test
    fun `encrypt then decrypt roundtrips live-status payload`() {
        val payload =
            "service=TrainRunningMob&subService=ShowFullRunJson" +
                "&trainNo=12639&startDate=18-SEP-2026"
        assertEquals(payload, NtesCrypto.decrypt(NtesCrypto.encrypt(payload)))
    }

    @Test
    fun `encrypt then decrypt roundtrips unicode payload`() {
        val payload = "service=X&name=чернівці-हिन्दी-✓"
        assertEquals(payload, NtesCrypto.decrypt(NtesCrypto.encrypt(payload)))
    }

    @Test
    fun `encrypt output is HASH pound ENC in uppercase`() {
        val encrypted = NtesCrypto.encrypt("service=TrainRunningMob&subService=FindTrainJson&trainNo=12639")
        val parts = encrypted.split("#")
        assertEquals(2, parts.size)
        val (hash, enc) = parts
        assertEquals(32, hash.length) // MD5 hex
        assertEquals(hash, hash.uppercase())
        assertEquals(enc, enc.uppercase())
        assertTrue(hash.all { it in '0'..'9' || it in 'A'..'F' })
        assertTrue(enc.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `decrypt accepts bare ENC without hash prefix`() {
        val payload = "service=TrainRunningMob&subService=FindTrainJson&trainNo=MAS"
        val bareEnc = NtesCrypto.encrypt(payload).substringAfter("#")
        assertEquals(payload, NtesCrypto.decrypt(bareEnc))
    }

    @Test
    fun `decrypt is deterministic across calls`() {
        val payload = "service=TrainRunningMob&subService=ShowFullRunJson&trainNo=12639&startDate=18-SEP-2026"
        // Fixed key+IV (no random salt) must give byte-identical output, like Python.
        assertEquals(NtesCrypto.encrypt(payload), NtesCrypto.encrypt(payload))
    }
}
