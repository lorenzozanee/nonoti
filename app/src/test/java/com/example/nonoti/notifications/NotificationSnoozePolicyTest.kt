package com.example.nonoti.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSnoozePolicyTest {
    @Test
    fun longFocusUsesShortSnoozeSlicesSoEarlyReleaseCanRecover() {
        assertEquals(60_000L, NotificationSnoozePolicy.durationMillis(10 * 60_000L))
    }

    @Test
    fun finalSliceNeverExtendsPastFocusEnd() {
        assertEquals(12_345L, NotificationSnoozePolicy.durationMillis(12_345L))
        assertEquals(0L, NotificationSnoozePolicy.durationMillis(0L))
    }

    @Test
    fun earlyReleaseWaitsForTheCurrentSliceButScheduledReleaseUsesShortSettleTime() {
        assertEquals(65_000L, NotificationSnoozePolicy.releaseDelayMillis(10 * 60_000L))
        assertEquals(10_000L, NotificationSnoozePolicy.releaseDelayMillis(5_000L))
        assertEquals(5_000L, NotificationSnoozePolicy.releaseDelayMillis(0L))
    }
}
