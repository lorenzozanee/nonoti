package com.example.nonoti.notifications

import com.example.nonoti.focus.FocusReadiness
import com.example.nonoti.focus.FocusSession
import com.example.nonoti.focus.FocusState

interface FocusPlatformPort {
    fun enableZen(): Boolean
    fun disableZen(): Boolean
    fun scheduleEnd(session: FocusSession): Boolean
    fun sendSummary(session: FocusSession, distinctCount: Int)
}

enum class FocusStartResult {
    Started,
    Rejected,
    Unsupported,
}

class FocusPlatformCoordinator(private val platform: FocusPlatformPort) {
    private val sessions = linkedMapOf<String, SessionRecord>()

    fun start(session: FocusSession, readiness: FocusReadiness): FocusStartResult {
        if (!readiness.isReady) return FocusStartResult.Rejected
        val existing = sessions[session.id]
        if (existing?.state == FocusState.Focusing) return FocusStartResult.Started
        if (!platform.enableZen()) {
            sessions[session.id] = SessionRecord(session, FocusState.Unsupported)
            return FocusStartResult.Unsupported
        }
        if (!platform.scheduleEnd(session)) {
            platform.disableZen()
            sessions[session.id] = SessionRecord(session, FocusState.Unsupported)
            return FocusStartResult.Unsupported
        }
        sessions[session.id] = SessionRecord(session, FocusState.Focusing)
        return FocusStartResult.Started
    }

    fun finish(sessionId: String, distinctCount: Int): FocusState {
        val record = sessions[sessionId] ?: return FocusState.Idle
        if (record.state == FocusState.Idle) return FocusState.Idle
        if (record.state == FocusState.Unsupported) return FocusState.Unsupported
        if (!platform.disableZen()) {
            sessions[sessionId] = record.copy(state = FocusState.Unsupported)
            return FocusState.Unsupported
        }
        if (!record.summarySent) {
            platform.sendSummary(record.session, distinctCount)
            sessions[sessionId] = record.copy(state = FocusState.Idle, summarySent = true)
        } else {
            sessions[sessionId] = record.copy(state = FocusState.Idle)
        }
        return FocusState.Idle
    }

    fun isActive(sessionId: String): Boolean = sessions[sessionId]?.state == FocusState.Focusing

    private data class SessionRecord(
        val session: FocusSession,
        val state: FocusState,
        val summarySent: Boolean = false,
    )
}
