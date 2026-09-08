package com.example.nonoti.focus

import java.time.Instant

enum class FocusState {
    Idle,
    Focusing,
    Releasing,
    Unsupported,
}

enum class FocusReadinessRequirement {
    NotificationAccess,
    NotificationPolicyAccess,
    ZenRule,
    ExactAlarms,
    PostNotifications,
}

data class FocusReadiness(
    val notificationAccess: Boolean,
    val notificationPolicyAccess: Boolean,
    val zenRule: Boolean,
    val exactAlarms: Boolean,
    val postNotifications: Boolean,
    val compatibility: Boolean,
) {
    val missingRequirements: Set<FocusReadinessRequirement>
        get() = buildSet {
            if (!notificationAccess) add(FocusReadinessRequirement.NotificationAccess)
            if (!notificationPolicyAccess) add(FocusReadinessRequirement.NotificationPolicyAccess)
            if (!zenRule) add(FocusReadinessRequirement.ZenRule)
            if (!exactAlarms) add(FocusReadinessRequirement.ExactAlarms)
            if (!postNotifications) add(FocusReadinessRequirement.PostNotifications)
        }

    val isReady: Boolean
        get() = missingRequirements.isEmpty()

    companion object {
        fun allReady(): FocusReadiness = FocusReadiness(
            notificationAccess = true,
            notificationPolicyAccess = true,
            zenRule = true,
            exactAlarms = true,
            postNotifications = true,
            compatibility = true,
        )
    }
}

enum class FocusFailureReason {
    MissingReadiness,
    ZenRuleInactive,
    StaleZenRule,
    PersistedUnsupported,
}

data class FocusSession(
    val id: String,
    val start: Instant,
    val end: Instant,
    val state: FocusState = FocusState.Focusing,
) {
    init {
        require(end.isAfter(start)) { "Focus session end must be after its start" }
    }
}

data class FocusReconciliationInput(
    val now: Instant,
    val readiness: FocusReadiness,
    val session: FocusSession?,
    val zenRuleActive: Boolean,
    val releaseCompleted: Boolean = false,
)

data class FocusReconciliation(
    val state: FocusState,
    val session: FocusSession?,
    val missingRequirements: Set<FocusReadinessRequirement> = emptySet(),
    val failureReason: FocusFailureReason? = null,
)

object FocusStateReconciler {
    fun reconcile(input: FocusReconciliationInput): FocusReconciliation {
        val missing = input.readiness.missingRequirements
        val session = input.session

        if (missing.isNotEmpty()) {
            return unsupported(
                session = session?.copy(state = FocusState.Unsupported),
                missing = missing,
                reason = FocusFailureReason.MissingReadiness,
            )
        }

        if (session == null) {
            return if (input.zenRuleActive) {
                unsupported(
                    session = null,
                    missing = emptySet(),
                    reason = FocusFailureReason.StaleZenRule,
                )
            } else {
                FocusReconciliation(FocusState.Idle, session = null)
            }
        }

        if (session.state == FocusState.Unsupported) {
            return unsupported(
                session = session,
                missing = emptySet(),
                reason = FocusFailureReason.PersistedUnsupported,
            )
        }

        if (session.state == FocusState.Releasing && input.releaseCompleted) {
            return FocusReconciliation(FocusState.Idle, session = null)
        }

        if (!input.zenRuleActive) {
            return unsupported(
                session = session.copy(state = FocusState.Unsupported),
                missing = emptySet(),
                reason = FocusFailureReason.ZenRuleInactive,
            )
        }

        if (session.state == FocusState.Releasing) {
            return FocusReconciliation(FocusState.Releasing, session = session)
        }

        return if (input.now.isBefore(session.end)) {
            FocusReconciliation(
                state = FocusState.Focusing,
                session = session.copy(state = FocusState.Focusing),
            )
        } else {
            FocusReconciliation(
                state = FocusState.Releasing,
                session = session.copy(state = FocusState.Releasing),
            )
        }
    }

    private fun unsupported(
        session: FocusSession?,
        missing: Set<FocusReadinessRequirement>,
        reason: FocusFailureReason,
    ): FocusReconciliation = FocusReconciliation(
        state = FocusState.Unsupported,
        session = session,
        missingRequirements = missing,
        failureReason = reason,
    )
}
