package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Agent-B-UI: between-stations typical-delay badge label (pure, JVM).
 *
 * The badge reads the pack arrival prior at the destination stop:
 * `"usually +25"` when typically late, `"usually on time"` when typically on
 * time, and no badge (null) when the pack has no row — never invented.
 */
class BetweenBadgeTest {

    @Test
    fun `late prior labels usually-plus`() {
        assertEquals("usually +25", usualDelayBadgeLabel(25))
        assertEquals("usually +1", usualDelayBadgeLabel(1))
    }

    @Test
    fun `on-time prior labels usually on time`() {
        assertEquals("usually on time", usualDelayBadgeLabel(0))
        assertEquals("usually on time", usualDelayBadgeLabel(-2))
    }

    @Test
    fun `missing prior yields no badge`() {
        assertNull(usualDelayBadgeLabel(null))
    }
}
