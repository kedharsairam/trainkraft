package com.trainkraft.app.presentation

import com.trainkraft.app.data.DelayPriorEntity
import com.trainkraft.app.data.FogOverlayEntity
import com.trainkraft.app.data.LiveStopDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [predictJourney] with hand-built stops (no fixtures).
 * Fixture-backed mechanism + regression checks live in PredictionEvalTest.
 */
class PredictionEngineTest {

    private fun stop(code: String, dist: Int = 0) = LiveStopDto(code = code, distance = dist)

    private fun prior(
        code: String,
        arr: Int = 0,
        dep: Int = 0,
    ) = DelayPriorEntity(
        trainNumber = "T",
        stationCode = code,
        arrAvgMin = arr,
        depAvgMin = dep,
        updatedAt = 0L,
    )

    private fun fog(
        action: String,
        from: String = "2026-12-01",
        to: String = "2027-02-15",
        season: String = "2026-27",
    ) = FogOverlayEntity(
        trainNumber = "T",
        action = action,
        fromDate = from,
        toDate = to,
        season = season,
    )

    // ------------------------------------------------------------- worked example

    @Test
    fun `worked example - BVI plus18 anchor recovers to 0 at MMCT via pattern`() {
        // 12952-direction order; real avg_delay_12952.json values (BVI dep
        // 00:18, MMCT arr On Time) reproduced as synthetic priors.
        val stops = listOf(stop("NDLS", 0), stop("ST", 1118), stop("BVI", 1351), stop("MMCT", 1380))
        val priors = listOf(prior("BVI", arr = 17, dep = 18), prior("MMCT", arr = 0)).associateBy { it.stationCode }
        val out = predictJourney(
            stops = stops,
            anchorIndex = 2,
            anchorDelayMin = 18,
            anchorAgeMin = 10,
            priors = priors,
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(1, out.predictions.size)
        val mmct = out.predictions[0]
        assertEquals("MMCT", mmct.stationCode)
        assertEquals(0, mmct.predictedDelayMin)
        assertEquals(PredictionBasis.PATTERN, mmct.basis)
    }

    // ------------------------------------------------------------- not started

    @Test
    fun `not started predicts priors with SCHEDULE basis and MED confidence`() {
        val stops = listOf(stop("A", 0), stop("B", 100), stop("C", 200))
        val priors = listOf(prior("A", arr = 2), prior("B", arr = 7, dep = 9)).associateBy { it.stationCode }
        val out = predictJourney(
            stops = stops,
            anchorIndex = null,
            anchorDelayMin = 0,
            anchorAgeMin = 0,
            priors = priors,
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(
            listOf(
                StopPrediction("A", 2, PredictionBasis.SCHEDULE, PredictionConfidence.MED),
                StopPrediction("B", 7, PredictionBasis.SCHEDULE, PredictionConfidence.MED),
                StopPrediction("C", 0, PredictionBasis.SCHEDULE, PredictionConfidence.MED),
            ),
            out.predictions,
        )
        assertNull(out.positionKm)
        assertNull(out.positionBetween)
        assertNull(out.serviceAlert)
        assertNull(out.seasonalNote)
    }

    @Test
    fun `not started with active fog drops confidence to LOW`() {
        val stops = listOf(stop("A", 0))
        val out = predictJourney(
            stops = stops,
            anchorIndex = null,
            anchorDelayMin = 0,
            anchorAgeMin = 0,
            priors = listOf(prior("A", arr = 5)).associateBy { it.stationCode },
            fog = fog("REVISED_TIMING"),
            todayYMD = "2027-01-10",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(PredictionConfidence.LOW, out.predictions[0].confidence)
        assertEquals("Fog timetable in effect — predictions less certain", out.seasonalNote)
    }

    // ------------------------------------------------------------- carried + clamp

    @Test
    fun `missing stop prior carries anchor and caps confidence at MED`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 12,
            anchorAgeMin = 5, // fresh + 1 ahead would be HIGH with a pattern
            priors = listOf(prior("A", dep = 12)).associateBy { it.stationCode },
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(
            listOf(StopPrediction("B", 12, PredictionBasis.CARRIED, PredictionConfidence.MED)),
            out.predictions,
        )
    }

    @Test
    fun `missing anchor prior carries anchor`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 9,
            anchorAgeMin = 5,
            priors = listOf(prior("B", arr = 3)).associateBy { it.stationCode },
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(PredictionBasis.CARRIED, out.predictions[0].basis)
        assertEquals(9, out.predictions[0].predictedDelayMin)
    }

    @Test
    fun `drift adjustment clamps to plus minus 45`() {
        val stops = listOf(stop("A", 0), stop("B", 100), stop("C", 200))
        val priors = listOf(
            prior("A", dep = 0),
            prior("B", arr = 200), // drift +200 -> +45
            prior("C", arr = 0), // drift 0, but anchor+drift floors at 0 anyway
        ).associateBy { it.stationCode }
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 10,
            anchorAgeMin = 5,
            priors = priors,
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(55, out.predictions[0].predictedDelayMin) // 10 + 45
        assertEquals(10, out.predictions[1].predictedDelayMin) // 10 + 0
    }

    @Test
    fun `prediction never goes negative`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        val priors = listOf(
            prior("A", dep = 30),
            prior("B", arr = 0), // drift -30 vs anchor 5 -> floor 0
        ).associateBy { it.stationCode }
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 5,
            anchorAgeMin = 5,
            priors = priors,
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(0, out.predictions[0].predictedDelayMin)
    }

