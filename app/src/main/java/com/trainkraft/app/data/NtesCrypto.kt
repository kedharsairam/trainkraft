package com.trainkraft.app.data

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES crypto for the NTES AppServAnd endpoint.
 *
 * Direct port of the verified Python implementation
 * (train-status-deep/ntes-client/ntes/crypto.py, Sep 2026):
 *
 * - Key: 16 ASCII bytes (AES-128)
 * - IV:  16 ASCII bytes (CBC IV)
 * - sckey: hex string for MD5 integrity hash
 * - Encrypt: hash = MD5(data + sckey).hexdigest().upper()
 *            cipher = AES-CBC-PKCS7(data) with key+iv
 *            enc = hexlify(base64encode(ciphertext)).upper()
 *            payload = hash + "#" + enc
 * - Decrypt: split "#", unhexlify, base64decode, AES-CBC decrypt, unpad, UTF-8 string
 *
 * Note: "AES/CBC/PKCS5Padding" on the JVM is PKCS#7 padding for 16-byte
 * blocks, so it is byte-identical to Python's PKCS7 pad/unpad here.
 */
object NtesCrypto {

    /** Empty fallbacks — force remote fetch or graceful failure. */
    const val KEY = ""
    const val IV = ""
    const val SCKEY = ""

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    private fun newCipher(mode: Int, keys: NtesKeys = NtesKeys()): Cipher {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(keys.key.toByteArray(Charsets.US_ASCII), "AES")
        val ivSpec = IvParameterSpec(keys.iv.toByteArray(Charsets.US_ASCII))
        cipher.init(mode, keySpec, ivSpec)
        return cipher
    }

    private fun md5HexUpper(data: String, keys: NtesKeys = NtesKeys()): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest((data + keys.sckey).toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_CHARS[(b.toInt() shr 4) and 0xF])
            sb.append(HEX_CHARS[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private fun bytesToHexUpper(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_CHARS[(b.toInt() shr 4) and 0xF])
            sb.append(HEX_CHARS[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd-length hex string" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi != -1 && lo != -1) { "invalid hex character" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /**
     * Mirrors Python `NTESCrypto.build(data)`:
     * returns "<MD5(data+sckey).upper()>#<hex(base64(AES-CBC(data))).upper()>".
     *
     * @param keys crypto keys; defaults to the hardcoded constants so existing
     * callers (and [NtesKeys] defaults) keep working.
     */
    fun encrypt(payload: String, keys: NtesKeys = NtesKeys()): String {
        require(payload.isNotEmpty()) { "empty payload" }
        val encrypted = newCipher(Cipher.ENCRYPT_MODE, keys)
            .doFinal(payload.toByteArray(Charsets.UTF_8))
        val b64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val enc = bytesToHexUpper(b64.toByteArray(Charsets.US_ASCII))
        return "${md5HexUpper(payload, keys)}#$enc"
    }

    /**
     * Mirrors Python `NTESCrypto.decode(enc)` except it returns the raw
     * decrypted UTF-8 string (Python json-parses it; [NtesApi] parses the
     * JSON instead so callers keep the raw response).
     *
     * Accepts either the full "HASH#ENC" payload or the bare ENC part.
     */
    fun decrypt(response: String, keys: NtesKeys = NtesKeys()): String {
        require(response.isNotEmpty()) { "empty input" }
        // Python: enc.split("#", 1)[1] — everything after the first "#".
        val enc = if ("#" in response) response.substringAfter("#") else response
        try {
            val b64Ascii = String(hexToBytes(enc), Charsets.US_ASCII)
            val cipherBytes = Base64.decode(b64Ascii, Base64.DEFAULT)
            val plain = newCipher(Cipher.DECRYPT_MODE, keys).doFinal(cipherBytes)
            return String(plain, Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("decryption failed: ${e.message}", e)
        }
    }
}
