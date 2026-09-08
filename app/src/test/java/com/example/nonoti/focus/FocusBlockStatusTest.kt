package com.example.nonoti.focus

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusBlockStatusTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun sameDayBlockTransitionsFromUpcomingToActiveToFinished() {
        val block = DailyFocusBlock(9 * 60, 11 * 60)

        assertEquals(FocusBlockStatus.Upcoming, FocusBlockStatuses.at(block, at(8, 59)))
        assertEquals(FocusBlockStatus.Active, FocusBlockStatuses.at(block, at(9, 0)))
        assertEquals(FocusBlockStatus.Active, FocusBlockStatuses.at(block, at(10, 59)))
        assertEquals(FocusBlockStatus.Finished, FocusBlockStatuses.at(block, at(11, 0)))
    }

    @Test
    fun crossMidnightBlockIsActiveOnBothSidesOfMidnight() {
        val block = DailyFocusBlock(23 * 60, 7 * 60)

        assertEquals(FocusBlockStatus.Upcoming, FocusBlockStatuses.at(block, at(22, 59)))
        assertEquals(FocusBlockStatus.Active, FocusBlockStatuses.at(block, at(23, 0)))
        assertEquals(FocusBlockStatus.Active, FocusBlockStatuses.at(block, at(1, 0)))
        assertEquals(FocusBlockStatus.Upcoming, FocusBlockStatuses.at(block, at(7, 0)))
    }

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 9, 7, hour, minute, 0, 0, zone)
}
