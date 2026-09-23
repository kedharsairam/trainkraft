package com.trainkraft.app.presentation

import com.trainkraft.app.data.AvgDelayDto
import com.trainkraft.app.data.DelayPriorEntity
import com.trainkraft.app.data.LiveStatusDto
import com.trainkraft.app.data.NtesFormats
import com.trainkraft.app.data.NtesJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eval harness for [predictJourney] on the captured production fixtures.
 *
 * Honest scope statement: the fixtures cover exactly 2 trains (12951
 * MMCT→NDLS mid-journey with per-stop actuals; 12952 NDLS→MMCT yet-to-start
 * plus its real `avg_delay_12952.json` priors). That is enough to guard the
 * recovery mechanism, the server-agreement bound on near stops, the
 * not-started path and determinism — i.e. mechanism checks + regression
 * trips. It is NOT a representative sample: true MAE gates with pass/fail
 * thresholds need accumulated snapshots across trains/dates (Phase F work).
 * Nothing below claims statistical significance; no fake rigor.
 */
class PredictionEvalTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "fixture $name not on classpath"
        }.bufferedReader().readText()

    /**
     * SYNTHETIC priors (hand-built in-test from this fixture's own DARR/DDEP
     * actuals — they stand in for pipeline priors, which no 12951 fixture
     * ships): 12951 anchored at ST (actual dep +5) must predict recovery at
     * NDLS (actual arr 0) while the naive carry-anchor baseline stays +5.
     *
     * Note on the brief's parenthetical "(BVI dep +15, MMCT arr 0)": those
     * are 12952-direction stations — on this 12951 run MMCT is the source
     * and BVI is already behind the ST anchor, so they cannot be future
     * predictions here. The checkable recovery pair on 12951 is ST(+5) →
     * NDLS(0); the BVI(+18) → MMCT(0) direction is covered with the real
     * 12952 prior values as the synthetic worked-example unit test in
     * PredictionEngineTest.
     */
    @Test
    fun `SYNTHETIC priors - 12951 anchored at ST predicts NDLS recovery and beats carry baseline`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12951_22sep.json"))
        val stops = dto.stops
        val anchor = stops.indexOfFirst { it.code == "ST" }
        assertTrue("ST must exist in 12951 fixture", anchor >= 0)

        // Anchor delay from the fixture's own actual departure delay.
        val anchorDelay = stops[anchor].departureDelayMinutes() ?: 0
        assertEquals(5, anchorDelay) // ST DDEP "00:05" — guards fixture drift.

        // SYNTHETIC priors mirroring this run's DARR/DDEP actuals.
        val priors = stops.associate { s ->
            s.code to DelayPriorEntity(
                trainNumber = "12951",
                stationCode = s.code,
                arrAvgMin = s.arrivalDelayMinutes() ?: 0,
                depAvgMin = s.departureDelayMinutes() ?: 0,
                updatedAt = 0L,
            )
        }

        val out = predictJourney(
            stops = stops,
            anchorIndex = anchor,
            anchorDelayMin = anchorDelay,
            anchorAgeMin = 10,
            priors = priors,
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )

        val ndls = out.predictions.first { it.stationCode == "NDLS" }
        val serverActual = stops.first { it.code == "NDLS" }.arrivalDelayMinutes() ?: 0
        assertEquals(0, serverActual) // NDLS DARR "On Time" — guards fixture drift.

        // Naive baseline: carry the anchor delay flat to the destination.
        val baselinePred = anchorDelay
        val engineErr = kotlin.math.abs(ndls.predictedDelayMin - serverActual)
        val baselineErr = kotlin.math.abs(baselinePred - serverActual)
        println(
            "12951 ST-anchor eval: engine NDLS pred=${ndls.predictedDelayMin} " +
                "(basis=${ndls.basis}) vs baseline pred=$baselinePred " +
                "vs server actual=$serverActual " +
                "(engineErr=$engineErr, baselineErr=$baselineErr)",
        )

        assertTrue("engine must predict recovery at NDLS (pred<=2)", ndls.predictedDelayMin <= 2)
        assertEquals(5, baselinePred)
        assertTrue(
            "engine (err=$engineErr) must beat baseline (err=$baselineErr)",
            engineErr < baselineErr,
        )
    }

    /**
     * Server-agreement bound on the same 12951 run: for HIGH-confidence near
     * stops (fresh anchor, ≤ 2 ahead) the engine must stay within 20 min of
     * the server's own actuals. Server short-term ETAs are good — a wild
     * deviation here means an engine bug, not a hard prediction.
     */
    @Test
    fun `12951 HIGH-confidence near stops agree with server actuals within 20 min`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12951_22sep.json"))
        val stops = dto.stops
        val anchor = stops.indexOfFirst { it.code == "ST" }
        val anchorDelay = stops[anchor].departureDelayMinutes() ?: 0
        val priors = stops.associate { s ->
            s.code to DelayPriorEntity(
                trainNumber = "12951",
                stationCode = s.code,
                arrAvgMin = s.arrivalDelayMinutes() ?: 0,
                depAvgMin = s.departureDelayMinutes() ?: 0,
                updatedAt = 0L,
            )
        }
        val out = predictJourney(
            stops = stops,
            anchorIndex = anchor,
            anchorDelayMin = anchorDelay,
            anchorAgeMin = 10,
            priors = priors,
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )
        // Near stops: BRC (1 ahead), RTM (2 ahead) — both HIGH at age 10.
        for (code in listOf("BRC", "RTM")) {
            val pred = out.predictions.first { it.stationCode == code }
            val actual = stops.first { it.code == code }.arrivalDelayMinutes() ?: 0
            println("12951 agreement: $code engine=${pred.predictedDelayMin} server=$actual conf=${pred.confidence}")
            assertEquals(PredictionConfidence.HIGH, pred.confidence)
            assertTrue(
                "$code |engine - server| must be <= 20 (engine=${pred.predictedDelayMin}, server=$actual)",
                kotlin.math.abs(pred.predictedDelayMin - actual) <= 20,
            )
        }
    }

    /**
     * Not-started case: 12952 (TRUNST=0) + REAL `avg_delay_12952.json` priors
     * parsed in-test — predictions must equal the priors (basis SCHEDULE),
     * deterministic. Spot-checks (KOTA arr 13, BVI arr 17, MMCT/NDLS 0)
     * guard fixture drift.
     */
    @Test
    fun `12952 not started predicts REAL avg-delay priors with SCHEDULE basis`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12952_23sep.json"))
        val stops = dto.stops
        // Pre-departure: nothing reached -> anchor null via the shared helper.
        val anchor = currentStopIndex(stops.map { it.arrived }, stops.map { it.departed })
        assertNull(anchor)

        val avg = NtesJson.decode<AvgDelayDto>(fixture("avg_delay_12952.json"))
        val priors = avg.stops.associate { s ->
            s.code to DelayPriorEntity(
                trainNumber = "12952",
                stationCode = s.code,
                arrAvgMin = NtesFormats.delayToMinutes(s.arrivalDelay) ?: 0,
                depAvgMin = NtesFormats.delayToMinutes(s.departureDelay) ?: 0,
                updatedAt = 0L,
            )
        }

        val out = predictJourney(
            stops = stops,
            anchorIndex = anchor,
            anchorDelayMin = 0,
            anchorAgeMin = 0,
            priors = priors,
            fog = null,
            todayYMD = "2026-09-23",
            elapsedMin = null,
            schedSegMin = null,
        )

        assertEquals(stops.map { it.code }, out.predictions.map { it.stationCode })
        out.predictions.forEach { p ->
            assertEquals("pred for ${p.stationCode} must equal its prior", priors[p.stationCode]?.arrAvgMin ?: 0, p.predictedDelayMin)
            assertEquals(PredictionBasis.SCHEDULE, p.basis)
            assertEquals(PredictionConfidence.MED, p.confidence)
        }
        assertEquals(13, out.predictions.first { it.stationCode == "KOTA" }.predictedDelayMin)
        assertEquals(17, out.predictions.first { it.stationCode == "BVI" }.predictedDelayMin)
        assertEquals(0, out.predictions.first { it.stationCode == "MMCT" }.predictedDelayMin)
        assertEquals(0, out.predictions.first { it.stationCode == "NDLS" }.predictedDelayMin)

        // Determinism: same inputs -> same outputs.
        val again = predictJourney(stops, anchor, 0, 0, priors, null, "2026-09-23", null, null)
        assertEquals(out, again)
    }

    /** Determinism on the running path (12951 ST anchor, synthetic priors). */
    @Test
    fun `running predictions are deterministic`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12951_22sep.json"))
        val stops = dto.stops
        val anchor = stops.indexOfFirst { it.code == "ST" }
        val anchorDelay = stops[anchor].departureDelayMinutes() ?: 0
        val priors = stops.associate { s ->
            s.code to DelayPriorEntity(
                trainNumber = "12951",
                stationCode = s.code,
                arrAvgMin = s.arrivalDelayMinutes() ?: 0,
                depAvgMin = s.departureDelayMinutes() ?: 0,
                updatedAt = 0L,
            )
        }
        val first = predictJourney(stops, anchor, anchorDelay, 10, priors, null, "2026-09-23", 20, 45)
        val second = predictJourney(stops, anchor, anchorDelay, 10, priors, null, "2026-09-23", 20, 45)
        assertEquals(first, second)
    }
}
