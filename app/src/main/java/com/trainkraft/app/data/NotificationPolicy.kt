package com.trainkraft.app.data

/**
 * One live-status poll's observable state.
 *
 * @property delayMin minutes of delay reported by NTES (LDEL)
 * @property lastStation last reported station (LSTNN/LSTN)
 * @property nextStation next station (NSTNN/NSTN)
 * @property statusText free-text run status (CPOS/LASTUPD)
 */
data class PollSnapshot(
    val delayMin: Int,
    val lastStation: String,
    val nextStation: String,
    val statusText: String,
    /** TRUNST: 0 = not started, 1 = running, 2 = arrived; -1 = unknown. */
    val runState: Int = -1,
    /** isArrDSTN — authoritative "reached destination" flag. */
    val arrivedAtDest: Boolean = false,
)

/**
 * Decides whether a poll warrants a notification — the single authority for
 * background alerts. Pure Kotlin (no Android/JSON), so every branch is unit
 * testable.
 *
 * Contract:
 *  - First poll after tracking starts = silent baseline (user just saw state).
 *  - Notify only on *meaningful change*: delay category deterioration, a big
 *    shift inside SEVERE (≥15 min), cancellation, or journey completion.
 *  - Station-to-station movement is NOT a trigger (the old worker notified on
 *    every 10-minute poll because "has a station" is almost always true).
 *  - Delay buckets mirror the UI: ≤5 min on-time, 6–15 moderate, >15 severe.
 */
object NotificationPolicy {

    enum class DelayCategory { ON_TIME, MODERATE, SEVERE }

    /** A delay jump of this many minutes inside SEVERE re-notifies. */
    const val SEVERE_SHIFT_MIN = 15

    sealed interface Decision {
        /** Post [title]/[body]. [journeyOver] means: stop tracking afterwards. */
        data class Notify(
            val title: String,
            val body: String,
            val journeyOver: Boolean = false,
        ) : Decision

        /** Update stored state silently. */
        data object Silent : Decision
    }

    fun delayCategory(delayMin: Int): DelayCategory = when {
        delayMin <= 5 -> DelayCategory.ON_TIME
        delayMin <= 15 -> DelayCategory.MODERATE
        else -> DelayCategory.SEVERE
    }

    fun isCancelled(statusText: String): Boolean =
        statusText.contains("cancel", ignoreCase = true) ||
            statusText.contains("divert", ignoreCase = true)

    fun isCompleted(s: PollSnapshot): Boolean =
        s.arrivedAtDest ||
            s.runState == 2 ||
            s.statusText.contains("journey completed", ignoreCase = true) ||
            s.statusText.contains("journey end", ignoreCase = true)

    /**
     * @param trainNumber shown in the notification title
     * @param current this poll's snapshot
     * @param prior stored row from the previous poll (null = first poll)
     */
    fun decide(
        trainNumber: String,
        current: PollSnapshot,
        prior: TrackedTrainEntity?,
    ): Decision {
        if (isCancelled(current.statusText)) {
            return Decision.Notify(
                title = "Train $trainNumber — Cancelled",
                body = body(current),
            )
        }
        if (isCompleted(current)) {
            return Decision.Notify(
                title = "Train $trainNumber — Journey completed",
                body = body(current),
                journeyOver = true,
            )
        }

        // First poll: store baseline only — nothing to diff against yet.
        if (prior?.lastPollAt == null) return Decision.Silent

        val priorCategory = prior.lastCategory
            ?.let { runCatching { DelayCategory.valueOf(it) }.getOrNull() }
            ?: return Decision.Silent

        val category = delayCategory(current.delayMin)

        val worsened = when (priorCategory) {
            DelayCategory.ON_TIME -> category != DelayCategory.ON_TIME
            DelayCategory.MODERATE -> category == DelayCategory.SEVERE
            DelayCategory.SEVERE -> false
        }
        val severeShift = category == DelayCategory.SEVERE &&
            prior.lastDelayMin != null &&
            kotlin.math.abs(current.delayMin - prior.lastDelayMin) >= SEVERE_SHIFT_MIN

        return if (worsened || severeShift) {
            Decision.Notify(
                title = "Train $trainNumber — now ${current.delayMin} min late",
                body = buildString {
                    append("Was ${prior.lastDelayMin ?: 0} min late")
                    if (current.lastStation.isNotEmpty()) append(" · At ${current.lastStation}")
                    if (current.nextStation.isNotEmpty()) append(" · Next: ${current.nextStation}")
                },
            )
        } else {
            Decision.Silent
        }
    }

    private fun body(s: PollSnapshot): String = buildString {
        if (s.delayMin > 0) append("${s.delayMin} min late · ")
        if (s.lastStation.isNotEmpty()) append("At ${s.lastStation}")
        if (s.nextStation.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append("Next: ${s.nextStation}")
        }
        if (isEmpty()) append(s.statusText)
    }
}
