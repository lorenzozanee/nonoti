package com.example.nonoti.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusCapabilityGuardTest {
    @Test
    fun activeSessionIsReturnedWhenSummaryCapabilityIsLost() {
        assertEquals("session", FocusCapabilityGuard.failedSession("session", canPostSummary = false))
    }

    @Test
    fun noFailureIsReturnedWithoutAnActiveSessionOrWhenSummaryIsAvailable() {
        assertNull(FocusCapabilityGuard.failedSession(null, canPostSummary = false))
        assertNull(FocusCapabilityGuard.failedSession("session", canPostSummary = true))
    }
}
