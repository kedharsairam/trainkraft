package com.trainkraft.app.presentation

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent-B-UI: Phase B VM-derivation + display-rule helpers (pure, JVM).
 *
 * Covers every [TrainDetailViewModel]-side decision the engine contract
 * depends on: anchor age from server LTIME, scheduled segment from STD + DF,
 * the engine-wins display rule, basis vocabulary, engine clock math, priors
 * chips, the position-marker label (tilde mandatory), the pack-vintage
 * caption, and the IST date shape.
 */
class PredictionDisplayTest {

    private val ist = ZoneId.of("Asia/Kolkata")

    // ---- istTodayYMD ----

    @Test
    fun `istTodayYMD formats YYYY-MM-DD`() {
        assertEquals("2026-09-23", istTodayYMD(LocalDate.of(2026, 9, 23)))
    }

    // ---- anchorAgeMinutes (LTIME "23-Sep-2026 15:00", IST) ----

    @Test
    fun `anchor age is minutes from LTIME to now`() {
        // 15:00 IST → 16:26 IST = 86 min.
        val ltime = "23-Sep-2026 15:00"
        val now = java.time.LocalDateTime.of(2026, 9, 23, 16, 26)
            .atZone(ist).toInstant().toEpochMilli()
        assertEquals(86, anchorAgeMinutes(ltime, now))
    }

    @Test
    fun `anchor age accepts lowercase month`() {
        val now = java.time.LocalDateTime.of(2026, 9, 23, 15, 5)
            .atZone(ist).toInstant().toEpochMilli()
        assertEquals(5, anchorAgeMinutes("23-sep-2026 15:00", now))
    }

    @Test
    fun `anchor age null when LTIME blank or unparseable`() {
        val now = System.currentTimeMillis()
        assertNull(anchorAgeMinutes(null, now))
        assertNull(anchorAgeMinutes("", now))
        assertNull(anchorAgeMinutes("15:01 23-Sep", now))
        assertNull(anchorAgeMinutes("Departed from Gangineni", now))
    }

    @Test
    fun `anchor age clamps future skew to zero`() {
        val now = java.time.LocalDateTime.of(2026, 9, 23, 14, 0)
            .atZone(ist).toInstant().toEpochMilli()
        assertEquals(0, anchorAgeMinutes("23-Sep-2026 15:00", now))
    }

    // ---- schedSegmentMinutes ----

    @Test
    fun `segment from STD clocks plus DF flags`() {
        assertEquals(10, schedSegmentMinutes("11:25", 0, "11:35", 0))
    }

    @Test
    fun `segment honors overnight DF rollover`() {
        assertEquals(50, schedSegmentMinutes("23:40", 0, "00:30", 1))
    }

    @Test
    fun `segment null when either clock unparseable`() {
        assertNull(schedSegmentMinutes("Source", 0, "11:35", 0))
        assertNull(schedSegmentMinutes("11:25", 0, "Destination", 0))
        assertNull(schedSegmentMinutes(null, 0, "11:35", 0))
    }

    @Test
    fun `segment null when negative`() {
        assertNull(schedSegmentMinutes("11:35", 0, "11:25", 0))
    }

    // ---- shouldShowEnginePrediction ----

    private fun pred(delay: Int, conf: PredictionConfidence) = StopPrediction(
        stationCode = "BKL",
        predictedDelayMin = delay,
        basis = PredictionBasis.PATTERN,
        confidence = conf,
    )

    @Test
    fun `engine wins on MED-or-better with 2min diff`() {
        assertTrue(shouldShowEnginePrediction(pred(12, PredictionConfidence.MED), 10))
        assertTrue(shouldShowEnginePrediction(pred(12, PredictionConfidence.HIGH), 10))
    }

    @Test
    fun `LOW confidence never wins`() {
        assertFalse(shouldShowEnginePrediction(pred(40, PredictionConfidence.LOW), 10))
    }

    @Test
    fun `sub-2min diff keeps server rendering`() {
        assertFalse(shouldShowEnginePrediction(pred(11, PredictionConfidence.HIGH), 10))
        assertFalse(shouldShowEnginePrediction(pred(10, PredictionConfidence.HIGH), 10))
    }

    @Test
    fun `unknown server delay or missing prediction keeps server`() {
        assertFalse(shouldShowEnginePrediction(pred(12, PredictionConfidence.HIGH), null))
        assertFalse(shouldShowEnginePrediction(null, 10))
    }

    // ---- basisChipLabel (literal vocabulary) ----

    @Test
    fun `basis labels are literal`() {
        assertEquals("typical pattern", basisChipLabel(PredictionBasis.PATTERN))
        assertEquals("carried", basisChipLabel(PredictionBasis.CARRIED))
        assertEquals("timetable", basisChipLabel(PredictionBasis.SCHEDULE))
    }

    // ---- engineExpMinutes ----

    @Test
    fun `engine clock adds delay and wraps midnight`() {
        assertEquals(941, engineExpMinutes(929, 12)) // 15:29 + 12 = 15:41
        assertEquals(5, engineExpMinutes(1435, 10)) // 23:55 + 10 = 00:05
        assertEquals(0, engineExpMinutes(10, -10))
    }

    @Test
    fun `engine clock null without schedule base`() {
        assertNull(engineExpMinutes(null, 12))
    }

    // ---- priorChipLabel ----

    @Test
    fun `prior chip vocabulary`() {
        assertNull(priorChipLabel(null))
        assertEquals("typically on time here", priorChipLabel(0))
        assertEquals("typically +10 here", priorChipLabel(10))
    }

    // ---- positionMarkerLabel (tilde mandatory) ----

    @Test
    fun `position label carries tilde and leg`() {
        assertEquals(
            "~205 km · between MTMI and BKL",
            positionMarkerLabel(205, "MTMI" to "BKL"),
        )
    }

    @Test
    fun `position label without leg`() {
        assertEquals("~205 km", positionMarkerLabel(205, null))
        assertEquals("~205 km", positionMarkerLabel(205, "" to ""))
    }

    @Test
    fun `position label null without km`() {
        assertNull(positionMarkerLabel(null, "MTMI" to "BKL"))
    }

    // ---- packVintageCaption ----

    @Test
    fun `vintage caption from pipeline ISO`() {
        assertEquals(
            "delay data · Sep 2026",
            packVintageCaption("2026-09-23T12:34:56Z"),
        )
        assertEquals("delay data · Jan 2026", packVintageCaption("2026-01-05"))
    }

    @Test
    fun `vintage caption hidden when absent or bad`() {
        assertNull(packVintageCaption(null))
        assertNull(packVintageCaption(""))
        assertNull(packVintageCaption("not-a-date"))
        assertNull(packVintageCaption("2026-13"))
    }
}
