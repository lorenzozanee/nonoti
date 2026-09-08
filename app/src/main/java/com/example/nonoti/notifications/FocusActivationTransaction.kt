package com.example.nonoti.notifications

object FocusActivationTransaction {
    fun run(
        persistRecoveryMarker: () -> Boolean,
        activate: () -> FocusStartResult,
        persistActive: () -> Boolean,
        rollback: () -> Unit,
    ): FocusStartResult {
        if (!persistRecoveryMarker()) return FocusStartResult.Unsupported
        val result = activate()
        if (result != FocusStartResult.Started) return result
        if (!persistActive()) {
            rollback()
            return FocusStartResult.Unsupported
        }
        return FocusStartResult.Started
    }
}
