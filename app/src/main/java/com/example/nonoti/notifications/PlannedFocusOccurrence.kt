package com.example.nonoti.notifications

import android.content.Context

object PlannedFocusOccurrence {
    private const val Prefix = "planned-"

    fun shouldStart(candidateId: String, skippedId: String?): Boolean =
        !candidateId.startsWith(Prefix) || candidateId != skippedId

    fun isPlanned(sessionId: String): Boolean = sessionId.startsWith(Prefix)
}

class PlannedFocusOccurrenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("planned_focus_occurrence", Context.MODE_PRIVATE)

    fun skip(sessionId: String) {
        if (PlannedFocusOccurrence.isPlanned(sessionId)) {
            preferences.edit().putString(SkippedId, sessionId).commit()
        }
    }

    fun shouldStart(sessionId: String): Boolean =
        PlannedFocusOccurrence.shouldStart(sessionId, preferences.getString(SkippedId, null))

    private companion object {
        const val SkippedId = "skipped_id"
    }
}
