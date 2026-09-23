package com.trainkraft.app.presentation

import com.trainkraft.app.data.LiveStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase E: home living cards — live payload → card summary mapping plus the
 * TalkBack label (pure, JVM). The card shows the NTES status line, a delay
 * chip ([LiveStatusDto.delayMin]) and the journey progress % only when the
 * payload carries a total distance; anything missing falls back to the
 * number+name row.
 */
class TrackedLiveSummaryTest {

    private fun dto(
        status: String = "Train is running late by 35 mins",
        delayRaw: String = "35",
        coveredKm: Int = 170,
        totalKm: Int = 1079,
    ) = LiveStatusDto(
        statusText = status,
        delayRaw = delayRaw,
        distanceCoveredKm = coveredKm,
        totalDistance = totalKm,
    )

    @Test
    fun `maps status delay and progress`() {
        val summary = trackedLiveSummaryFrom(dto())
        assertEquals("Train is running late by 35 mins", summary?.statusText)
        assertEquals(35, summary?.delayMin)
        // 170 * 100 / 1079 = 15.
        assertEquals(15, summary?.progressPercent)
    }

    @Test
    fun `no total distance means no progress`() {
        val summary = trackedLiveSummaryFrom(dto(totalKm = 0))
        assertEquals("Train is running late by 35 mins", summary?.statusText)
        assertNull(summary?.progressPercent)
    }

    @Test
    fun `blank status yields no summary`() {
        assertNull(trackedLiveSummaryFrom(dto(status = "  ")))
        assertNull(trackedLiveSummaryFrom(dto(status = "")))
    }

    @Test
    fun `non-numeric delay reads as zero`() {
        assertEquals(0, trackedLiveSummaryFrom(dto(delayRaw = "?"))?.delayMin)
    }

    @Test
    fun `talkback reads status and delay when live`() {
        val row = TrackedRow(trainNumber = "12951", trainName = "Rajdhani")
        val live = TrackedLiveSummary(
            statusText = "Train is running late by 35 mins",
            delayMin = 35,
            progressPercent = 15,
        )
        assertEquals(
            "12951 Rajdhani, Train is running late by 35 mins, delay +35m",
            trackedCardDescription(row, live),
        )
    }

    @Test
    fun `talkback falls back to number and name`() {
        val row = TrackedRow(trainNumber = "12951", trainName = "Rajdhani")
        assertEquals("12951 Rajdhani", trackedCardDescription(row, null))
        val unnamed = TrackedRow(trainNumber = "12951", trainName = null)
        assertEquals("12951", trackedCardDescription(unnamed, null))
    }
}
