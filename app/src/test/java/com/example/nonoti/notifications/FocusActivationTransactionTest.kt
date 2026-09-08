package com.example.nonoti.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusActivationTransactionTest {
    @Test
    fun recoveryMarkerIsPersistedBeforePlatformActivation() {
        val events = mutableListOf<String>()

        val result = FocusActivationTransaction.run(
            persistRecoveryMarker = { events += "marker"; true },
            activate = { events += "activate"; FocusStartResult.Started },
            persistActive = { events += "active"; true },
            rollback = { events += "rollback" },
        )

        assertEquals(FocusStartResult.Started, result)
        assertEquals(listOf("marker", "activate", "active"), events)
    }

    @Test
    fun activePersistenceFailureRollsBackPlatformState() {
        val events = mutableListOf<String>()

        val result = FocusActivationTransaction.run(
            persistRecoveryMarker = { events += "marker"; true },
            activate = { events += "activate"; FocusStartResult.Started },
            persistActive = { events += "active"; false },
            rollback = { events += "rollback" },
        )

        assertEquals(FocusStartResult.Unsupported, result)
        assertEquals(listOf("marker", "activate", "active", "rollback"), events)
    }

    @Test
    fun recoveryMarkerFailureNeverTouchesPlatform() {
        val events = mutableListOf<String>()

        val result = FocusActivationTransaction.run(
            persistRecoveryMarker = { events += "marker"; false },
            activate = { events += "activate"; FocusStartResult.Started },
            persistActive = { events += "active"; true },
            rollback = { events += "rollback" },
        )

        assertEquals(FocusStartResult.Unsupported, result)
        assertEquals(listOf("marker"), events)
    }
}
