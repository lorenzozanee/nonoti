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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NonotiNotificationListenerService : NotificationListenerService() {
    private lateinit var policy: NotificationEligibilityPolicy
    private val io = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        policy = NotificationEligibilityPolicy(NotificationPolicy(packageName))
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
        val saved = FocusRuntime.store().upsert(snapshot).notification ?: return
        val persisted = runCatching {
            runBlocking(Dispatchers.IO) {
                val dao = (application as NonotiApplication).database.dao()
                if (!saved.isGroupSummary && saved.groupKey != null) {
                    dao.deleteGroupSummaries(saved.sessionId, saved.packageName, saved.groupKey)
                }
                dao.upsertNotificationBlocking(saved.toEntity())
            }
        }.isSuccess
        if (!persisted) {
            CompatibilityStore(this).invalidate()
            io.execute { NonotiPlatform.failOpen(applicationContext, active.id) }
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

    private companion object {
        const val ListenerReconnectGraceMillis = 5_000L
    }
}

object ListenerConnectionChange {
    fun shouldFailOpen(listenerConnected: Boolean, accessGranted: Boolean): Boolean =
        !listenerConnected && !accessGranted
}
