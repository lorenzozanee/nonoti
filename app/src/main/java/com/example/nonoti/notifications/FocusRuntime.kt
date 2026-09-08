package com.example.nonoti.notifications

import android.service.notification.StatusBarNotification
import android.app.PendingIntent
import android.content.Context
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ActiveFocusSession(
    val id: String,
    val endAtMillis: Long,
    val isReleasing: Boolean = false,
    val isUnsupported: Boolean = false,
    val failureReason: String? = null,
)

object FocusRuntime {
    private val activeSession = AtomicReference<ActiveFocusSession?>(null)
    private val listenerConnected = AtomicBoolean(false)
    private val activeState = MutableStateFlow<ActiveFocusSession?>(null)
    private val store = InMemoryNotificationBoxStore()
    private val contentIntents = java.util.concurrent.ConcurrentHashMap<String, PendingIntent>()

    fun start(session: ActiveFocusSession) {
        activeSession.set(session)
        activeState.value = session
    }

    fun stop() {
        activeSession.set(null)
        activeState.value = null
    }

    fun beginRelease(sessionId: String) {
        val current = activeSession.get() ?: return
        if (current.id != sessionId || current.isReleasing) return
        val releasing = current.copy(isReleasing = true)
        activeSession.set(releasing)
        activeState.value = releasing
    }

    fun showUnsupported(sessionId: String, endAtMillis: Long, failureReason: String) {
        val unsupported = ActiveFocusSession(
            id = sessionId,
            endAtMillis = endAtMillis,
            isReleasing = true,
            isUnsupported = true,
            failureReason = failureReason,
        )
        activeSession.set(unsupported)
        activeState.value = unsupported
    }

    fun current(): ActiveFocusSession? = activeSession.get()

    fun setListenerConnected(connected: Boolean) {
        listenerConnected.set(connected)
    }

    fun isListenerConnected(): Boolean = listenerConnected.get()

    val state: StateFlow<ActiveFocusSession?> = activeState

    fun store(): NotificationBoxStore = store

    fun snapshotFor(sbn: StatusBarNotification, nowMillis: Long): NotificationSnapshot? {
        val session = activeSession.get() ?: return null
        sbn.notification.contentIntent?.let { contentIntents[identity(session.id, sbn.key)] = it }
        return NotificationPlatformAdapter.toSnapshot(sbn, session.id, nowMillis)
    }

    fun openSource(context: Context, notification: BoxNotification): Boolean {
        val pending = contentIntents.remove(identity(notification.sessionId, notification.notificationKey))
        if (pending != null && runCatching { pending.send() }.isSuccess) return true
        val launch = context.packageManager.getLaunchIntentForPackage(notification.packageName) ?: return false
        return runCatching { context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    private fun identity(sessionId: String, key: String) = "$sessionId\u0000$key"
}
