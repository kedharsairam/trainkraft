package com.trainkraft.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Notification-tap extra parsing: valid numbers pass, everything else stays home. */
class DeepLinkParsingTest {

    @Test
    fun `valid train number passes through trimmed`() {
        assertEquals("12951", parseDeepLinkTrainNumber("12951"))
        assertEquals("12951", parseDeepLinkTrainNumber("  12951  "))
    }

    @Test
    fun `blank null and malformed extras yield null`() {
        assertNull(parseDeepLinkTrainNumber(null))
        assertNull(parseDeepLinkTrainNumber(""))
        assertNull(parseDeepLinkTrainNumber("   "))
        assertNull(parseDeepLinkTrainNumber("NDLS"))
        assertNull(parseDeepLinkTrainNumber("12"))
        assertNull(parseDeepLinkTrainNumber("1234567"))
        assertNull(parseDeepLinkTrainNumber("12A51"))
    }
}
