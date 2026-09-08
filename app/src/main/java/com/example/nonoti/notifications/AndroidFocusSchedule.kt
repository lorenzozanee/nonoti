package com.example.nonoti.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.nonoti.NonotiApplication
import com.example.nonoti.focus.DailyFocusBlock
import com.example.nonoti.focus.FocusOccurrences
import com.example.nonoti.focus.FocusSchedulePort
import com.example.nonoti.focus.FocusSession
import com.example.nonoti.focus.FocusReadiness
import java.time.Instant
import java.time.ZonedDateTime

class AndroidFocusSchedule(private val context: Context) : FocusSchedulePort {
    override fun reconcile() {
        Thread {
            val app = context.applicationContext as NonotiApplication
            val blocks = app.database.dao().focusBlocksBlocking()
            val alarms = context.getSystemService(AlarmManager::class.java)
            val now = ZonedDateTime.now()
            for (entity in blocks) {
                val block = DailyFocusBlock(entity.startMinute, entity.endMinute)
                val occurrence = FocusOccurrences.currentOrNext(block, now)
                if (!now.isBefore(occurrence.start) && now.isBefore(occurrence.end)) {
                    startOccurrence(context, block, occurrence.start.toInstant(), occurrence.end.toInstant())
                    scheduleStart(alarms, block, FocusOccurrences.next(block, now).start.toInstant())
                } else {
                    scheduleStart(alarms, block, occurrence.start.toInstant())
                }
            }
        }.start()
    }

    override fun cancel(block: DailyFocusBlock) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(block),
            startIntent(block),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    private fun scheduleStart(alarms: AlarmManager?, block: DailyFocusBlock, start: Instant) {
        if (alarms?.canScheduleExactAlarms() != true) return
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(block),
            startIntent(block),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start.toEpochMilli(), pending)
    }

    private fun startIntent(block: DailyFocusBlock): Intent =
        Intent(context, FocusStartReceiver::class.java)
            .setAction(FocusStartReceiver.ACTION_START)
            .putExtra(FocusStartReceiver.EXTRA_START_MINUTE, block.startMinute)
            .putExtra(FocusStartReceiver.EXTRA_END_MINUTE, block.endMinute)

    private fun requestCode(block: DailyFocusBlock): Int = (block.startMinute shl 11) or block.endMinute

    companion object {
        fun startOccurrence(context: Context, block: DailyFocusBlock, start: Instant, end: Instant) {
            val platform = AndroidFocusPlatform(context.applicationContext)
            val id = "planned-${block.startMinute}-${block.endMinute}-${start.toEpochMilli()}"
            if (!PlannedFocusOccurrenceStore(context).shouldStart(id)) return
            NonotiPlatform.start(context, FocusSession(id, start, end), platform.readiness())
        }
    }
}

class FocusStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START) return
        val block = DailyFocusBlock(
            intent.getIntExtra(EXTRA_START_MINUTE, -1),
            intent.getIntExtra(EXTRA_END_MINUTE, -1),
        )
        if (block.startMinute !in 0..1439 || block.endMinute !in 0..1439) return
        val pendingResult = goAsync()
        Thread {
            try {
                val app = context.applicationContext as NonotiApplication
                val stillExists = app.database.dao().focusBlocksBlocking().any {
                    it.startMinute == block.startMinute && it.endMinute == block.endMinute
                }
                if (!stillExists) return@Thread
                val now = ZonedDateTime.now()
                val occurrence = FocusOccurrences.currentOrNext(block, now)
                if (!now.isBefore(occurrence.start) && now.isBefore(occurrence.end)) {
                    AndroidFocusSchedule.startOccurrence(context, block, occurrence.start.toInstant(), occurrence.end.toInstant())
                }
                AndroidFocusSchedule(context).reconcile()
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_START = "com.example.nonoti.action.FOCUS_START"
        const val EXTRA_START_MINUTE = "start_minute"
        const val EXTRA_END_MINUTE = "end_minute"
    }
}

class SystemChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SupportedActions) return
        if (intent.action in ClockActions) {
            val pendingResult = goAsync()
            Thread {
                try {
                    NonotiPlatform.handleClockChange(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }.start()
            return
        }
        if (intent.action == NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED) {
            val ruleId = intent.getStringExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_ID)
            val status = intent.getIntExtra(
                NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_STATUS,
                NotificationManager.AUTOMATIC_RULE_STATUS_UNKNOWN,
            )
            if (ZenRuleController(context).isOwnRuleId(ruleId) && status in RuleFailureStatuses) {
                ZenRuleController(context).markInactive()
                FocusRuntime.current()?.let { session ->
                    CompatibilityStore(context).invalidate()
                    NonotiPlatform.failOpen(context, session.id)
                }
                return
            }
        }
        if (intent.action == NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED) {
            FocusRuntime.current()?.let { session ->
                if (!AndroidFocusPlatform(context).readiness().notificationPolicyAccess) {
                    CompatibilityStore(context).invalidate()
                    NonotiPlatform.failOpen(context, session.id)
                    return
                }
            }
        }
        if (intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
            FocusRuntime.current()?.let { session ->
                if (InterruptionFilterChange.shouldFailOpen(session.id, AndroidFocusPlatform(context).isZenActive())) {
                    CompatibilityStore(context).invalidate()
                    NonotiPlatform.failOpen(context, session.id)
                    return
                }
            }
        }
        FocusRuntime.current()?.let { session ->
            val readiness = AndroidFocusPlatform(context).readiness()
            if (SystemCapabilityChange.shouldFailOpen(intent.action, readiness)) {
                CompatibilityStore(context).invalidate()
                NonotiPlatform.failOpen(context, session.id)
                return
            }
        }
        AndroidFocusSchedule(context).reconcile()
    }

    companion object {
        private val ClockActions = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            TimezoneOffsetChangedAction,
        )
        private val SupportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            TimezoneOffsetChangedAction,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED,
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
            NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED,
        )
        private val RuleFailureStatuses = setOf(
            NotificationManager.AUTOMATIC_RULE_STATUS_DISABLED,
            NotificationManager.AUTOMATIC_RULE_STATUS_REMOVED,
            AutomaticRuleStatusDeactivated,
        )
        private const val AutomaticRuleStatusDeactivated = 5
        private const val TimezoneOffsetChangedAction = "android.intent.action.TIMEZONE_OFFSET_CHANGED"
    }
}

object SystemCapabilityChange {
    fun shouldFailOpen(action: String?, readiness: FocusReadiness): Boolean =
        action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED && !readiness.exactAlarms
}

object InterruptionFilterChange {
    fun shouldFailOpen(sessionId: String?, ownZenActive: Boolean): Boolean =
        sessionId != null && !ownZenActive
}
