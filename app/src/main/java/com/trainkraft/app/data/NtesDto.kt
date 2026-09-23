package com.trainkraft.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Strict NTES response DTOs — built from captured production fixtures
 * (`app/src/test/resources/fixtures/`, captured 2026-09-23 by the
 * env-gated NtesFixtureCaptureTest) and validated against them in
 * NtesDtoTest.
 *
 * Rules:
 *  - Unknown keys are ignored (the server sends many more fields than we use).
 *  - Types are exact per the captured schema; a type/shape drift surfaces as a
 *    SerializationException which [NtesRepository] turns into a cache fallback
 *    or an explicit failure — never a silently wrong screen.
 *  - Optional fields default, so a row-level missing key doesn't sink the parse.
 */
object NtesJson {
    val json = Json {
        ignoreUnknownKeys = true
        // Tolerates quoted-number quirks; strictness lives in the typed fields.
        isLenient = true
    }

    inline fun <reified T> decode(raw: String): T = json.decodeFromString(raw)
}

/** Parsing helpers for NTES' heterogeneous time/delay strings. */
object NtesFormats {

    /**
     * Delay value → minutes: `"RT"`/`"On Time"` → 0, `"HH:MM"` → minutes,
     * blank/unknown → null.
     */
    fun delayToMinutes(raw: String?): Int? {
        val v = raw?.trim().orEmpty()
        return when {
            v.isEmpty() -> null
            v.equals("RT", true) -> 0
            v.contains("on time", ignoreCase = true) -> 0
            else -> hhmmToMinutes(v)
        }
    }

    /** `"05:10"` / `"15:40"` → minutes-of-day; anything else → null. */
    fun hhmmToMinutes(raw: String?): Int? {
        val v = raw?.trim().orEmpty()
        val m = Regex("(\\d{1,2}):(\\d{2})").find(v) ?: return null
        val (h, min) = m.destructured
        val hh = h.toInt()
        val mm = m.groupValues[2].toInt()
        return if (hh < 24 && mm < 60) hh * 60 + mm else null
    }

    /**
     * Timestamp → minutes-of-day: `"05:10 23-Sep"`, `"21:30"` → minutes;
     * sentinels (`Source`, `Destination`, `SRC`, `DSTN`, `**UA**`, "") → null.
     */
    fun timeToMinutes(raw: String?): Int? = hhmmToMinutes(raw)
}

// ---------------------------------------------------------------- live status

/** ShowFullRunJson — one train's live running status. */
@Serializable
data class LiveStatusDto(
    @SerialName("TN") val trainNumber: String = "",
    @SerialName("TNM") val trainName: String = "",
    @SerialName("CPOS") val statusText: String = "",
    @SerialName("LASTUPD") val lastUpdate: String = "",
    @SerialName("LUPDFULL") val lastUpdateFull: String = "",
    @SerialName("LTIME") val lastUpdateTime: String = "",
    @SerialName("LUPDT") val lastUpdateShort: String = "",
    @SerialName("LDEL") val delayRaw: String = "0",
    @SerialName("TRUNST") val runState: Int = 0,
    @SerialName("STD") val journeyDate: String = "",
    @SerialName("SRC") val sourceCode: String = "",
    @SerialName("SRCN") val sourceName: String = "",
    @SerialName("DSTN") val destCode: String = "",
    @SerialName("DSTNN") val destName: String = "",
    @SerialName("LSTN") val lastStationCode: String = "",
    @SerialName("LSTNN") val lastStationName: String = "",
    @SerialName("NSTN") val nextStationCode: String = "",
    @SerialName("NSTNN") val nextStationName: String = "",
    @SerialName("LSTNRT") val lastStationOnTime: Boolean = false,
    @SerialName("isArrDSTN") val arrivedAtDest: Boolean = false,
    @SerialName("showCoachPositionFlag") val showCoachPosition: Boolean = false,
    @SerialName("coachPositionFlag") val coachPositionAvailable: Boolean = false,
    @SerialName("TTLDIST") val totalDistance: Int = 0,
    @SerialName("SchType") val scheduleType: String = "",
    @SerialName("STNS") val stops: List<LiveStopDto> = emptyList(),
    @SerialName("AlertMsg") val alertMsg: String = "",
) {
    /** Delay in minutes (server sends `"0"`/`"35"` as a string). */
    val delayMin: Int get() = delayRaw.toIntOrNull() ?: 0

    /**
     * Next stop the train hasn't reached — the one whose platform matters now.
     */
    fun nextUnreachedStop(): LiveStopDto? = stops.firstOrNull { !it.arrived && !it.departed }
}

