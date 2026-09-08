package com.example.nonoti.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedFocusOccurrenceTest {
    @Test
    fun skippedOccurrenceDoesNotRestart() {
        assertFalse(PlannedFocusOccurrence.shouldStart("planned-540-660-1", "planned-540-660-1"))
    }

    @Test
    fun nextOccurrenceStillStarts() {
        assertTrue(PlannedFocusOccurrence.shouldStart("planned-540-660-2", "planned-540-660-1"))
    }

    @Test
    fun oneOffFocusIsNeverSuppressed() {
        assertTrue(PlannedFocusOccurrence.shouldStart("manual-1", "manual-1"))
    }
}