    // ------------------------------------------------------------- confidence

    @Test
    fun `confidence ladder - fresh near HIGH, ageing MED, stale LOW`() {
        val stops = listOf(stop("A", 0), stop("B", 100), stop("C", 200), stop("D", 300))
        val priors = listOf(
            prior("A", dep = 5),
            prior("B", arr = 5),
            prior("C", arr = 5),
            prior("D", arr = 5),
        ).associateBy { it.stationCode }
        fun confAt(age: Long, anchor: Int, ahead: Int): PredictionConfidence {
            val out = predictJourney(stops, anchor, 5, age, priors, null, "2026-09-23", null, null)
            return out.predictions[ahead - 1].confidence
        }
        assertEquals(PredictionConfidence.HIGH, confAt(10, 0, 1))
        assertEquals(PredictionConfidence.HIGH, confAt(10, 0, 2))
        assertEquals(PredictionConfidence.MED, confAt(10, 0, 3)) // near rule needs <= 2 ahead
        assertEquals(PredictionConfidence.MED, confAt(15, 0, 1)) // 15 is not < 15
        assertEquals(PredictionConfidence.MED, confAt(59, 0, 1))
        assertEquals(PredictionConfidence.LOW, confAt(60, 0, 1))
    }

    @Test
    fun `revised timing fog caps running confidence at LOW`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        val priors = listOf(prior("A", dep = 5), prior("B", arr = 5)).associateBy { it.stationCode }
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 5,
            anchorAgeMin = 5,
            priors = priors,
            fog = fog("REVISED_TIMING"),
            todayYMD = "2027-01-10",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(PredictionConfidence.LOW, out.predictions[0].confidence)
        assertEquals("Fog timetable in effect — predictions less certain", out.seasonalNote)
        assertNull(out.serviceAlert)
    }

    // ------------------------------------------------------------- fog

    @Test
    fun `cancelled fog on running instance raises honest contradiction alert`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 5,
            anchorAgeMin = 5,
            priors = emptyMap(),
            fog = fog("CANCELLED"),
            todayYMD = "2027-01-10",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertEquals(
            "Listed as cancelled 2026-12-01–2027-02-15 under the 2026-27 fog program — verify before travel",
            out.serviceAlert,
        )
        assertNull(out.seasonalNote)
    }

    @Test
    fun `cancelled fog on not-started instance raises no alert`() {
        val out = predictJourney(
            stops = listOf(stop("A", 0)),
            anchorIndex = null,
            anchorDelayMin = 0,
            anchorAgeMin = 0,
            priors = emptyMap(),
            fog = fog("CANCELLED"),
            todayYMD = "2027-01-10",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertNull(out.serviceAlert)
        assertNull(out.seasonalNote)
    }

    @Test
    fun `reduced freq fog produces no output`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 5,
            anchorAgeMin = 5,
            priors = emptyMap(),
            fog = fog("REDUCED_FREQ"),
            todayYMD = "2027-01-10",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertNull(out.serviceAlert)
        assertNull(out.seasonalNote)
    }

    @Test
    fun `fog outside date window is inactive`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 5,
            anchorAgeMin = 5,
            priors = emptyMap(),
            fog = fog("CANCELLED", from = "2026-12-01", to = "2027-02-15"),
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertNull(out.serviceAlert)
        // Boundaries are inclusive.
        val edge = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 5,
            anchorAgeMin = 5,
            priors = emptyMap(),
            fog = fog("CANCELLED", from = "2026-12-01", to = "2027-02-15"),
            todayYMD = "2027-02-15",
            elapsedMin = null,
            schedSegMin = null,
        )
        assertTrue(edge.serviceAlert!!.startsWith("Listed as cancelled"))
    }

    // ------------------------------------------------------------- position

    @Test
    fun `position interpolates between anchor and next`() {
        val stops = listOf(stop("A", 0), stop("B", 100), stop("C", 300))
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 5,
            anchorAgeMin = 5,
            priors = emptyMap(),
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = 30,
            schedSegMin = 60,
        )
        assertEquals(50, out.positionKm)
        assertEquals("A" to "B", out.positionBetween)
    }

    @Test
    fun `position fraction never claims arrival pre-event`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        val out = predictJourney(
            stops = stops,
            anchorIndex = 0,
            anchorDelayMin = 0,
            anchorAgeMin = 0,
            priors = emptyMap(),
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = 10_000, // wildly overdue -> capped at 0.99
            schedSegMin = 60,
        )
        assertEquals(99, out.positionKm)
    }

    @Test
    fun `position nulls on any invalid input`() {
        val stops = listOf(stop("A", 0), stop("B", 100))
        // Null times.
        assertNull(
            predictJourney(stops, 0, 0, 0, emptyMap(), null, "2026-09-23", null, 60).positionKm,
        )
        assertNull(
            predictJourney(stops, 0, 0, 0, emptyMap(), null, "2026-09-23", 10, null).positionBetween,
        )
        // Non-positive segment.
        assertNull(
            predictJourney(stops, 0, 0, 0, emptyMap(), null, "2026-09-23", 10, 0).positionKm,
        )
        // No next stop (anchor is last).
        val last = predictJourney(stops, 1, 0, 0, emptyMap(), null, "2026-09-23", 10, 60)
        assertNull(last.positionKm)
        assertNull(last.positionBetween)
        assertTrue(last.predictions.isEmpty())
        // Blank codes.
        val blank = predictJourney(
            listOf(stop("", 0), stop("B", 100)), 0, 0, 0, emptyMap(), null, "2026-09-23", 10, 60,
        )
        assertNull(blank.positionKm)
        assertNull(blank.positionBetween)
    }

    // ------------------------------------------------------------- robustness

    @Test
    fun `weird inputs never throw`() {
        // Empty stops.
        val empty = predictJourney(emptyList(), 0, 5, 5, emptyMap(), null, "2026-09-23", 5, 10)
        assertTrue(empty.predictions.isEmpty())
        // Negative anchor -> not-started path (all stops predicted).
        val neg = predictJourney(
            listOf(stop("A", 0)), -3, 5, 5,
            listOf(prior("A", arr = 4)).associateBy { it.stationCode },
            null, "2026-09-23", null, null,
        )
        assertEquals(4, neg.predictions[0].predictedDelayMin)
        // Past-the-end anchor -> clamped to last (no future stops).
        val past = predictJourney(listOf(stop("A", 0)), 99, 5, 5, emptyMap(), null, "", null, null)
        assertTrue(past.predictions.isEmpty())
        // Negative delays/ages coerce.
        val coerced = predictJourney(
            listOf(stop("A", 0), stop("B", 100)), 0, -7, -9,
            emptyMap(), null, "2026-09-23", -4, 60,
        )
        assertEquals(0, coerced.predictions[0].predictedDelayMin)
        assertEquals(0, coerced.positionKm)
    }

    @Test
    fun `predictions cover future stops only in route order`() {
        val stops = listOf(stop("A", 0), stop("B", 100), stop("C", 200), stop("D", 300))
        val out = predictJourney(stops, 1, 3, 5, emptyMap(), null, "2026-09-23", null, null)
        assertEquals(listOf("C", "D"), out.predictions.map { it.stationCode })
    }
}
