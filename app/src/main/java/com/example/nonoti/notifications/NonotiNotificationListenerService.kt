package com.example.nonoti.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.NotificationChannel
import android.content.ComponentName
import android.os.UserHandle
import com.example.nonoti.NonotiApplication
import com.example.nonoti.data.toEntity
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NonotiNotificationListenerService : NotificationListenerService() {
    private lateinit var policy: NotificationEligibilityPolicy
    private val io = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        policy = NotificationEligibilityPolicy(NotificationPolicy(packageName))
        restoreSnoozedState()
        FocusRuntime.setListenerConnected(true)
        scope.launch {
            (application as NonotiApplication).settingsRepository.settings.collect { settings ->
                policy = NotificationEligibilityPolicy(NotificationPolicy(packageName, settings.alwaysAllowedPackages))
            }
        }
        io.execute {
            NonotiPlatform.restore(applicationContext)
            runCatching { activeNotifications.orEmpty().forEach(::onNotificationPosted) }
        }
    }

    override fun onListenerDisconnected() {
        FocusRuntime.setListenerConnected(false)
        FocusRuntime.current()?.let { session ->
            NotificationListenerService.requestRebind(ComponentName(this, NonotiNotificationListenerService::class.java))
            io.execute {
                Thread.sleep(ListenerReconnectGraceMillis)
                val accessGranted = androidx.core.app.NotificationManagerCompat
                    .getEnabledListenerPackages(this)
                    .contains(packageName)
                if (ListenerConnectionChange.shouldFailOpen(
                        listenerConnected = FocusRuntime.isListenerConnected(),
                        accessGranted = accessGranted,
                    )
                ) {
                    CompatibilityStore(this).invalidate()
                    NonotiPlatform.failOpen(applicationContext, session.id)
                }
            }
        }
        super.onListenerDisconnected()
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        FocusRuntime.current()?.let { session ->
            if (!session.isUnsupported &&
                InterruptionFilterChange.shouldFailOpen(
                    session.id,
                    AndroidFocusPlatform(applicationContext).isZenActive(),
                )
            ) {
                CompatibilityStore(this).invalidate()
                io.execute { NonotiPlatform.failOpen(applicationContext, session.id) }
            }
        }
        super.onInterruptionFilterChanged(interruptionFilter)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!::policy.isInitialized) return
        val active = FocusRuntime.current() ?: return
        if (active.isUnsupported) return
        FocusRuntime.markRestored(active.id, sbn.key)
        val platform = AndroidFocusPlatform(applicationContext)
        val readiness = platform.readiness().let { if (active.id.startsWith("compatibility-")) it.copy(compatibility = true) else it }
        if (!readiness.isReady || !platform.isZenActive()) {
            CompatibilityStore(this).invalidate()
            io.execute { NonotiPlatform.failOpen(applicationContext, active.id) }
            return
        }
        val snapshot = FocusRuntime.snapshotFor(sbn, System.currentTimeMillis()) ?: return
        val compatibilityTest = snapshot.packageName == packageName && snapshot.channelId == CompatibilityTestController.ChannelId && CompatibilityStore(this).isTestRunning()
        if (!compatibilityTest && !policy.isEligible(snapshot)) return
        val remaining = (FocusRuntime.current()?.endAtMillis ?: return) - System.currentTimeMillis()
        val snoozeDuration = if (active.isReleasing) 0L else NotificationSnoozePolicy.durationMillis(remaining)
        val saved = FocusRuntime.store().upsert(snapshot).notification ?: return
        val captureResult = NotificationCaptureOrder.run(
            persist = {
                runCatching {
                    runBlocking(Dispatchers.IO) {
                        val dao = (application as NonotiApplication).database.dao()
                        if (!saved.isGroupSummary && saved.groupKey != null) {
                            dao.deleteGroupSummaries(saved.sessionId, saved.packageName, saved.groupKey)
                        }
                        dao.upsertNotificationBlocking(saved.toEntity())
                    }
                }.isSuccess
            },
            snooze = {
                snoozeDuration <= 0 || runCatching { snoozeNotification(sbn.key, snoozeDuration) }.isSuccess
            },
        )
        if (captureResult != NotificationCaptureResult.Captured) {
            CompatibilityStore(this).invalidate()
            io.execute { NonotiPlatform.failOpen(applicationContext, active.id) }
            return
        }
        if (snoozeDuration > 0) {
            FocusRuntime.markSnoozed(active.id, sbn.key)
            reconcileAfterSnooze(active.id, sbn.key, snoozeDuration)
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        if (reason == REASON_SNOOZED) return
        val session = FocusRuntime.current() ?: return
        FocusRuntime.store().markCancelled(session.id, sbn.key)
        io.execute { (application as NonotiApplication).database.dao().markNotificationCancelledBlocking(session.id, sbn.key) }
    }

    override fun onNotificationChannelModified(
        pkg: String,
        user: UserHandle,
        channel: NotificationChannel,
        modificationType: Int,
    ) {
        if (pkg == packageName && channel.id == "focus-summary") {
            FocusCapabilityGuard.check(applicationContext)
        }
    }

    override fun onDestroy() {
        FocusRuntime.setListenerConnected(false)
        super.onDestroy()
        io.shutdown()
        scope.cancel()
    }

    private fun restoreSnoozedState() {
        val dao = (application as NonotiApplication).database.dao()
        val persisted = runBlocking { dao.focusSession() } ?: return
        val capturedKeys = runBlocking {
            dao.observeNotifications(persisted.id).first().mapTo(mutableSetOf()) { it.notificationKey }
        }
        val snoozedKeys = runCatching {
            snoozedNotifications.orEmpty().mapTo(mutableSetOf()) { it.key }
        }.getOrDefault(emptySet())
        capturedKeys.intersect(snoozedKeys).forEach { key ->
            FocusRuntime.markSnoozed(persisted.id, key)
        }
    }

    private fun reconcileAfterSnooze(sessionId: String, notificationKey: String, durationMillis: Long) {
        scope.launch {
            delay(durationMillis + SnoozeReconciliationGraceMillis)
            if (FocusRuntime.current()?.id != sessionId) return@launch
            val knownToSystem = runCatching {
                activeNotifications.orEmpty().any { it.key == notificationKey } ||
                    snoozedNotifications.orEmpty().any { it.key == notificationKey }
            }.getOrDefault(true)
            if (knownToSystem) return@launch
            FocusRuntime.markRestored(sessionId, notificationKey)
            FocusRuntime.store().markCancelled(sessionId, notificationKey)
            (application as NonotiApplication).database.dao()
                .markNotificationCancelledBlocking(sessionId, notificationKey)
        }
    }

    private companion object {
        const val SnoozeReconciliationGraceMillis = 2_000L
        const val ListenerReconnectGraceMillis = 5_000L
    }
}

object ListenerConnectionChange {
    fun shouldFailOpen(listenerConnected: Boolean, accessGranted: Boolean): Boolean =
        !listenerConnected && !accessGranted
}

object NotificationSnoozePolicy {
    private const val MaximumSliceMillis = 60_000L

    fun durationMillis(remainingMillis: Long): Long = remainingMillis.coerceIn(0L, MaximumSliceMillis)

    fun releaseDelayMillis(remainingMillis: Long): Long =
        remainingMillis.coerceIn(0L, MaximumSliceMillis) + 5_000L
}
