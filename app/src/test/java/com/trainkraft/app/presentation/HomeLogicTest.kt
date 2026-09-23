package com.trainkraft.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLogicTest {

    @Test
    fun `empty tracked section shows bare count`() {
        assertEquals("Tracked trains (0)", trackedSectionLabel(0))
    }

    @Test
    fun `singular count reads naturally`() {
        assertEquals("Tracked trains (1)", trackedSectionLabel(1))
    }

    @Test
    fun `plural counts show the number`() {
        assertEquals("Tracked trains (2)", trackedSectionLabel(2))
        assertEquals("Tracked trains (12)", trackedSectionLabel(12))
    }
}
