package com.example.nonoti.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import androidx.core.app.NotificationCompat
import com.example.nonoti.R
import com.example.nonoti.focus.FocusReadiness
import com.example.nonoti.focus.FocusSession
import com.example.nonoti.focus.DailyFocusBlock
import com.example.nonoti.focus.FocusOccurrences
import java.time.Instant
import java.time.ZonedDateTime
import com.example.nonoti.NonotiApplication
import com.example.nonoti.MainActivity
import com.example.nonoti.data.FocusSessionEntity
import com.example.nonoti.focus.FocusState
import com.example.nonoti.focus.FocusSessionRules
import com.example.nonoti.focus.FocusWindow
import kotlinx.coroutines.runBlocking
import android.service.notification.NotificationListenerService

class AndroidFocusPlatform(private val context: Context) : FocusPlatformPort {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val zen = ZenRuleController(context)

    override fun enableZen(): Boolean = zen.ensureRule() != null && zen.setEnabled(true)

    override fun disableZen(): Boolean = zen.setEnabled(false)

    override fun scheduleEnd(session: FocusSession): Boolean {
        val intent = Intent(context, FocusEndReceiver::class.java).setAction(FocusEndReceiver.ACTION_END).putExtra(FocusEndReceiver.EXTRA_SESSION_ID, session.id)
        val pending = PendingIntent.getBroadcast(context, session.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fallback = PendingIntent.getBroadcast(
            context,
            session.id.hashCode() xor Int.MIN_VALUE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (alarms?.canScheduleExactAlarms() != true) return false
        return runCatching {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.end.toEpochMilli(), pending)
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                session.end.plusSeconds(120).toEpochMilli(),
                fallback,
            )
            true
        }.getOrDefault(false)
    }