/** One row of LiveStatusDto.stops (a main route stop). */
@Serializable
data class LiveStopDto(
    @SerialName("SC") val code: String = "",
    @SerialName("SN") val name: String = "",
    @SerialName("SHN") val nameHindi: String = "",
    @SerialName("STA") val scheduledArrival: String = "",
    @SerialName("STD") val scheduledDeparture: String = "",
    @SerialName("ETA") val estArrival: String = "",
    @SerialName("ETD") val estDeparture: String = "",
    @SerialName("PF") val platform: String = "",
    @SerialName("DIST") val distance: Int = 0,
    @SerialName("DF") val dayFlag: Int = 0,
    @SerialName("Sr") val seq: Int = 0,
    @SerialName("ISA") val arrived: Boolean = false,
    @SerialName("ISD") val departed: Boolean = false,
    @SerialName("arrivalCoachPosition") val arrivalCoachPosition: String = "",
    @SerialName("departureCoachPosition") val departureCoachPosition: String = "",
    @SerialName("arrivalCoachClass") val arrivalCoachClass: String = "",
    @SerialName("departureCoachClass") val departureCoachClass: String = "",
) {
    /** First non-blank coach composition at this stop (arrival preferred). */
    fun coachComposition(): String =
        arrivalCoachPosition.ifBlank { departureCoachPosition }
}

// ----------------------------------------------------------------- avg delay

/** GetAvgDelayJson — historical average delays per station. */
@Serializable
data class AvgDelayDto(
    @SerialName("TrainName") val trainName: String = "",
    @SerialName("TrainNo") val trainNumber: String = "",
    @SerialName("Src") val sourceCode: String = "",
    @SerialName("SrcName") val sourceName: String = "",
    @SerialName("Dstn") val destCode: String = "",
    @SerialName("DstnName") val destName: String = "",
    @SerialName("TypeName") val typeDesc: String = "",
    @SerialName("DaysOfRun") val daysOfRun: String = "",
    @SerialName("vAvgDelayList") val stops: List<AvgDelayStopDto> = emptyList(),
)

/** One station's average arrival/departure delay ("HH:MM", "On Time", ""). */
@Serializable
data class AvgDelayStopDto(
    @SerialName("stn") val code: String = "",
    @SerialName("stnName") val name: String = "",
    @SerialName("stnArrDelay") val arrivalDelay: String = "",
    @SerialName("stnDepDelay") val departureDelay: String = "",
    @SerialName("sr") val seq: Int = 0,
)

// ---------------------------------------------------------- station live board

/** TrainsAtStationJson — live board for one station. */
@Serializable
data class StationLiveDto(
    @SerialName("Station") val stationCode: String = "",
    @SerialName("StationName") val stationName: String = "",
    @SerialName("NextHr") val nextHours: String = "",
    @SerialName("TotalTrains") val totalTrains: Int = 0,
    @SerialName("TrainsAtStation") val trains: List<StationLiveTrainDto> = emptyList(),
)

/** One train on the live station board. */
@Serializable
data class StationLiveTrainDto(
    @SerialName("TrainNumber") val trainNumber: String = "",
    @SerialName("TrainName") val trainName: String = "",
    @SerialName("TrainType") val trainType: String = "",
    @SerialName("TrainTypeDesc") val typeDesc: String = "",
    @SerialName("Source") val sourceCode: String = "",
    @SerialName("SourceName") val sourceName: String = "",
    @SerialName("Destination") val destCode: String = "",
    @SerialName("DestinationName") val destName: String = "",
    @SerialName("STA") val scheduledArrival: String = "",
    @SerialName("STD") val scheduledDeparture: String = "",
    @SerialName("ETA") val estArrival: String = "",
    @SerialName("ETD") val estDeparture: String = "",
    /** "RT" | "HH:MM" | "" */
    @SerialName("DelayArr") val arrivalDelay: String = "",
    /** "RT" | "HH:MM" | "" */
    @SerialName("DelayDep") val departureDelay: String = "",
    @SerialName("Platform") val platform: String = "",
    @SerialName("Cancel") val cancelled: Int = 0,
    @SerialName("Diverted") val diverted: Int = 0,
    @SerialName("ArrCancelFlag") val arrCancelled: Int = 0,
    @SerialName("DepCancelFlag") val depCancelled: Int = 0,
    @SerialName("StartDate") val startDate: String = "",
) {
    /** True when STD is an actual departure ("HH:MM …"), not a DSTN/SRC marker. */
    fun departsFromHere(): Boolean = NtesFormats.timeToMinutes(scheduledDeparture) != null
    fun delayDepMinutes(): Int? = NtesFormats.delayToMinutes(departureDelay)
    fun delayArrMinutes(): Int? = NtesFormats.delayToMinutes(arrivalDelay)
}

// ------------------------------------------------------ trains between stns

/** TrainBtwStnJson — trains between two stations (NTES resolves both cities
 *  regionally: a NDLS→MMCT query also returns NZM→BDTS etc.). */
@Serializable
data class BetweenTrainsDto(
    @SerialName("StationFromCode") val fromCode: String = "",
    @SerialName("StationFrom") val fromName: String = "",
    @SerialName("StationToCode") val toCode: String = "",
    @SerialName("StationTo") val toName: String = "",
    @SerialName("TrainType") val trainType: String = "",
    @SerialName("TotalTrains") val totalTrains: Int = 0,
    @SerialName("Trains") val trains: List<BetweenTrainDto> = emptyList(),
)

