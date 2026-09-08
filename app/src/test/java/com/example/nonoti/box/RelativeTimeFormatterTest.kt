package com.example.nonoti.box

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeFormatterTest {
    @Test
    fun formatsRecentMinutesHoursAndDays() {
        val now = 10 * 24 * 60 * 60 * 1_000L

        assertEquals(RelativeTime(0, RelativeTimeUnit.Now), RelativeTimeFormatter.format(now - 30_000, now))
        assertEquals(RelativeTime(5, RelativeTimeUnit.Minutes), RelativeTimeFormatter.format(now - 5 * 60_000, now))
        assertEquals(RelativeTime(3, RelativeTimeUnit.Hours), RelativeTimeFormatter.format(now - 3 * 60 * 60_000, now))
        assertEquals(RelativeTime(2, RelativeTimeUnit.Days), RelativeTimeFormatter.format(now - 2 * 24 * 60 * 60_000, now))
    }
}
