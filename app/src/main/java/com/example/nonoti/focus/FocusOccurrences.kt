package com.example.nonoti.focus

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class FocusOccurrence(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
)

object FocusOccurrences {
    fun active(blocks: List<DailyFocusBlock>, now: ZonedDateTime): FocusOccurrence? =
        blocks
            .asSequence()
            .map { block -> currentOrNext(block, now) }
            .firstOrNull { occurrence -> !now.isBefore(occurrence.start) && now.isBefore(occurrence.end) }

    fun currentOrNext(block: DailyFocusBlock, now: ZonedDateTime): FocusOccurrence {
        if (block.startMinute > block.endMinute) {
            val previous = occurrence(block, now.toLocalDate().minusDays(1), now)
            if (!now.isBefore(previous.start) && now.isBefore(previous.end)) return previous
        }

        val today = occurrence(block, now.toLocalDate(), now)
        if (now.isBefore(today.start) || now.isBefore(today.end)) return today
        return occurrence(block, now.toLocalDate().plusDays(1), now)
    }

    fun next(block: DailyFocusBlock, now: ZonedDateTime): FocusOccurrence {
        val today = occurrence(block, now.toLocalDate(), now)
        return if (today.start.isAfter(now)) {
            today
        } else {
            occurrence(block, now.toLocalDate().plusDays(1), now)
        }
    }

    private fun occurrence(block: DailyFocusBlock, startDate: LocalDate, now: ZonedDateTime): FocusOccurrence {
        val start = resolve(startDate, block.startMinute, now.zone)
        val endDate = if (block.endMinute < block.startMinute) startDate.plusDays(1) else startDate
        val end = resolve(endDate, block.endMinute, now.zone)
        return FocusOccurrence(start, end)
    }

    private fun resolve(date: LocalDate, minute: Int, zone: ZoneId): ZonedDateTime {
        val local = date.atTime(minute.toLocalTime())
        val validOffsets = zone.rules.getValidOffsets(local)
        if (validOffsets.isNotEmpty()) return ZonedDateTime.ofLocal(local, zone, validOffsets.first())
        val transition = requireNotNull(zone.rules.getTransition(local))
        return transition.dateTimeAfter.atZone(zone)
    }

    private fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
}
