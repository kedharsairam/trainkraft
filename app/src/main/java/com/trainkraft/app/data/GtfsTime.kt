package com.trainkraft.app.data

/**
 * Canonical GTFS time parsing for stop_times.txt.
 *
 * GTFS allows hours >= 24 to denote next-day service, e.g. "25:30:00"
 * means 01:30 on the following calendar day.
 *
 * Storage convention (see [StopTimeEntity]):
 * - minutesInDay = (hours % 24) * 60 + minutes  (0..1439, seconds dropped)
 * - dayOffset   = hours / 24                    (0 for same-day, 1+ after midnight)
 *
 * Use [parse] at import time; use [format] at display time.
 */
object GtfsTime {

    data class Parsed(val minutesInDay: Int, val dayOffset: Int)

    fun parse(raw: String): Parsed {
        val parts = raw.trim().split(":")
        require(parts.size == 3) { "Bad GTFS time: $raw" }
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        // Seconds intentionally dropped (timetable minute precision).
        val minutesInDay = (h % 24) * 60 + m
        val dayOffset = h / 24
        return Parsed(minutesInDay, dayOffset)
    }

    fun format(minutesInDay: Int, dayOffset: Int = 0): String {
        val h = (minutesInDay / 60) % 24
        val m = minutesInDay % 60
        val suffix = if (dayOffset > 0) " +$dayOffset d" else ""
        return "%02d:%02d%s".format(h, m, suffix)
    }
}
