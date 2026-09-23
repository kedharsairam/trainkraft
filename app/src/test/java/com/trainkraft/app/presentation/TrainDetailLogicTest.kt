package com.trainkraft.app.presentation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainDetailLogicTest {

    // ---- headline priority: exceptions > arrived > not-started > running ----

    @Test
    fun `cancelled exception beats every other state`() {
        val h = resolveHeadline(
            exceptionMsg = "Train 12787 cancelled between BZA and WL",
            runState = 2,
            destLabel = "NAGARSOL",
            journeyDate = "23-Sep-2026",
            statusText = "Arrived",
            delayMin = 0,
        )
        assertEquals(Headline("Cancelled", HeadlineTone.DANGER), h)
    }

    @Test
    fun `diverted exception labels Diverted`() {
        val h = resolveHeadline(
            exceptionMsg = "Train diverted via KZJ",
            runState = 1,
            destLabel = "NAGARSOL",
            journeyDate = "23-Sep-2026",
            statusText = "Running",
            delayMin = 26,
        )
        assertEquals(Headline("Diverted", HeadlineTone.DANGER), h)
    }

    @Test
    fun `other active exception labels Disrupted`() {
        val h = resolveHeadline(
            exceptionMsg = "Train short terminated at KZJ",
            runState = 1,
            destLabel = "",
            journeyDate = "",
            statusText = "",
            delayMin = 0,
        )
        assertEquals(Headline("Disrupted", HeadlineTone.DANGER), h)
    }

    @Test
    fun `no-exception sentinel is not an exception`() {
        val h = resolveHeadline(
            exceptionMsg = "No Exceptional Details found for train 12952 !!!",
            runState = 1,
            destLabel = "",
            journeyDate = "",
            statusText = "Departed from UTRAN(URN) at 05:13 23-Sep",
            delayMin = 0,
        )
        assertEquals(HeadlineTone.LIVE, h.tone)
    }

    @Test
    fun `arrived headline carries destination and delay`() {
        assertEquals(
            Headline("Arrived NAGARSOL · +26m", HeadlineTone.NEUTRAL),
            resolveHeadline(null, 2, "NAGARSOL", "23-Sep-2026", "", 26),
        )
        assertEquals(
            Headline("Arrived NAGARSOL · On time", HeadlineTone.NEUTRAL),
            resolveHeadline(null, 2, "NAGARSOL", "23-Sep-2026", "", 0),
        )
    }

    @Test
    fun `not-started headline carries journey date`() {
        assertEquals(
            Headline("Starts 23-Sep-2026", HeadlineTone.NEUTRAL),
            resolveHeadline(null, 0, "", "23-Sep-2026", "Yet to start from its source", 0),
        )
        assertEquals(
            Headline("Yet to start", HeadlineTone.NEUTRAL),
            resolveHeadline(null, 0, "", "", "", 0),
        )
    }

    @Test
    fun `running late headline leads with delay minutes`() {
        assertEquals(
            Headline(
                "26 min late · Departed from GANGINENI(GNN) at 15:00 23-Sep",
                HeadlineTone.LATE,
            ),
            resolveHeadline(
                null, 1, "", "23-Sep-2026",
                "Departed from GANGINENI(GNN) at 15:00 23-Sep", 26,
            ),
        )
    }

    @Test
    fun `running on time headline is the status text`() {
        assertEquals(
            Headline("Departed from CHHOTI ODAI(COO) at 05:14 23-Sep", HeadlineTone.LIVE),
            resolveHeadline(
                null, 1, "", "23-Sep-2026",
                "Departed from CHHOTI ODAI(COO) at 05:14 23-Sep", 0,
            ),
        )
    }

    // ---- zone classifier ----

    @Test
    fun `mid-journey run zones past current future`() {
        // 12787 fixture shape: indices 0..6 departed, rest untouched.
        val arrived = List(22) { it <= 6 }
        val departed = List(22) { it <= 6 }
        assertEquals(6, currentStopIndex(arrived, departed))
        val zones = classifyStopZones(22, 6)
        assertEquals(22, zones.size)
        assertTrue(zones.take(6).all { it == StopZone.PAST })
        assertEquals(StopZone.CURRENT, zones[6])
        assertTrue(zones.drop(7).all { it == StopZone.FUTURE })
    }

    @Test
    fun `arrived-only stop is current and halted`() {
        val arrived = listOf(true, true, true)
        val departed = listOf(true, true, false)
        assertEquals(2, currentStopIndex(arrived, departed))
        assertTrue(isHaltedAt(arrived, departed, 2))
        assertEquals(
            listOf(StopZone.PAST, StopZone.PAST, StopZone.CURRENT),
            classifyStopZones(3, 2),
        )
    }

    @Test
    fun `pre-departure has no current and all future`() {
        val arrived = listOf(false, false)
        val departed = listOf(false, false)
        assertNull(currentStopIndex(arrived, departed))
        assertEquals(
            listOf(StopZone.FUTURE, StopZone.FUTURE),
            classifyStopZones(2, null),
        )
    }

    @Test
    fun `fully departed run ends on the last stop`() {
        val arrived = listOf(true, true)
        val departed = listOf(true, true)
        assertEquals(1, currentStopIndex(arrived, departed))
        assertEquals(
            listOf(StopZone.PAST, StopZone.CURRENT),
            classifyStopZones(2, 1),
        )
    }

    @Test
    fun `empty timeline classifies to empty`() {
        assertNull(currentStopIndex(emptyList(), emptyList()))
        assertTrue(classifyStopZones(0, null).isEmpty())
    }

    // ---- UA-flag display rule ----

    @Test
    fun `unavailable flags render dash never fake time`() {
        assertEquals("—", displayTimeOrDash(true, "15:29 23-Sep"))
        assertEquals("—", displayTimeOrDash(false, "**UA**"))
        assertEquals("—", displayTimeOrDash(false, ""))
        assertEquals("—", displayTimeOrDash(false, "   "))
    }

    @Test
    fun `usable times render verbatim with day suffix`() {
        assertEquals("15:29 23-Sep", displayTimeOrDash(false, "15:29 23-Sep"))
        assertEquals("Source", displayTimeOrDash(false, "Source"))
    }

    // ---- progress label ----

    @Test
    fun `fixture progress label matches ground truth`() {
        assertEquals("170 / 1079 km · 15%", progressLabel(170, 1079))
    }

    @Test
    fun `zero total hides the progress block`() {
        assertNull(progressLabel(0, 0))
        assertNull(progressLabel(50, 0))
        assertNull(progressLabel(50, -5))
    }

    // ---- instance capsule label ----

    @Test
    fun `capsule label is weekday plus day of month`() {
        // 21-Sep-2026 is a Monday.
        assertEquals("Mon 21", instanceCapsuleLabel("21-Sep-2026"))
        assertEquals("Wed 23", instanceCapsuleLabel("23-SEP-2026"))
    }

    @Test
    fun `capsule label never throws on garbage`() {
        assertEquals("tomorrow?", instanceCapsuleLabel("tomorrow?"))
        assertEquals("", instanceCapsuleLabel("   "))
    }

    // ---- server time short ----

    @Test
    fun `server LTIME shortens to clock time`() {
        assertEquals("15:00", serverTimeShort("23-Sep-2026 15:00"))
        assertEquals("15:01", serverTimeShort("15:01 23-Sep"))
        assertNull(serverTimeShort(""))
        assertNull(serverTimeShort(null))
    }

    // ---- selection + today helpers ----

    @Test
    fun `non-stop label pluralizes exactly at one`() {
        assertEquals("▸ 5 non-stop stations", nonStopLabel(5, expanded = false))
        assertEquals("▾ 5 non-stop stations", nonStopLabel(5, expanded = true))
        assertEquals("▸ 1 non-stop station", nonStopLabel(1, expanded = false))
        assertEquals("▾ 1 non-stop station", nonStopLabel(1, expanded = true))
        assertEquals("▸ 0 non-stop stations", nonStopLabel(0, expanded = false))
    }

    @Test
    fun `instance selection is case-insensitive with today fallback`() {
        assertTrue(isSelectedInstance("23-SEP-2026", "23-Sep-2026"))
        assertTrue(
            isSelectedInstance(
                "23-SEP-2026",
                ntesTodayLabel(LocalDate.of(2026, 9, 23)),
            ),
        )
        assertEquals("23-SEP-2026", ntesTodayLabel(LocalDate.of(2026, 9, 23)))
    }
}
