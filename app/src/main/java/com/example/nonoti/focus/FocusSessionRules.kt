package com.example.nonoti.focus

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

data class FocusWindow(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(end.isAfter(start)) { "Focus window end must be after its start" }
    }
}

enum class StartNowError {
    TooShort,
    TooLong,
}

sealed interface StartNowResult {
    data class Accepted(val window: FocusWindow) : StartNowResult

    data class Rejected(val reason: StartNowError) : StartNowResult
}

object FocusSessionRules {
    private val MinimumDuration = Duration.ofMinutes(5)
    private val MaximumDuration = Duration.ofHours(24)

    fun startNow(now: ZonedDateTime, requestedEnd: LocalTime): StartNowResult {
        val endDate = if (requestedEnd.isAfter(now.toLocalTime())) {
            now.toLocalDate()
        } else {
            now.toLocalDate().plusDays(1)
        }
        val end = ZonedDateTime.of(endDate, requestedEnd, now.zone)
        val duration = Duration.between(now.toInstant(), end.toInstant())

        return when {
            duration < MinimumDuration -> StartNowResult.Rejected(StartNowError.TooShort)
            duration > MaximumDuration -> StartNowResult.Rejected(StartNowError.TooLong)
            else -> StartNowResult.Accepted(FocusWindow(now.toInstant(), end.toInstant()))
        }
    }

    fun extend(current: FocusWindow, requestedEnd: Instant): FocusWindow =
        if (requestedEnd.isAfter(current.end)) {
            current.copy(end = requestedEnd)
        } else {
            current
        }

    fun mergeContinuous(windows: List<FocusWindow>): List<FocusWindow> {
        if (windows.isEmpty()) return emptyList()

        val merged = mutableListOf<FocusWindow>()
        for (window in windows.sortedBy { it.start }) {
            val previous = merged.lastOrNull()
            if (previous == null || window.start.isAfter(previous.end)) {
                merged += window
            } else if (window.end.isAfter(previous.end)) {
                merged[merged.lastIndex] = previous.copy(end = window.end)
            }
        }
        return merged
    }

    fun extendThroughDailyBlocks(
        initial: FocusWindow,
        blocks: List<DailyFocusBlock>,
        now: ZonedDateTime,
    ): FocusWindow {
        val occurrences = blocks.map { block ->
            val occurrence = FocusOccurrences.currentOrNext(block, now)
            FocusWindow(occurrence.start.toInstant(), occurrence.end.toInstant())
        }
        return mergeContinuous(occurrences + initial).first { window ->
            !initial.start.isBefore(window.start) && !initial.end.isAfter(window.end)
        }
    }

    fun mergeWithActive(active: FocusWindow, candidate: FocusWindow): FocusWindow? {
        if (candidate.start.isAfter(active.end) || active.start.isAfter(candidate.end)) return null
        return FocusWindow(
            start = minOf(active.start, candidate.start),
            end = maxOf(active.end, candidate.end),
        )
    }
}
