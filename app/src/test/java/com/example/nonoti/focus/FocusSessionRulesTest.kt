package com.example.nonoti.focus

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun startNowEndAtOrBeforeCurrentLocalTimeUsesTomorrow() {
        val now = ZonedDateTime.of(2026, 9, 7, 14, 30, 15, 0, zone)

        val result = FocusSessionRules.startNow(now, LocalTime.of(14, 0))

        assertTrue(result is StartNowResult.Accepted)
        val window = (result as StartNowResult.Accepted).window
        assertEquals(now.toInstant(), window.start)
        assertEquals(
            ZonedDateTime.of(2026, 9, 8, 14, 0, 0, 0, zone).toInstant(),
            window.end,
        )
    }

    @Test
    fun startNowEndAfterCurrentLocalTimeStaysOnToday() {
        val now = ZonedDateTime.of(2026, 9, 7, 14, 30, 0, 0, zone)

        val result = FocusSessionRules.startNow(now, LocalTime.of(17, 0))

        assertEquals(
            ZonedDateTime.of(2026, 9, 7, 17, 0, 0, 0, zone).toInstant(),
            (result as StartNowResult.Accepted).window.end,
        )
    }

    @Test
    fun startNowRejectsDurationShorterThanFiveMinutes() {
        val now = ZonedDateTime.of(2026, 9, 7, 14, 30, 15, 0, zone)

        val result = FocusSessionRules.startNow(now, LocalTime.of(14, 34))

        assertEquals(StartNowError.TooShort, (result as StartNowResult.Rejected).reason)
    }

    @Test
    fun extendingAnActiveSessionCannotShortenIt() {
        val current = FocusWindow(
            start = Instant.parse("2026-09-07T06:00:00Z"),
            end = Instant.parse("2026-09-07T09:00:00Z"),
        )

        val result = FocusSessionRules.extend(
            current,
            requestedEnd = Instant.parse("2026-09-07T08:00:00Z"),
        )

        assertEquals(current, result)
    }

    @Test
    fun extendingAnActiveSessionMovesOnlyItsEndForward() {
        val current = FocusWindow(
            start = Instant.parse("2026-09-07T06:00:00Z"),
            end = Instant.parse("2026-09-07T09:00:00Z"),
        )

        val result = FocusSessionRules.extend(
            current,
            requestedEnd = Instant.parse("2026-09-07T10:30:00Z"),
        )

        assertEquals(current.start, result.start)
        assertEquals(Instant.parse("2026-09-07T10:30:00Z"), result.end)
    }

    @Test
    fun overlappingAndTouchingWindowsBecomeOneContinuousSession() {
        val windows = listOf(
            FocusWindow(
                Instant.parse("2026-09-07T06:00:00Z"),
                Instant.parse("2026-09-07T08:00:00Z"),
            ),
            FocusWindow(
                Instant.parse("2026-09-07T07:30:00Z"),
                Instant.parse("2026-09-07T09:00:00Z"),
            ),
            FocusWindow(
                Instant.parse("2026-09-07T09:00:00Z"),
                Instant.parse("2026-09-07T10:00:00Z"),
            ),
        )

        assertEquals(
            listOf(
                FocusWindow(
                    Instant.parse("2026-09-07T06:00:00Z"),
                    Instant.parse("2026-09-07T10:00:00Z"),
                ),
            ),
            FocusSessionRules.mergeContinuous(windows),
        )
    }

    @Test
    fun disjointWindowsRemainSeparateAndSorted() {
        val later = FocusWindow(
            Instant.parse("2026-09-07T12:00:00Z"),
            Instant.parse("2026-09-07T13:00:00Z"),
        )
        val earlier = FocusWindow(
            Instant.parse("2026-09-07T06:00:00Z"),
            Instant.parse("2026-09-07T07:00:00Z"),
        )

        assertEquals(listOf(earlier, later), FocusSessionRules.mergeContinuous(listOf(later, earlier)))
    }

    @Test
    fun startNowExtendsThroughTouchingDailyBlocks() {
        val now = ZonedDateTime.of(2026, 9, 7, 14, 0, 0, 0, zone)
        val manual = FocusWindow(now.toInstant(), now.plusHours(1).toInstant())

        val result = FocusSessionRules.extendThroughDailyBlocks(
            manual,
            listOf(DailyFocusBlock(15 * 60, 16 * 60), DailyFocusBlock(16 * 60, 17 * 60)),
            now,
        )

        assertEquals(now.plusHours(3).toInstant(), result.end)
    }

    @Test
    fun activeSessionMergesWithTouchingPlannedSession() {
        val active = FocusWindow(
            Instant.parse("2026-09-07T06:00:00Z"),
            Instant.parse("2026-09-07T07:00:00Z"),
        )
        val planned = FocusWindow(
            Instant.parse("2026-09-07T07:00:00Z"),
            Instant.parse("2026-09-07T09:00:00Z"),
        )

        assertEquals(
            FocusWindow(active.start, planned.end),
            FocusSessionRules.mergeWithActive(active, planned),
        )
    }

    @Test
    fun disjointSessionDoesNotReplaceActiveSession() {
        val active = FocusWindow(
            Instant.parse("2026-09-07T06:00:00Z"),
            Instant.parse("2026-09-07T07:00:00Z"),
        )
        val planned = FocusWindow(
            Instant.parse("2026-09-07T08:00:00Z"),
            Instant.parse("2026-09-07T09:00:00Z"),
        )

        assertEquals(null, FocusSessionRules.mergeWithActive(active, planned))
    }
}
