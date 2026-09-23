package com.trainkraft.app.presentation

import com.trainkraft.app.data.DelayPriorEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase E: station-board usually-badges — pack row → station mapping
 * (pure, JVM). The label itself is shared with between
 * ([usualDelayBadgeLabel]); this covers the per-station lookup.
 */
class StationBoardBadgeTest {

    private fun prior(
        train: String,
        station: String,
        arrAvgMin: Int,
    ) = DelayPriorEntity(
        trainNumber = train,
        stationCode = station,
        arrAvgMin = arrAvgMin,
        updatedAt = 0L,
    )

    @Test
    fun `matching station returns its arrival prior`() {
        val priors = listOf(
            prior("12951", "NDLS", 4),
            prior("12951", "BRC", 25),
        )
        assertEquals(25, usualDelayForStation(priors, "BRC"))
    }

    @Test
    fun `station match is case-insensitive`() {
        val priors = listOf(prior("12951", "brc", 10))
        assertEquals(10, usualDelayForStation(priors, "BRC"))
    }

    @Test
    fun `no row for station yields no badge`() {
        val priors = listOf(prior("12951", "NDLS", 4))
        assertNull(usualDelayForStation(priors, "BRC"))
        assertNull(usualDelayForStation(emptyList(), "BRC"))
    }
}
