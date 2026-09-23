package com.trainkraft.app.presentation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase E: "Smartest" sort + best-pick chip (pure, JVM).
 *
 * Rule: predicted arrival = scheduled arrival + usual delay (pack priors
 * only, no network). Missing badge (absent key = no pack row) sorts by
 * schedule — no invented delay. Ties break by departure, then number.
 */
class SmartestSortTest {

    private val monday = LocalDate.of(2026, 9, 21)

    private fun row(
        number: String,
        depMin: Int = 300,
        arrMin: Int = 600,
        arrDayOffset: Int = 0,
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
        dayOfRun = "Daily",
    )

    private fun smartest(
        rows: List<BetweenUiRow>,
        usual: Map<String, Int>,
    ) = applyBetweenView(rows, monday, null, BetweenSort.SMARTEST, usual)
        .map { it.trainNumber }

    @Test
    fun `usual delay pushes scheduled-early train behind`() {
        val rows = listOf(
            row("early", depMin = 300, arrMin = 600),
            row("late", depMin = 310, arrMin = 700),
        )
        // early predicts 600 + 120 = 720; late has no badge → 700.
        assertEquals(listOf("late", "early"), smartest(rows, mapOf("early" to 120)))
    }

    @Test
    fun `missing badge sorts by schedule`() {
        val rows = listOf(
            row("b", depMin = 300, arrMin = 700),
            row("a", depMin = 400, arrMin = 600),
        )
        assertEquals(listOf("a", "b"), smartest(rows, emptyMap()))
    }

    @Test
    fun `non-positive usual adds nothing`() {
        val rows = listOf(
            row("b", depMin = 300, arrMin = 700),
            row("a", depMin = 400, arrMin = 600),
        )
        val usual = mapOf("a" to 0, "b" to -5)
        assertEquals(listOf("a", "b"), smartest(rows, usual))
    }

    @Test
    fun `predicted-arrival ties break by departure then number`() {
        val rows = listOf(
            row("c", depMin = 300, arrMin = 600), // predicts 660
            row("b", depMin = 200, arrMin = 660), // predicts 660, earlier dep
            row("a", depMin = 200, arrMin = 660), // predicts 660, same dep, lower number
        )
        val usual = mapOf("c" to 60)
        assertEquals(listOf("a", "b", "c"), smartest(rows, usual))
    }

    @Test
    fun `best pick is the top card when it has badge data`() {
        val rows = listOf(row("x", arrMin = 600), row("y", arrMin = 700))
        val visible = applyBetweenView(rows, monday, null, BetweenSort.SMARTEST, mapOf("x" to 10))
        assertEquals("x", smartestBestPickNumber(visible, BetweenSort.SMARTEST, mapOf("x" to 10)))
    }

    @Test
    fun `no best pick without badge data on top`() {
        val rows = listOf(row("x", arrMin = 600), row("y", arrMin = 700))
        val visible = applyBetweenView(rows, monday, null, BetweenSort.SMARTEST, emptyMap())
        assertNull(smartestBestPickNumber(visible, BetweenSort.SMARTEST, emptyMap()))
    }

    @Test
    fun `no best pick unless smartest sort`() {
        val rows = listOf(row("x", arrMin = 600))
        val visible = applyBetweenView(rows, monday, null, BetweenSort.DEPARTURE, mapOf("x" to 10))
        assertNull(smartestBestPickNumber(visible, BetweenSort.DEPARTURE, mapOf("x" to 10)))
        assertNull(smartestBestPickNumber(emptyList(), BetweenSort.SMARTEST, mapOf("x" to 10)))
    }
}
