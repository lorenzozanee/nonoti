package com.example.nonoti.notifications

enum class NotificationCaptureResult {
    Captured,
    PersistenceFailed,
    SnoozeFailed,
}

object NotificationCaptureOrder {
    fun run(persist: () -> Boolean, snooze: () -> Boolean): NotificationCaptureResult {
        if (!persist()) return NotificationCaptureResult.PersistenceFailed
        if (!snooze()) return NotificationCaptureResult.SnoozeFailed
        return NotificationCaptureResult.Captured
    }
}
