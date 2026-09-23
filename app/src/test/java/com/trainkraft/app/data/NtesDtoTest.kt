package com.trainkraft.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the captured production fixtures (`src/test/resources/fixtures/`,
 * captured 2026-09-23) with the strict DTOs. If NTES changes a schema, these
 * fail before users ever see a broken screen.
 */
class NtesDtoTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "fixture $name not on classpath"
        }.bufferedReader().readText()

    // ---------------------------------------------------------------- live

    @Test
    fun `live status en-route parses position, stops and flags`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12951_22sep.json"))
        assertEquals("12951", dto.trainNumber)
        assertEquals(0, dto.delayMin)
        assertTrue(dto.statusText.startsWith("Departed from"))
        assertFalse(dto.arrivedAtDest)
        assertEquals(1, dto.runState)
        assertEquals("MMCT", dto.sourceCode)
        assertEquals("NEW DELHI", dto.destName)
        assertEquals(8, dto.stops.size)
        assertEquals("MMCT", dto.stops[0].code)
        // Train between MMCT and BVI: next unreached main stop is BVI.
        assertEquals("BVI", dto.nextUnreachedStop()?.code)
        // En-route: source stop carries the full coach composition.
        assertTrue(dto.stops[0].departureCoachPosition.startsWith("ENG"))
        // KOTA stop carries platform 1.
        assertEquals("1", dto.stops.first { it.code == "KOTA" }.platform)
    }

    @Test
    fun `live status yet-to-start parses with full stop list`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12952_23sep.json"))
        assertEquals("12952", dto.trainNumber)
        assertEquals("Yet to start from its source", dto.statusText)
        assertEquals(0, dto.runState)
        assertFalse(dto.arrivedAtDest)
        assertEquals("NDLS", dto.stops[0].code)
        assertEquals("NDLS", dto.nextUnreachedStop()?.code)
        assertTrue(dto.showCoachPosition)
        // Not yet started: coach composition isn't assigned yet.
        assertEquals("", dto.stops[0].departureCoachPosition)
    }

    // ------------------------------------------------------------ avg delay

    @Test
    fun `avg delay parses per-station values`() {
        val dto = NtesJson.decode<AvgDelayDto>(fixture("avg_delay_12952.json"))
        assertEquals("12952", dto.trainNumber)
        assertEquals(8, dto.stops.size)
        val kota = dto.stops.first { it.code == "KOTA" }
        assertEquals(13, NtesFormats.delayToMinutes(kota.arrivalDelay))
        assertEquals(12, NtesFormats.delayToMinutes(kota.departureDelay))
        // Origin: empty arr, "On Time " (trailing space!) dep.
        val ndls = dto.stops.first { it.code == "NDLS" }
        assertNull(NtesFormats.delayToMinutes(ndls.arrivalDelay))
        assertEquals(0, NtesFormats.delayToMinutes(ndls.departureDelay))
    }

    // -------------------------------------------------------- station board

    @Test
    fun `station live board parses delays, platform and departure rows`() {
        val dto = NtesJson.decode<StationLiveDto>(fixture("station_live_NDLS.json"))
        assertEquals("NDLS", dto.stationCode)
        // The board is a live time-window: exact counts drift between captures
        // (69 rows at 05:10, 71 at 05:17), but header and array stay in sync.
        assertEquals(dto.totalTrains, dto.trains.size)
        assertTrue("board should list many trains", dto.trains.size >= 60)

        val punjab = dto.trains.first { it.trainNumber == "12138" }
        assertEquals("05:10 23-Sep", punjab.scheduledDeparture)
        assertEquals(18, punjab.delayDepMinutes())
        assertEquals(28, punjab.delayArrMinutes())
        assertEquals("3", punjab.platform)
        assertTrue(punjab.departsFromHere())
        assertEquals(0, punjab.cancelled)

        // Terminating rows (STD="DSTN") are arrivals, not departures.
        val terminating = dto.trains.first { it.scheduledDeparture == "DSTN" }
        assertFalse(terminating.departsFromHere())

        // "RT" = right time.
        val rt = dto.trains.first { it.departureDelay == "RT" }
        assertEquals(0, rt.delayDepMinutes())
    }

    // -------------------------------------------------------------- between

    @Test
    fun `trains between parses corridor rows incl regional resolution`() {
        val dto = NtesJson.decode<BetweenTrainsDto>(fixture("trains_between_NDLS_MMCT.json"))
        assertEquals("NDLS", dto.fromCode)
        assertEquals("MMCT", dto.toCode)
        assertEquals(32, dto.totalTrains)

        val raj = dto.trains.first { it.trainNumber == "12952" }
        assertEquals("NDLS", raj.boardCode)
        assertEquals("MMCT", raj.alightCode)
        assertEquals(16 * 60 + 55, raj.depMinutes())
        assertEquals(8 * 60 + 35, raj.arrMinutes())

        // NTES regionally resolves both cities: non-NDLS Delhi boards appear.
        assertTrue(dto.trains.any { it.boardCode == "NZM" })
    }

    // ------------------------------------------------------------- schedule

    @Test
    fun `schedule parses official route with day offsets`() {
        val dto = NtesJson.decode<TrainScheduleDto>(fixture("schedule_12952.json"))
        assertEquals("12952", dto.trainNumber)
        assertEquals("15:40", dto.travelTime)
        assertEquals(76, dto.availableDates.size)
        assertEquals(8, dto.stations.size)

        val origin = dto.stations.first()
        assertEquals("NDLS", origin.code)
        assertEquals(16 * 60 + 55, origin.depMinutes())
        assertNull(origin.arrMinutes()) // "" at source
        assertEquals(0, origin.dayOffset)

        val dest = dto.stations.last()
        assertEquals("MMCT", dest.code)
        assertEquals(8 * 60 + 35, dest.arrMinutes())
        assertNull(dest.depMinutes()) // "" at destination
        assertEquals(1, dest.dayOffset) // Day=2
        assertEquals("1380", dest.distance)
    }

    // ----------------------------------------------------------- find train

    @Test
    fun `find train parses results`() {
        val dto = NtesJson.decode<FindTrainDto>(fixture("find_train_12952.json"))
        assertEquals(1, dto.trains.size)
        val t = dto.trains[0]
        assertEquals("12952", t.trainNumber)
        assertEquals("NDLS", t.sourceCode)
        assertEquals("MMCT", t.destCode)
        assertEquals("RAJ", t.trainType)
    }

    // ------------------------------------------------------------ instance

    @Test
    fun `train instance parses recent runs`() {
        val dto = NtesJson.decode<TrainInstanceDto>(fixture("instance_12952.json"))
        assertEquals("12952", dto.trainNumber)
        assertEquals(6, dto.instances.size)
        assertEquals(0, dto.instances[0].runState) // yet to start (tomorrow)
        assertTrue(dto.instances.any { it.runState == 2 }) // completed runs
        assertTrue(dto.instances.any { it.runState == 1 }) // running run
        assertNotNull(dto.instances.first { it.runState == 1 }.position)
    }

    // -------------------------------------------------------------- formats

    @Test
    fun `live status 12787 mid-journey parses per-stop delays, wtt and reversal`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12787_23sep.json"))
        assertEquals("12787", dto.trainNumber)
        assertEquals(1079, dto.totalDistance)
        assertEquals(1, dto.runState)
        assertEquals("DR", dto.lastEventCode)
        assertTrue("LDSRC=${dto.distanceCoveredKm}", dto.distanceCoveredKm in 100..1079)
        assertTrue("progress=${dto.progressPercent()}", dto.progressPercent() in 9..20)

        val bza = dto.stops.first { it.code == "BZA" }
        assertTrue(bza.isReversalStop())
        assertEquals("5", bza.platform)
        assertEquals(139, bza.distance)
        assertEquals(26, bza.arrivalDelayMinutes())
        assertEquals(27, bza.departureDelayMinutes())

        // No other stop is a reversal.
        assertTrue(dto.stops.filter { it.code != "BZA" }.none { it.isReversalStop() })

        assertEquals(5, dto.stops.first { it.code == "PKO" }.nonStopCount())
        assertEquals(10, dto.stops.first { it.code == "GDV" }.nonStopCount())

        // Destination: arrival usable, no departure (DDEP "" → null).
        val nsl = dto.stops.last()
        assertEquals("NSL", nsl.code)
        assertEquals("", nsl.departureDelay)
        assertNull(nsl.departureDelayMinutes())
    }

    @Test
    fun `live status 12951 parses per-stop delays, wtt and ua flags`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12951_22sep.json"))

        val st = dto.stops.first { it.code == "ST" }
        assertEquals(4, st.arrivalDelayMinutes())
        assertEquals(5, st.departureDelayMinutes())
        assertEquals(23, st.nonStopCount())
        assertFalse(st.arrivalUnavailable())
        assertFalse(st.departureUnavailable())

        // Destination arrived "On Time" → 0 minutes.
        val ndls = dto.stops.first { it.code == "NDLS" }
        assertEquals("On Time", ndls.arrivalDelay)
        assertEquals(0, ndls.arrivalDelayMinutes())

        // Future stop with ETA/ETD unavailable (UA flags = 1).
        val bvi = dto.stops.first { it.code == "BVI" }
        assertTrue(bvi.arrivalUnavailable())
        assertTrue(bvi.departureUnavailable())

        // Unreached destination row carries no UA flags → defaults 0.
        assertFalse(ndls.arrivalUnavailable())
        assertFalse(ndls.departureUnavailable())
    }

    @Test
    fun `wtt sub-entry parses static timetable identity`() {
        val dto = NtesJson.decode<LiveStatusDto>(fixture("live_status_12951_22sep.json"))
        val first = dto.stops.first { it.code == "ST" }.nonStoppingStations.first()
        assertEquals("URN", first.code)
        assertEquals(266, first.distance)
        assertEquals("20:01", first.scheduledArrival)
        assertEquals("UTRAN", first.name)
    }

    @Test
    fun `progress percent is zero without a total distance`() {
        assertEquals(0, LiveStatusDto(totalDistance = 0, distanceCoveredKm = 50).progressPercent())
        assertEquals(15, LiveStatusDto(totalDistance = 1079, distanceCoveredKm = 170).progressPercent())
    }

    @Test
    fun `train exceptions parses no-exception sentinel`() {
        val dto = NtesJson.decode<TrainExcpDto>(fixture("exceptions_12952.json"))
        assertEquals("No Exceptional Details found for train 12952 !!!", dto.alertMsg)
        assertFalse(dto.hasActiveException())
        // Helper semantics on unit-constructed payloads.
        assertFalse(TrainExcpDto().hasActiveException())
        assertTrue(TrainExcpDto("Train Diverted via alternate route").hasActiveException())
    }

    @Test
    fun `formats handle per-stop delay sentinels`() {
        assertEquals(0, NtesFormats.delayToMinutes("On Time"))
        assertEquals(6, NtesFormats.delayToMinutes("00:06"))
        assertNull(NtesFormats.delayToMinutes(""))
        assertEquals(0, NtesFormats.delayToMinutes("RT"))
    }

    @Test
    fun `formats handle every observed sentinel`() {
        assertEquals(0, NtesFormats.delayToMinutes("RT"))
        assertEquals(0, NtesFormats.delayToMinutes("On Time "))
        assertNull(NtesFormats.delayToMinutes(""))
        assertNull(NtesFormats.delayToMinutes(null))
        assertEquals(73, NtesFormats.delayToMinutes("01:13"))

        assertEquals(310, NtesFormats.timeToMinutes("05:10 23-Sep"))
        assertEquals(16 * 60 + 55, NtesFormats.timeToMinutes("16:55"))
        assertNull(NtesFormats.timeToMinutes("Source"))
        assertNull(NtesFormats.timeToMinutes("Destination"))
        assertNull(NtesFormats.timeToMinutes("DSTN"))
        assertNull(NtesFormats.timeToMinutes("SRC"))
        assertNull(NtesFormats.timeToMinutes("**UA**"))
        assertNull(NtesFormats.timeToMinutes(""))
        assertNull(NtesFormats.hhmmToMinutes("24:00"))
        assertNull(NtesFormats.hhmmToMinutes("12:61"))
    }
}
