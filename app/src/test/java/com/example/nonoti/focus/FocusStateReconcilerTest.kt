package com.example.nonoti.focus

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusStateReconcilerTest {
    private val now = Instant.parse("2026-09-07T06:00:00Z")

    @Test
    fun readyWithoutASessionIsIdle() {
        val result = FocusStateReconciler.reconcile(
            FocusReconciliationInput(
                now = now,
                readiness = FocusReadiness.allReady(),
                session = null,
                zenRuleActive = false,
            ),
        )

        assertEquals(FocusState.Idle, result.state)
        assertNull(result.session)
    }

    @Test
    fun compatibilitySelfTestIsNotRequiredForFocus() {
        val readiness = FocusReadiness.allReady().copy(compatibility = false)

        assertEquals(true, readiness.isReady)
        assertEquals(emptySet<FocusReadinessRequirement>(), readiness.missingRequirements)
    }

    @Test
    fun missingReadinessIsUnsupportedBeforeStartingFocus() {
        val result = FocusStateReconciler.reconcile(
            FocusReconciliationInput(
                now = now,
                readiness = FocusReadiness.allReady().copy(notificationAccess = false),
                session = null,
                zenRuleActive = false,
            ),
        )

        assertEquals(FocusState.Unsupported, result.state)
        assertEquals(
            setOf(FocusReadinessRequirement.NotificationAccess),
            result.missingRequirements,
        )
    }

    @Test
    fun activeReadySessionIsFocusingBeforeItsEnd() {
        val session = session(end = now.plusSeconds(3_600))

        val result = FocusStateReconciler.reconcile(
            FocusReconciliationInput(
                now = now.plusSeconds(60),
                readiness = FocusReadiness.allReady(),
                session = session,
                zenRuleActive = true,
            ),
        )

        assertEquals(FocusState.Focusing, result.state)
        assertEquals(session, result.session)
    }

    @Test
    fun reachingTheEndMovesFocusingSessionToReleasing() {
        val session = session(end = now.plusSeconds(3_600))

        val result = FocusStateReconciler.reconcile(
            FocusReconciliationInput(
                now = session.end,
                readiness = FocusReadiness.allReady(),
                session = session,
                zenRuleActive = true,
            ),
        )

        assertEquals(FocusState.Releasing, result.state)
        assertEquals(FocusState.Releasing, result.session?.state)
    }

    @Test
    fun completedReleaseReturnsToIdle() {
        val session = session(
            end = now.minusSeconds(60),
            state = FocusState.Releasing,
        )

        val result = FocusStateReconciler.reconcile(
            FocusReconciliationInput(
                now = now,
                readiness = FocusReadiness.allReady(),
                session = session,
                zenRuleActive = true,
                releaseCompleted = true,
            ),
        )

        assertEquals(FocusState.Idle, result.state)
        assertNull(result.session)
    }

    @Test
    fun losingReadinessDuringFocusFailsOpenToUnsupported() {
        val session = session(end = now.plusSeconds(3_600))

        val result = FocusStateReconciler.reconcile(
            FocusReconciliationInput(
                now = now.plusSeconds(60),
                readiness = FocusReadiness.allReady().copy(notificationPolicyAccess = false),
                session = session,
                zenRuleActive = true,
            ),
        )

        assertEquals(FocusState.Unsupported, result.state)
        assertEquals(FocusState.Unsupported, result.session?.state)
        assertEquals(
            setOf(FocusReadinessRequirement.NotificationPolicyAccess),
            result.missingRequirements,
        )
    }

    @Test
    fun manualZenRuleRemovalFailsOpenWithoutReenablingIt() {
        val session = session(end = now.plusSeconds(3_600))

        val result = FocusStateReconciler.reconcile(
            FocusReconciliationInput(
                now = now.plusSeconds(60),
                readiness = FocusReadiness.allReady(),
                session = session,
                zenRuleActive = false,
            ),
        )

        assertEquals(FocusState.Unsupported, result.state)
        assertEquals(FocusFailureReason.ZenRuleInactive, result.failureReason)
    }

    @Test
    fun staleZenRuleWithoutASessionIsUnsupported() {
        val result = FocusStateReconciler.reconcile(
            FocusReconciliationInput(
                now = now,
                readiness = FocusReadiness.allReady(),
                session = null,
                zenRuleActive = true,
            ),
        )

        assertEquals(FocusState.Unsupported, result.state)
        assertEquals(FocusFailureReason.StaleZenRule, result.failureReason)
    }

    private fun session(
        end: Instant,
        state: FocusState = FocusState.Focusing,
        start: Instant = now.minusSeconds(120),
    ): FocusSession = FocusSession(
        id = "session-1",
        start = start,
        end = end,
        state = state,
    )
}