/** One train between the queried stations. */
@Serializable
data class BetweenTrainDto(
    @SerialName("TrainNumber") val trainNumber: String = "",
    @SerialName("TrainName") val trainName: String = "",
    /** Actual boarding point for this corridor leg (may differ from query). */
    @SerialName("FromStation") val boardCode: String = "",
    @SerialName("FromStationName") val boardName: String = "",
    /** Server spells this key with a lowercase `t`. */
    @SerialName("toStation") val alightCode: String = "",
    @SerialName("ToStationName") val alightName: String = "",
    @SerialName("Source") val sourceCode: String = "",
    @SerialName("SourceName") val sourceName: String = "",
    @SerialName("Destination") val destCode: String = "",
    @SerialName("DestinationName") val destName: String = "",
    @SerialName("DepTimeFrom") val depTime: String = "",
    @SerialName("ArrTimeTo") val arrTime: String = "",
    @SerialName("TravelTime") val travelTime: String = "",
    @SerialName("ClassOfTravel") val classes: String = "",
    @SerialName("DayOfRun") val dayOfRun: String = "",
    @SerialName("TrainTypeDesc") val typeDesc: String = "",
) {
    fun depMinutes(): Int? = NtesFormats.hhmmToMinutes(depTime)
    fun arrMinutes(): Int? = NtesFormats.hhmmToMinutes(arrTime)
}

// --------------------------------------------------------------- schedule

/** GetTrainSchedule — the official current schedule (fills gaps the offline
 *  GTFS snapshot can't cover, e.g. trains introduced after the snapshot). */
@Serializable
data class TrainScheduleDto(
    @SerialName("TrainNumber") val trainNumber: String = "",
    @SerialName("TrainName") val trainName: String = "",
    @SerialName("Source") val sourceCode: String = "",
    @SerialName("SourceName") val sourceName: String = "",
    @SerialName("Destination") val destCode: String = "",
    @SerialName("DestinationName") val destName: String = "",
    @SerialName("TravelTime") val travelTime: String = "",
    @SerialName("DaysOfRun") val daysOfRun: String = "",
    @SerialName("TrainType") val trainType: String = "",
    @SerialName("TrainTypeDesc") val typeDesc: String = "",
    @SerialName("ClassOfTravel") val classes: String = "",
    @SerialName("startDate") val startDate: String = "",
    /** Selectable run dates for the date picker (newest-first on server). */
    @SerialName("vStartDateList") val availableDates: List<String> = emptyList(),
    @SerialName("stations") val stations: List<ScheduleStationDto> = emptyList(),
)

/** One stop of TrainScheduleDto (times are "HH:MM" or "" at the endpoints). */
@Serializable
data class ScheduleStationDto(
    @SerialName("Sr") val seq: Int = 0,
    @SerialName("StationCode") val code: String = "",
    @SerialName("StationName") val name: String = "",
    @SerialName("STA") val arrival: String = "",
    @SerialName("STD") val departure: String = "",
    @SerialName("Halt") val haltMin: Int = 0,
    @SerialName("Day") val day: Int = 1,
    @SerialName("DayOfRun") val dayOfRun: String = "",
    @SerialName("Distance") val distance: String = "",
    @SerialName("Reversal") val reversal: Int = 0,
) {
    fun arrMinutes(): Int? = NtesFormats.timeToMinutes(arrival)
    fun depMinutes(): Int? = NtesFormats.timeToMinutes(departure)
    /** Day offset from departure day (Day is 1-based). */
    val dayOffset: Int get() = (day - 1).coerceAtLeast(0)
}

// ------------------------------------------------------------- find train

/** FindTrainJson — train search by number/name fragment. */
@Serializable
data class FindTrainDto(
    @SerialName("Trains") val trains: List<FindTrainItemDto> = emptyList(),
    @SerialName("TrainNoName") val queryEcho: String = "",
)

@Serializable
data class FindTrainItemDto(
    @SerialName("TrainNumber") val trainNumber: String = "",
    @SerialName("TrainName") val trainName: String = "",
    @SerialName("Type") val trainType: String = "",
    @SerialName("Source") val sourceCode: String = "",
    @SerialName("SourceName") val sourceName: String = "",
    @SerialName("Destination") val destCode: String = "",
    @SerialName("DestinationName") val destName: String = "",
)

// -------------------------------------------------------- train instance

/** GetTrainInstance — recent runs of a train (position per run date). */
@Serializable
data class TrainInstanceDto(
    @SerialName("TrainNo") val trainNumber: String = "",
    @SerialName("TrainName") val trainName: String = "",
    @SerialName("Src") val sourceCode: String = "",
    @SerialName("SrcName") val sourceName: String = "",
    @SerialName("Dstn") val destCode: String = "",
    @SerialName("DstnName") val destName: String = "",
    @SerialName("vInstanceList") val instances: List<TrainRunInstanceDto> = emptyList(),
)

@Serializable
data class TrainRunInstanceDto(
    /** 0 = yet to start, 1 = running, 2 = arrived/completed. */
    @SerialName("trainStatus") val runState: Int = 0,
    @SerialName("trainPosition") val position: String = "",
    @SerialName("startDate") val startDate: String = "",
    @SerialName("excpMsg") val exceptionMsg: String = "",
)
