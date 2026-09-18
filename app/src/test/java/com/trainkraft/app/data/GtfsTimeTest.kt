package com.trainkraft.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GtfsTimeTest {

    @Test
    fun `format 0 minutes is 00_00`() {
        assertEquals("00:00", GtfsTime.format(0))
    }

    @Test
    fun `format 60 minutes is 01_00`() {
        assertEquals("01:00", GtfsTime.format(60))
    }

    @Test
    fun `format 720 minutes is 12_00`() {
        assertEquals("12:00", GtfsTime.format(720))
    }

    @Test
    fun `format 1439 minutes is 23_59`() {
        assertEquals("23:59", GtfsTime.format(1439))
    }

    @Test
    fun `format with day offset appends suffix`() {
        assertEquals("01:30 +1 d", GtfsTime.format(90, dayOffset = 1))
    }

    @Test
    fun `format with day offset 0 has no suffix`() {
        assertEquals("01:30", GtfsTime.format(90, dayOffset = 0))
    }

    @Test
    fun `format12h midnight is 12_00 AM`() {
        assertEquals("12:00 AM", GtfsTime.format12h(0))
    }

    @Test
    fun `format12h noon is 12_00 PM`() {
        assertEquals("12:00 PM", GtfsTime.format12h(720))
    }

    @Test
    fun `format12h 13_00 is 1_00 PM`() {
        assertEquals("1:00 PM", GtfsTime.format12h(780))
    }

    @Test
    fun `format12h 23_59 is 11_59 PM`() {
        assertEquals("11:59 PM", GtfsTime.format12h(1439))
    }

    @Test
    fun `format12h with day offset`() {
        assertEquals("1:30 AM +1 d", GtfsTime.format12h(90, dayOffset = 1))
    }

    @Test
    fun `parse then format roundtrips`() {
        val parsed = GtfsTime.parse("13:45:00")
        assertEquals(825, parsed.minutesInDay)
        assertEquals(0, parsed.dayOffset)
        assertEquals("13:45", GtfsTime.format(parsed.minutesInDay, parsed.dayOffset))
    }

    @Test
    fun `parse next-day time`() {
        val parsed = GtfsTime.parse("25:30:00")
        assertEquals(90, parsed.minutesInDay)
        assertEquals(1, parsed.dayOffset)
        assertEquals("01:30 +1 d", GtfsTime.format(parsed.minutesInDay, parsed.dayOffset))
    }

    @Test
    fun `format with use24h false uses 12h`() {
        assertEquals("1:30 AM", GtfsTime.format(90, use24h = false))
    }

    @Test
    fun `format with use24h true uses 24h`() {
        assertEquals("01:30", GtfsTime.format(90, use24h = true))
    }
}
