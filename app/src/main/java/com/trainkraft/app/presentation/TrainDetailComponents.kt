package com.trainkraft.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.ScheduleStop
import com.trainkraft.app.ui.theme.KraftSpacing
import org.json.JSONObject

internal data class LiveParsed(
    val delay: String?,
    val platform: String?,
    val lastLocation: String?,
    val status: String?,
    val destination: String?,
    val source: String?,
    val date: String?,
    val fallbackEntries: List<Pair<String, String>>,
)

internal fun parseLiveJson(raw: String): LiveParsed? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{")) return null
    return try {
        val obj = JSONObject(trimmed)
        fun findValue(vararg candidates: String): String? {
            val topKeys = mutableListOf<String>()
            val iter = obj.keys()
            while (iter.hasNext()) topKeys.add(iter.next())
            for (cand in candidates) {
                topKeys.firstOrNull { it.equals(cand, ignoreCase = true) }?.let { k ->
                    val v = obj.optString(k, "").trim()
                    if (v.isNotEmpty() && v != "null") return v
                }
            }
            for (cand in candidates) {
                topKeys.firstOrNull { it.contains(cand, ignoreCase = true) }?.let { k ->
                    val v = obj.optString(k, "").trim()
                    if (v.isNotEmpty() && v != "null") return v
                }
            }
            for (k in topKeys) {
                val nested = runCatching { obj.getJSONObject(k) }.getOrNull() ?: continue
                val nKeys = mutableListOf<String>()
                val nIter = nested.keys()
                while (nIter.hasNext()) nKeys.add(nIter.next())
                for (cand in candidates) {
                    nKeys.firstOrNull { it.equals(cand, ignoreCase = true) }?.let { nk ->
                        val v = nested.optString(nk, "").trim()
                        if (v.isNotEmpty() && v != "null") return v
                    }
                }
                for (cand in candidates) {
                    nKeys.firstOrNull { it.contains(cand, ignoreCase = true) }?.let { nk ->
                        val v = nested.optString(nk, "").trim()
                        if (v.isNotEmpty() && v != "null") return v
                    }
                }
            }
            return null
        }

        val ldel = obj.optString("LDEL", "").trim()
        val delay = ldel.ifEmpty { findValue("delay", "late", "delayMinutes", "lateMinutes", "delayInMinutes", "arrivalDelay", "departureDelay") }
        val platform = findValue("platform", "platformNo", "platNo", "pf")
        val lastLocation = findValue("lastLocation", "curStation", "currentStation", "currStn", "curStn", "lastStation", "currentLocation", "location", "lastStn")
        val status = findValue("status", "runningStatus", "curStatus", "trainStatus", "curStnStatus")
        val destination = findValue("DSTNN", "destination", "destStation", "dest")
        val source = findValue("SRC", "source", "srcStation", "src")
        val date = findValue("STD", "date", "runDate", "scheduleDate")

        val fallback = mutableListOf<Pair<String, String>>()
        val iter2 = obj.keys()
        while (iter2.hasNext()) {
            val k = iter2.next()
            val v = obj.opt(k)?.toString()?.trim().orEmpty()
            if (v.isNotEmpty() && v != "null" && v != "{}" && v != "[]") {
                val display = if (v.length > 120) v.take(120) + "\u2026" else v
                fallback.add(k to display)
            }
        }
        LiveParsed(delay, platform, lastLocation, status, destination, source, date, fallback)
    } catch (e: Exception) {
        if (com.trainkraft.app.BuildConfig.DEBUG) {
            android.util.Log.d("TrainDetailComponents", "parseLiveJson failed", e)
        }
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveParsedCard(json: String) {
    val parsed = remember(json) { parseLiveJson(json) }
    val showRaw = remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KraftSpacing.spacing12),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
        ) {
            if (parsed != null) {
                val statusText = buildString {
                    parsed.status?.let { append(it) }
                    parsed.delay?.let {
                        if (isNotEmpty()) append(" \u00b7 ")
                        append("${it}min late")
                    }
                }
                if (statusText.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
                    ) {
                        val dotColor = when {
                            parsed.delay == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            parsed.delay.toIntOrNull()?.let { it <= 5 } == true -> MaterialTheme.colorScheme.tertiary
                            parsed.delay.toIntOrNull()?.let { it <= 15 } == true -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.error
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(dotColor, CircleShape),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                val details = mutableListOf<Pair<String, String>>()
                parsed.platform?.let { details.add("Platform" to it) }
                parsed.lastLocation?.let { details.add("At" to it) }
                parsed.source?.let { details.add("From" to it) }
                parsed.destination?.let { details.add("To" to it) }
                parsed.date?.let { details.add("Date" to it) }

                if (details.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing4),
                    ) {
                        details.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(64.dp),
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (parsed.fallbackEntries.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = KraftSpacing.spacing2))
                    Text(
                        text = if (showRaw.value) "Hide raw data" else "Show raw data",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRaw.value = !showRaw.value }
                            .padding(vertical = KraftSpacing.spacing4),
                    )
                    if (showRaw.value) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing2),
                        ) {
                            parsed.fallbackEntries.forEach { (k, v) ->
                                LiveRow(label = k, value = v)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LiveRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = KraftSpacing.spacing8),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun CoachPositionSection(json: String) {
    val parsed = remember(json) { parseCoachPosition(json) }
    if (parsed == null || parsed.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Coach position",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing4),
            modifier = Modifier.fillMaxWidth(),
        ) {
            parsed.forEach { coach ->
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        text = coach,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
        val classInfo = remember(json) { parseCoachClass(json) }
        if (classInfo != null && classInfo.isNotEmpty()) {
            Text(
                text = classInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun parseCoachPosition(json: String): List<String>? {
    return try {
        val obj = JSONObject(json.trim())
        val stns = obj.optJSONArray("STNS") ?: return null
        if (stns.length() == 0) return null
        val firstStation = stns.getJSONObject(0)
        val coachPos = firstStation.optString("arrivalCoachPosition", "").trim()
        if (coachPos.isEmpty()) {
            val depPos = firstStation.optString("departureCoachPosition", "").trim()
            if (depPos.isEmpty()) return null
            depPos.split("-").map { it.trim() }
        } else {
            coachPos.split("-").map { it.trim() }
        }
    } catch (e: Exception) {
        if (com.trainkraft.app.BuildConfig.DEBUG) {
            android.util.Log.d("TrainDetailComponents", "parseCoachPosition failed", e)
        }
        null
    }
}

private fun parseCoachClass(json: String): String? {
    return try {
        val obj = JSONObject(json.trim())
        val stns = obj.optJSONArray("STNS") ?: return null
        if (stns.length() == 0) return null
        val firstStation = stns.getJSONObject(0)
        val coachClass = firstStation.optString("arrivalCoachClass", "").trim()
        if (coachClass.isEmpty()) return null
        val parts = coachClass.split("-").map { it.trim() }
        val grouped = mutableListOf<Pair<String, Int>>()
        var current = parts.firstOrNull() ?: return null
        var count = 1
        for (i in 1 until parts.size) {
            if (parts[i] == current) {
                count++
            } else {
                grouped.add(current to count)
                current = parts[i]
                count = 1
            }
        }
        grouped.add(current to count)
        grouped.joinToString(" + ") { (cls, cnt) -> if (cnt > 1) "$cnt\u00d7$cls" else cls }
    } catch (e: Exception) {
        if (com.trainkraft.app.BuildConfig.DEBUG) {
            android.util.Log.d("TrainDetailComponents", "parseCoachClass failed", e)
        }
        null
    }
}

@Composable
internal fun AvgDelaySection(
    json: String?,
    isLoading: Boolean,
    use24h: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KraftSpacing.spacing16),
        verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
    ) {
        Text(
            text = "Average delay",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Loading\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (json != null && !isLoading) {
            val entries = remember(json) { parseAvgDelay(json) }
            if (entries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(KraftSpacing.spacing2)) {
                    entries.take(10).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = entry.first,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(100.dp),
                            )
                            Spacer(Modifier.width(KraftSpacing.spacing8))
                            Text(
                                text = entry.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseAvgDelay(json: String): List<Pair<String, String>> {
    return try {
        val obj = JSONObject(json.trim())
        val list = obj.optJSONArray("vAvgDelayList") ?: return emptyList()
        val entries = mutableListOf<Pair<String, String>>()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val stnName = item.optString("stnName", item.optString("stn", ""))
            val arrDelay = item.optString("stnArrDelay", "").trim()
            val depDelay = item.optString("stnDepDelay", "").trim()
            val delay = when {
                arrDelay.isNotEmpty() && depDelay.isNotEmpty() -> "Arr $arrDelay / Dep $depDelay"
                arrDelay.isNotEmpty() -> "Arr $arrDelay"
                depDelay.isNotEmpty() -> "Dep $depDelay"
                else -> "On time"
            }
            entries.add(stnName to delay)
        }
        entries
    } catch (e: Exception) {
        if (com.trainkraft.app.BuildConfig.DEBUG) {
            android.util.Log.d("TrainDetailComponents", "parseAvgDelay failed", e)
        }
        emptyList()
    }
}

@Composable
internal fun ScheduleStopRow(
    stop: ScheduleStop,
    isFirst: Boolean,
    isLast: Boolean,
    use24h: Boolean = true,
) {
    val endpointColor = when {
        isFirst -> MaterialTheme.colorScheme.tertiary
        isLast -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KraftSpacing.spacing16,
                vertical = KraftSpacing.spacing12,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(endpointColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stop.seq.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(KraftSpacing.spacing12))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KraftSpacing.spacing8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stop.code,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isFirst || isLast) endpointColor
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (stop.dayOffset > 0) {
                    DayBadge(stop.dayOffset)
                }
            }
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Arr ${stop.arrMin?.let { GtfsTime.format(it, use24h = use24h) } ?: "--:--"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Dep ${stop.depMin?.let { GtfsTime.format(it, use24h = use24h) } ?: "--:--"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
