package com.example.nonoti.focus

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusOccurrenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun nextOccurrenceUsesTodayWhenBlockHasNotStarted() {
        val now = ZonedDateTime.of(2026, 9, 7, 8, 0, 0, 0, zone)
        val occurrence = FocusOccurrences.currentOrNext(DailyFocusBlock(9 * 60, 11 * 60), now)

        assertEquals("2026-09-07T09:00+08:00[Asia/Shanghai]", occurrence.start.toString())
        assertEquals("2026-09-07T11:00+08:00[Asia/Shanghai]", occurrence.end.toString())
    }

    @Test
    fun crossMidnightBlockIsCurrentAfterMidnight() {
        val now = ZonedDateTime.of(2026, 9, 8, 1, 0, 0, 0, zone)
        val occurrence = FocusOccurrences.currentOrNext(DailyFocusBlock(23 * 60, 7 * 60), now)

        assertEquals("2026-09-07T23:00+08:00[Asia/Shanghai]", occurrence.start.toString())
        assertEquals("2026-09-08T07:00+08:00[Asia/Shanghai]", occurrence.end.toString())
    }

    @Test
    fun endedBlockMovesToTomorrow() {
        val now = ZonedDateTime.of(2026, 9, 7, 12, 0, 0, 0, zone)
        val occurrence = FocusOccurrences.currentOrNext(DailyFocusBlock(9 * 60, 11 * 60), now)

        assertEquals("2026-09-08T09:00+08:00[Asia/Shanghai]", occurrence.start.toString())
    }

    @Test
    fun nextOccurrenceAfterActiveBlockStartsTheFollowingDay() {
        val now = ZonedDateTime.of(2026, 9, 7, 10, 0, 0, 0, zone)

        val occurrence = FocusOccurrences.next(DailyFocusBlock(9 * 60, 11 * 60), now)

        assertEquals("2026-09-08T09:00+08:00[Asia/Shanghai]", occurrence.start.toString())
        assertEquals("2026-09-08T11:00+08:00[Asia/Shanghai]", occurrence.end.toString())
    }

    @Test
    fun activeOccurrenceUsesTheCurrentLocalTimezone() {
        val now = ZonedDateTime.parse("2026-09-07T15:00:00+08:00[Asia/Shanghai]")

        val occurrence = FocusOccurrences.active(
            listOf(DailyFocusBlock(14 * 60, 17 * 60)),
            now,
        )

        assertEquals("2026-09-07T14:00+08:00[Asia/Shanghai]", occurrence?.start.toString())
        assertEquals("2026-09-07T17:00+08:00[Asia/Shanghai]", occurrence?.end.toString())
    }

    @Test
    fun activeOccurrenceIsNullOutsideEveryBlock() {
        val now = ZonedDateTime.parse("2026-09-07T18:00:00+08:00[Asia/Shanghai]")

        val occurrence = FocusOccurrences.active(
            listOf(DailyFocusBlock(14 * 60, 17 * 60)),
            now,
        )

        assertEquals(null, occurrence)
    }

    @Test
    fun nonexistentLocalStartUsesTheNextValidClockTime() {
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, newYork)

        val occurrence = FocusOccurrences.currentOrNext(
            DailyFocusBlock(2 * 60 + 30, 3 * 60 + 30),
            now,
        )

        assertEquals("2026-03-08T03:00-04:00[America/New_York]", occurrence.start.toString())
        assertEquals("2026-03-08T03:30-04:00[America/New_York]", occurrence.end.toString())
    }

    @Test
    fun repeatedLocalBoundaryUsesOnlyTheEarlierOccurrence() {
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, newYork)

        val occurrence = FocusOccurrences.currentOrNext(
            DailyFocusBlock(1 * 60 + 30, 2 * 60),
            now,
        )

        assertEquals("2026-11-01T01:30-04:00[America/New_York]", occurrence.start.toString())
        assertEquals("2026-11-01T02:00-05:00[America/New_York]", occurrence.end.toString())
    }
}