    override fun sendSummary(session: FocusSession, distinctCount: Int) {
        ensureSummaryChannel()
        val notification = NotificationCompat.Builder(context, SummaryChannelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.summary_title))
            .setContentText(context.resources.getQuantityString(R.plurals.summary_text, distinctCount, distinctCount))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    7,
                    Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_BOX),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        notifications?.notify(session.id.hashCode(), notification)
    }

    fun readiness(): FocusReadiness {
        val notificationAccess = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) &&
            FocusRuntime.isListenerConnected()
        val policyAccess = notifications?.isNotificationPolicyAccessGranted == true
        val exactAlarms = alarms?.canScheduleExactAlarms() ?: false
        ensureSummaryChannel()
        val summaryChannelEnabled = notifications?.getNotificationChannel(SummaryChannelId)?.importance != NotificationManager.IMPORTANCE_NONE
        val postNotifications = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() && summaryChannelEnabled
        val zenRule = if (policyAccess) zen.ensureRule() != null else false
        return FocusReadiness(notificationAccess, policyAccess, zenRule, exactAlarms, postNotifications, compatibility = CompatibilityStore(context).isPassed())
    }

    fun isZenActive(): Boolean = ZenRuleController(context).isActive()

    fun canPostSummary(): Boolean {
        ensureSummaryChannel()
        val permission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val channel = notifications?.getNotificationChannel(SummaryChannelId)
        return permission &&
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun cancelEnd(sessionId: String) {
        val intent = Intent(context, FocusEndReceiver::class.java).setAction(FocusEndReceiver.ACTION_END)
        listOf(sessionId.hashCode(), sessionId.hashCode() xor Int.MIN_VALUE).forEach { requestCode ->
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { pending ->
                alarms?.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun ensureSummaryChannel() {
        notifications?.createNotificationChannel(NotificationChannel(SummaryChannelId, context.getString(R.string.summary_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private companion object {
        const val SummaryChannelId = "focus-summary"
    }
}

class FocusEndReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_END) return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        if (!FocusForegroundService.release(context.applicationContext, sessionId)) {
            NonotiPlatform.release(context.applicationContext, sessionId)
        }
    }

    companion object {
        const val ACTION_END = "com.example.nonoti.action.FOCUS_END"
        const val EXTRA_SESSION_ID = "session_id"
    }
}

object NonotiPlatform {
    private var coordinator: FocusPlatformCoordinator? = null

    @Synchronized
    fun start(context: Context, session: FocusSession, readiness: FocusReadiness): FocusStartResult {
        val app = context.applicationContext as NonotiApplication
        val persisted = runBlocking { app.database.dao().focusSession() }
        val continuing = persisted != null && persisted.state == FocusState.Focusing && persisted.endMillis > System.currentTimeMillis()
        val effectiveSession = if (continuing) {
            val merged = FocusSessionRules.mergeWithActive(
                FocusWindow(Instant.ofEpochMilli(persisted.startMillis), Instant.ofEpochMilli(persisted.endMillis)),
                FocusWindow(session.start, session.end),
            ) ?: return FocusStartResult.Rejected
            FocusSession(persisted.id, merged.start, merged.end)
        } else {
            session
        }
        if (continuing) {
            val platform = AndroidFocusPlatform(context.applicationContext)
            if (!readiness.isReady || !platform.isZenActive()) {
                CompatibilityStore(context).invalidate()
                failOpen(context, persisted.id)
                return FocusStartResult.Unsupported
            }
            platform.cancelEnd(persisted.id)
            if (!platform.scheduleEnd(effectiveSession)) {
                CompatibilityStore(context).invalidate()
                failOpen(context, persisted.id)
                return FocusStartResult.Unsupported
            }
            FocusRuntime.start(ActiveFocusSession(effectiveSession.id, effectiveSession.end.toEpochMilli()))
            if (!FocusForegroundService.start(context)) {
                failOpen(context, persisted.id)
                return FocusStartResult.Unsupported
            }
            runBlocking {
                app.database.dao().upsertFocusSession(
                    persisted.copy(
                        startMillis = effectiveSession.start.toEpochMilli(),
                        endMillis = effectiveSession.end.toEpochMilli(),
                        state = FocusState.Focusing,
                        failureReason = null,
                    ),
                )
            }
            return FocusStartResult.Started
        }
        if (!readiness.isReady) return FocusStartResult.Rejected
        val active = FocusPlatformCoordinator(AndroidFocusPlatform(context.applicationContext))
        val result = FocusActivationTransaction.run(
            persistRecoveryMarker = {
                runCatching {
                    runBlocking {
                        app.database.dao().upsertFocusSession(
                            effectiveSession.toEntity(FocusState.Unsupported).copy(failureReason = "focus activation incomplete"),
                        )
                    }
                }.isSuccess
            },
            activate = { active.start(effectiveSession, readiness) },
            persistActive = {
                runCatching {
                    check(FocusForegroundService.start(context))
                    coordinator = active
                    FocusRuntime.start(ActiveFocusSession(effectiveSession.id, effectiveSession.end.toEpochMilli()))
                    runBlocking { app.database.dao().upsertFocusSession(effectiveSession.toEntity(FocusState.Focusing)) }
                }.isSuccess
            },
            rollback = {
                val platform = AndroidFocusPlatform(context.applicationContext)
                platform.disableZen()
                platform.cancelEnd(effectiveSession.id)
                coordinator = null
                FocusRuntime.stop()
                FocusForegroundService.stop(context)
                runBlocking { app.database.dao().clearFocusSession() }
            },
        )
        return result
    }

    @Synchronized
    fun finish(context: Context, sessionId: String, releaseTimedOut: Boolean = false) {
        val app = context.applicationContext as NonotiApplication
        val persisted = runBlocking { app.database.dao().focusSession() }
        if (!FocusSessionIdentity.isCurrent(sessionId, persisted?.id, FocusRuntime.current()?.id)) return
        val persistedEnd = persisted?.endMillis ?: 0L
        val pendingSnoozed = FocusRuntime.pendingSnoozedCount(sessionId)
        val count = maxOf(FocusRuntime.store().distinctCount(sessionId), app.database.dao().notificationCountBlocking(sessionId))
        val platform = AndroidFocusPlatform(context.applicationContext)
        val summaryReady = platform.canPostSummary()
        if (!platform.disableZen()) {
            CompatibilityStore(context).invalidate()
            if (persisted?.id == sessionId) {
                runBlocking {
                    app.database.dao().upsertFocusSession(
                        persisted.copy(state = FocusState.Unsupported, failureReason = "unable to disable focus rule"),
                    )
                }
            }
            coordinator = null
            FocusRuntime.showUnsupported(sessionId, persisted?.endMillis ?: System.currentTimeMillis(), "unable to disable focus rule")
            FocusForegroundService.stop(context)
            return
        }
        platform.cancelEnd(sessionId)
        if (!summaryReady) {
            CompatibilityStore(context).invalidate()
            if (persisted?.id == sessionId) {
                runBlocking {
                    app.database.dao().upsertFocusSession(
                        persisted.copy(state = FocusState.Unsupported, failureReason = "summary notification unavailable"),
                    )
                }
            }
            coordinator = null
            FocusRuntime.showUnsupported(sessionId, persisted?.endMillis ?: System.currentTimeMillis(), "summary notification unavailable")
            FocusForegroundService.stop(context)
            AndroidFocusSchedule(context).reconcile()
            return
        }
        val start = persisted?.startMillis ?: 0L
        val end = persisted?.endMillis ?: 0L
        if (start > 0 && end > start && persisted != null && !persisted.summarySent) {
            platform.sendSummary(FocusSession(sessionId, Instant.ofEpochMilli(start), Instant.ofEpochMilli(end)), count)
            runBlocking {
                app.database.dao().upsertFocusSession(persisted.copy(summarySent = true))
            }
        }
        if (releaseTimedOut) {
            CompatibilityStore(context).invalidate()
            if (persisted?.id == sessionId) {
                runBlocking {
                    app.database.dao().upsertFocusSession(
                        persisted.copy(
                            state = FocusState.Unsupported,
                            failureReason = "notifications did not restore before release timeout",
                            summarySent = true,
                        ),
                    )
                }
            }
            if (sessionId.startsWith("compatibility-")) CompatibilityStore(context).cancelTest()
            coordinator = null
            FocusRuntime.showUnsupported(
                sessionId,
                persisted?.endMillis ?: System.currentTimeMillis(),
                "notifications did not restore before release timeout",
            )
            FocusForegroundService.stop(context)
            AndroidFocusSchedule(context).reconcile()
            return
        }
        runBlocking { app.database.dao().clearFocusSession() }
        if (sessionId.startsWith("compatibility-")) {
            if (summaryReady && pendingSnoozed == 0 && count > 0 && System.currentTimeMillis() >= persistedEnd) CompatibilityStore(context).markTestCompleted()
            else CompatibilityStore(context).cancelTest()
        }
        coordinator = null
        FocusRuntime.stop()
        FocusForegroundService.stop(context)
        AndroidFocusSchedule(context).reconcile()
    }

    fun release(context: Context, sessionId: String) {
        Thread { releaseBlocking(context, sessionId) }.start()
    }

    fun releaseBlocking(context: Context, sessionId: String) {
        FocusRuntime.beginRelease(sessionId)
        val app = context.applicationContext as NonotiApplication
        val persisted = runBlocking { app.database.dao().focusSession() }
        val end = persisted?.endMillis ?: 0L
        if (persisted?.id == sessionId) {
            runBlocking { app.database.dao().upsertFocusSession(persisted.copy(state = FocusState.Releasing)) }
        }
        val timeout = NotificationSnoozePolicy.releaseDelayMillis(end - System.currentTimeMillis())
        try {
            val startedAt = System.currentTimeMillis()
            val deadline = startedAt + timeout
            while (System.currentTimeMillis() < deadline &&
                (System.currentTimeMillis() - startedAt < 5_000L || FocusRuntime.pendingSnoozedCount(sessionId) > 0)
            ) {
                Thread.sleep(250L)
            }
            val releaseTimedOut = FocusRuntime.pendingSnoozedCount(sessionId) > 0
            finish(context, sessionId, releaseTimedOut)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            failOpen(context, sessionId)
        }
    }

    @Synchronized
    fun failOpen(context: Context, sessionId: String) {
        val app = context.applicationContext as NonotiApplication
        val persisted = runBlocking { app.database.dao().focusSession() }
        if (!FocusSessionIdentity.isCurrent(sessionId, persisted?.id, FocusRuntime.current()?.id)) return
        val platform = AndroidFocusPlatform(context.applicationContext)
        val zenDisabled = platform.disableZen()
        if (zenDisabled) platform.cancelEnd(sessionId)
        if (persisted?.id == sessionId) {
            runBlocking {
                app.database.dao().upsertFocusSession(
                    persisted.copy(state = FocusState.Unsupported, failureReason = "platform capability lost"),
                )
            }
        }
        coordinator = null
        FocusRuntime.showUnsupported(
            sessionId,
            persisted?.endMillis ?: System.currentTimeMillis(),
            "platform capability lost",
        )
        FocusForegroundService.stop(context)
        AndroidFocusSchedule(context).reconcile()
    }

    @Synchronized
    fun restore(context: Context) {
        val app = context.applicationContext as NonotiApplication
        val persisted = runBlocking { app.database.dao().focusSession() }
        if (persisted == null) {
            val platform = AndroidFocusPlatform(context)
            if (platform.isZenActive()) {
                CompatibilityStore(context).invalidate()
                if (platform.disableZen()) {
                    FocusRuntime.stop()
                } else {
                    FocusRuntime.showUnsupported(
                        "stale-focus-recovery",
                        System.currentTimeMillis(),
                        "unable to disable stale focus rule",
                    )
                }
            }
            FocusForegroundService.stop(context)
            return
        }
        if (persisted.state == FocusState.Unsupported) {
            val platform = AndroidFocusPlatform(context)
            if (platform.disableZen()) {
                platform.cancelEnd(persisted.id)
                runBlocking { app.database.dao().clearFocusSession() }
                FocusRuntime.stop()
            } else {
                FocusRuntime.showUnsupported(
                    persisted.id,
                    persisted.endMillis,
                    persisted.failureReason ?: "recovery required",
                )
            }
            FocusForegroundService.stop(context)
            return
        }
        if (persisted.state == FocusState.Releasing) {
            awaitListenerConnection(context)
            if (!FocusRuntime.isListenerConnected()) {
                CompatibilityStore(context).invalidate()
                failOpen(context, persisted.id)
                return
            }
            FocusRuntime.start(ActiveFocusSession(persisted.id, persisted.endMillis, isReleasing = true))
            release(context, persisted.id)
            return
        }
        val id = persisted.id
        if (FocusRuntime.current()?.id == id) return
        awaitListenerConnection(context)
        val start = persisted.startMillis
        val end = persisted.endMillis
        if (start <= 0 || end <= start) {
            val platform = AndroidFocusPlatform(context)
            if (platform.disableZen()) {
                platform.cancelEnd(id)
                runBlocking { app.database.dao().clearFocusSession() }
            } else {
                runBlocking {
                    app.database.dao().upsertFocusSession(
                        persisted.copy(state = FocusState.Unsupported, failureReason = "invalid session could not disable focus rule"),
                    )
                }
                FocusRuntime.showUnsupported(id, end, "invalid session could not disable focus rule")
            }
            FocusForegroundService.stop(context)
            return
        }
        if (end <= System.currentTimeMillis()) {
            finish(context, id)
            return
        }
        if (!ZenRuleConfirmation.canVerifyRestore(android.os.Build.VERSION.SDK_INT)) {
            CompatibilityStore(context).invalidate()
            failOpen(context, id)
            return
        }
        val session = FocusSession(id, Instant.ofEpochMilli(start), Instant.ofEpochMilli(end))
        val platform = AndroidFocusPlatform(context)
        if (!platform.readiness().isReady || !platform.isZenActive() || !platform.scheduleEnd(session)) {
            CompatibilityStore(context).invalidate()
            failOpen(context, id)
            return
        }
        FocusRuntime.start(ActiveFocusSession(id, end))
        if (!FocusForegroundService.start(context)) {
            failOpen(context, id)
        }
    }

    @Synchronized
    fun handleClockChange(context: Context) {
        val app = context.applicationContext as NonotiApplication
        val persisted = runBlocking { app.database.dao().focusSession() }
        val platform = AndroidFocusPlatform(context.applicationContext)
        if (persisted == null) {
            if (platform.isZenActive() && !platform.disableZen()) {
                CompatibilityStore(context).invalidate()
                return
            }
            AndroidFocusSchedule(context).reconcile()
            return
        }

        if (persisted.state == FocusState.Focusing && persisted.id.startsWith("planned-")) {
            val blocks = app.database.dao().focusBlocksBlocking().map { entity ->
                DailyFocusBlock(entity.startMinute, entity.endMinute)
            }
            val activeOccurrence = FocusOccurrences.active(blocks, ZonedDateTime.now())
            if (activeOccurrence != null) {
                val readiness = platform.readiness()
                if (!readiness.isReady || !platform.isZenActive()) {
                    CompatibilityStore(context).invalidate()
                    failOpen(context, persisted.id)
                    return
                }
                val updated = FocusSession(
                    persisted.id,
                    activeOccurrence.start.toInstant(),
                    activeOccurrence.end.toInstant(),
                )
                platform.cancelEnd(persisted.id)
                if (!platform.scheduleEnd(updated)) {
                    CompatibilityStore(context).invalidate()
                    failOpen(context, persisted.id)
                    return
                }
                runBlocking {
                    app.database.dao().upsertFocusSession(
                        persisted.copy(
                            startMillis = updated.start.toEpochMilli(),
                            endMillis = updated.end.toEpochMilli(),
                            state = FocusState.Focusing,
                            failureReason = null,
                        ),
                    )
                }
                FocusRuntime.start(ActiveFocusSession(updated.id, updated.end.toEpochMilli()))
                AndroidFocusSchedule(context).reconcile()
                return
            }
        }

        release(context, persisted.id)
    }

    private fun awaitListenerConnection(context: Context) {
        if (FocusRuntime.isListenerConnected()) return
        val enabled = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!enabled) return
        NotificationListenerService.requestRebind(
            ComponentName(context, NonotiNotificationListenerService::class.java),
        )
        val deadline = System.currentTimeMillis() + 5_000L
        while (!FocusRuntime.isListenerConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L)
        }
    }

    @Synchronized
    fun extend(context: Context, minutes: Long): Boolean {
        val persisted = runBlocking { (context.applicationContext as NonotiApplication).database.dao().focusSession() } ?: return false
        val id = persisted.id
        val start = persisted.startMillis
        val currentEnd = persisted.endMillis
        if (start <= 0 || currentEnd <= start) return false
        val extended = FocusSession(id, Instant.ofEpochMilli(start), Instant.ofEpochMilli(currentEnd).plusSeconds(minutes * 60))
        return start(context, extended, AndroidFocusPlatform(context).readiness()) == FocusStartResult.Started
    }

    @Synchronized
    fun extendTo(context: Context, requestedEnd: Instant): Boolean {
        val persisted = runBlocking { (context.applicationContext as NonotiApplication).database.dao().focusSession() } ?: return false
        val id = persisted.id
        val start = persisted.startMillis
        val currentEnd = persisted.endMillis
        if (start <= 0 || currentEnd <= start || requestedEnd.toEpochMilli() <= currentEnd) return false
        val extended = FocusSession(id, Instant.ofEpochMilli(start), requestedEnd)
        return start(context, extended, AndroidFocusPlatform(context).readiness()) == FocusStartResult.Started
    }
}

private fun FocusSession.toEntity(state: FocusState): FocusSessionEntity = FocusSessionEntity(
    id = id,
    startMillis = start.toEpochMilli(),
    endMillis = end.toEpochMilli(),
    state = state,
)

object FocusSessionIdentity {
    fun isCurrent(requestedId: String, persistedId: String?, runtimeId: String?): Boolean =
        requestedId == persistedId || requestedId == runtimeId
}
