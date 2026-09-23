package com.trainkraft.app.presentation

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetweenLogicTest {

    // ---- dayOfRun parsing ----

    @Test
    fun `Daily matches every weekday`() {
        val days = parseDayOfRun("Daily")
        assertEquals(7, days.size)
        assertTrue(days.containsAll(enumValues<DayOfWeek>().toList()))
    }

    @Test
    fun `Daily is case-insensitive and trims`() {
        assertEquals(parseDayOfRun("Daily"), parseDayOfRun("  DAILY "))
    }

    @Test
    fun `single abbreviation parses`() {
        assertEquals(setOf(DayOfWeek.MONDAY), parseDayOfRun("Mon"))
        assertEquals(setOf(DayOfWeek.FRIDAY), parseDayOfRun("Fri"))
        assertEquals(setOf(DayOfWeek.SUNDAY), parseDayOfRun("Sun"))
    }

    @Test
    fun `comma lists parse regardless of case and spacing`() {
        val expected = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals(expected, parseDayOfRun("Mon,Wed,Fri"))
        assertEquals(expected, parseDayOfRun("Mon, Wed, Fri"))
        assertEquals(expected, parseDayOfRun("mon,wed,fri"))
    }

    @Test
    fun `fixture vocabulary parses`() {
        // Exact tokens seen in trains_between_NDLS_MMCT.json.
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            parseDayOfRun("Mon,thu"),
        )
        assertEquals(
            setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            parseDayOfRun("Tue,wed,sun"),
        )
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            parseDayOfRun("Mon,tue,thu,sat"),
        )
        assertEquals(
            setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            parseDayOfRun("Tue,wed,fri,sat"),
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), parseDayOfRun("Mon,sat"))
    }

    @Test
    fun `unknown tokens never crash and match nothing extra`() {
        assertTrue(parseDayOfRun("").size == 7) // blank = unknown schedule: shown
        assertTrue(parseDayOfRun("Funday").isEmpty())
        // Known tokens survive alongside garbage.
        assertEquals(setOf(DayOfWeek.MONDAY), parseDayOfRun("Mon,Funday"))
    }

    @Test
    fun `runsOnDay delegates to parsing`() {
        assertTrue(runsOnDay("Daily", DayOfWeek.SUNDAY))
        assertTrue(runsOnDay("Mon,thu", DayOfWeek.THURSDAY))
        assertFalse(runsOnDay("Mon,thu", DayOfWeek.TUESDAY))
    }

    @Test
    fun `running week mask is Monday-first`() {
        assertEquals(
            listOf(true, false, true, false, true, false, false),
            runningWeekMask("Mon,Wed,Fri"),
        )
        assertEquals(List(7) { true }, runningWeekMask("Daily"))
    }

    // ---- counts ----

    @Test
    fun `weekly counts are consistent`() {
        val dayOfRuns = listOf("Daily", "Mon", "Mon,Wed,Fri", "Tue,wed,sun")
        val monday = LocalDate.of(2026, 9, 21) // a Monday
        val week = betweenWeekDates(monday)
        assertEquals(7, week.size)
        val counts = countsForDates(dayOfRuns, week)
        // Mon 3, Tue 2, Wed 3, Thu 1, Fri 2, Sat 1, Sun 2.
        assertEquals(listOf(3, 2, 3, 1, 2, 1, 2), counts)
        assertEquals(14, counts.sum())
    }

    @Test
    fun `countRunningOn single day`() {
        val dayOfRuns = listOf("Daily", "Mon", "Fri", "Mon,thu", "Tue")
        assertEquals(3, countRunningOn(dayOfRuns, DayOfWeek.MONDAY)) // Daily + Mon + Mon,thu
        assertEquals(2, countRunningOn(dayOfRuns, DayOfWeek.THURSDAY)) // Daily + Mon,thu
        assertEquals(1, countRunningOn(dayOfRuns, DayOfWeek.SATURDAY)) // Daily only
    }

    // ---- duration ----

    @Test
    fun `travel time to minutes`() {
        assertEquals(1152, durationMinutes("19:12"))
        assertEquals(1585, durationMinutes("26:25")) // >24h leg
        assertEquals(5 * 60 + 10, durationMinutes("05:10"))
    }

    @Test
    fun `malformed travel time is null`() {
        assertNull(durationMinutes(""))
        assertNull(durationMinutes("Daily"))
        assertNull(durationMinutes("19:70"))
        assertNull(durationMinutes("19"))
    }

    @Test
    fun `formatDuration renders compactly`() {
        assertEquals("19h 12m", formatDuration(1152))
        assertEquals("45m", formatDuration(45))
        assertEquals("2h", formatDuration(120))
    }

    // ---- arrival day offset ----

    @Test
    fun `overnight arrival infers plus one`() {
        // 05:00 + 19:12 = 00:12 next day.
        assertEquals(1, inferArrivalDayOffset(depMin = 300, arrMin = 12, travelMinutes = 1152))
    }

    @Test
    fun `multi-day leg from travel time`() {
        // 18:55 + 27:35 → +1d; 05:10 + 26:25 → +1d.
        assertEquals(1, inferArrivalDayOffset(1135, 1350, 1655))
        assertEquals(1, inferArrivalDayOffset(310, 455, 1585))
    }

    @Test
    fun `same-day arrival stays zero`() {
        assertEquals(0, inferArrivalDayOffset(depMin = 310, arrMin = 455, travelMinutes = 85))
    }

    @Test
    fun `fallback rollover without travel time`() {
        assertEquals(1, inferArrivalDayOffset(depMin = 1150, arrMin = 90, travelMinutes = null))
        assertEquals(0, inferArrivalDayOffset(depMin = 300, arrMin = 900, travelMinutes = null))
        // Equal times count as next-day (NTES midnight wrap).
        assertEquals(1, inferArrivalDayOffset(depMin = 300, arrMin = 300, travelMinutes = null))
    }

    @Test
    fun `arrival plus label`() {
        assertNull(arrivalPlusLabel(0))
        assertEquals("+1d", arrivalPlusLabel(1))
        assertEquals("+2d", arrivalPlusLabel(2))
    }

    // ---- classes / type filters ----

    @Test
    fun `splitClasses keeps raw tokens`() {
        assertEquals(
            listOf("1A", "2A", "3A", "SL", "GEN", "PWD"),
            splitClasses("1A,2A,3A,SL,GEN,PWD"),
        )
        assertEquals(listOf("SL", "3A"), splitClasses(" SL, 3A "))
        assertTrue(splitClasses("").isEmpty())
    }

    @Test
    fun `distinctTypeFilters sorted unique non-blank`() {
        val rows = listOf(
            row("1", typeDesc = "Superfast"),
            row("2", typeDesc = "Rajdhani"),
            row("3", typeDesc = "Superfast"),
            row("4", typeDesc = ""),
        )
        assertEquals(listOf("Rajdhani", "Superfast"), distinctTypeFilters(rows))
    }

    // ---- sorting / view ----

    private fun row(
        number: String,
        depMin: Int = 300,
        arrMin: Int = 732,
        arrDayOffset: Int = 1,
        dayOfRun: String = "Daily",
        typeDesc: String = "Superfast",
    ) = BetweenUiRow(
        trainNumber = number,
        trainName = "Test $number",
        fromCode = "NDLS",
        fromName = "New Delhi",
        depMin = depMin,
        depDayOffset = 0,
        toCode = "MMCT",
        toName = "Mumbai Central",
        arrMin = arrMin,
        arrDayOffset = arrDayOffset,
        dayOfRun = dayOfRun,
        typeDesc = typeDesc,
    )

    @Test
    fun `sort by departure time`() {
        val rows = listOf(row("C", depMin = 1300), row("A", depMin = 300), row("B", depMin = 315))
        val sorted = applyBetweenView(rows, LocalDate.of(2026, 9, 23), null, BetweenSort.DEPARTURE)
        assertEquals(listOf("A", "B", "C"), sorted.map { it.trainNumber })
    }

    @Test
    fun `sort by duration`() {
        val rows = listOf(
            row("slow", depMin = 300, arrMin = 300, arrDayOffset = 1), // 1440m
            row("fast", depMin = 300, arrMin = 732, arrDayOffset = 0), // 432m
        )
        val sorted = applyBetweenView(rows, LocalDate.of(2026, 9, 23), null, BetweenSort.DURATION)
        assertEquals(listOf("fast", "slow"), sorted.map { it.trainNumber })
    }

    @Test
    fun `sort by arrival time`() {
        val rows = listOf(
            row("late", depMin = 300, arrMin = 100, arrDayOffset = 1), // 01:40 +1d
            row("early", depMin = 1300, arrMin = 1400, arrDayOffset = 0), // 23:20 same day
        )
        val sorted = applyBetweenView(rows, LocalDate.of(2026, 9, 23), null, BetweenSort.ARRIVAL)
        assertEquals(listOf("early", "late"), sorted.map { it.trainNumber })
    }

    @Test
    fun `date filter keeps only trains running that day`() {
        val rows = listOf(row("daily", dayOfRun = "Daily"), row("mon", dayOfRun = "Mon"))
        val wednesday = LocalDate.of(2026, 9, 23)
        val filtered = applyBetweenView(rows, wednesday, null, BetweenSort.DEPARTURE)
        assertEquals(listOf("daily"), filtered.map { it.trainNumber })
        val monday = LocalDate.of(2026, 9, 21)
        assertEquals(2, applyBetweenView(rows, monday, null, BetweenSort.DEPARTURE).size)
    }

    @Test
    fun `type filter narrows without re-query`() {
        val rows = listOf(
            row("1", typeDesc = "Superfast"),
            row("2", typeDesc = "Rajdhani"),
        )
        val monday = LocalDate.of(2026, 9, 21)
        assertEquals(
            listOf("2"),
            applyBetweenView(rows, monday, "Rajdhani", BetweenSort.DEPARTURE)
                .map { it.trainNumber },
        )
        assertEquals(
            2,
            applyBetweenView(rows, monday, BETWEEN_ALL_FILTER, BetweenSort.DEPARTURE).size,
        )
        assertEquals(
            2,
            applyBetweenView(rows, monday, null, BetweenSort.DEPARTURE).size,
        )
    }

    @Test
    fun `sorting is stable with train-number tiebreak`() {
        val rows = listOf(row("B", depMin = 300), row("A", depMin = 300))
        val sorted = applyBetweenView(rows, LocalDate.of(2026, 9, 23), null, BetweenSort.DEPARTURE)
        assertEquals(listOf("A", "B"), sorted.map { it.trainNumber })
    }
}
