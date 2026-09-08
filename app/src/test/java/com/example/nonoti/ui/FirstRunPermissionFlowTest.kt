package com.example.nonoti.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstRunPermissionFlowTest {
    @Test
    fun missingPermissionsAreAttemptedInSystemPromptOrder() {
        val missing = PermissionReadiness(
            postNotifications = false,
            notificationAccess = false,
            dndAccess = false,
            exactAlarms = false,
        )

        assertEquals(PermissionOnboardingStep.NotificationAccess, FirstRunPermissionFlow.next(missing, emptySet()))
        assertEquals(
            PermissionOnboardingStep.DoNotDisturb,
            FirstRunPermissionFlow.next(missing, setOf(PermissionOnboardingStep.NotificationAccess)),
        )
        assertEquals(
            PermissionOnboardingStep.ExactAlarms,
            FirstRunPermissionFlow.next(
                missing,
                setOf(PermissionOnboardingStep.NotificationAccess, PermissionOnboardingStep.DoNotDisturb),
            ),
        )
        assertEquals(
            PermissionOnboardingStep.PostNotifications,
            FirstRunPermissionFlow.next(missing, PermissionOnboardingStep.entries.dropLast(1).toSet()),
        )
    }

    @Test
    fun grantedAndAlreadyAttemptedPermissionsCompleteTheFlow() {
        val readiness = PermissionReadiness(
            postNotifications = true,
            notificationAccess = false,
            dndAccess = true,
            exactAlarms = true,
        )

        assertNull(
            FirstRunPermissionFlow.next(
                readiness,
                setOf(PermissionOnboardingStep.NotificationAccess),
            ),
        )
    }
}
