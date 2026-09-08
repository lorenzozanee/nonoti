package com.example.nonoti.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import com.example.nonoti.NonotiApplication
import com.example.nonoti.focus.FocusSession
import com.example.nonoti.notifications.AndroidFocusPlatform
import com.example.nonoti.notifications.CompatibilityStore
import com.example.nonoti.notifications.FocusRuntime
import com.example.nonoti.notifications.FocusStartResult
import com.example.nonoti.notifications.NonotiPlatform
import java.time.Instant
import kotlinx.coroutines.runBlocking

class DebugTestControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                pending.resultData = when (intent.action) {
                    ActionReset -> reset(context)
                    ActionStart -> start(context)
                    ActionQuery -> query(context)
                    else -> "error=unknown_action"
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun reset(context: Context): String {
        val app = context.applicationContext as NonotiApplication
        FocusRuntime.current()?.let { NonotiPlatform.failOpen(context, it.id) }
        AndroidFocusPlatform(context).disableZen()
        FocusRuntime.stop()
        runBlocking { app.database.dao().clearFocusSession() }
        CompatibilityStore(context).apply {
            beginTest()
            markTestCompleted()
            markPassed()
        }
        return query(context)
    }

    private fun start(context: Context): String {
        val now = Instant.now()
        val session = FocusSession("debug-host-${now.toEpochMilli()}", now, now.plusSeconds(600))
        val platform = AndroidFocusPlatform(context)
        val result = NonotiPlatform.start(context, session, platform.readiness())
        return "start=$result;${query(context)}"
    }

    private fun query(context: Context): String {
        val app = context.applicationContext as NonotiApplication
        val persisted = runBlocking { app.database.dao().focusSession() }
        val notificationCount = persisted?.let { app.database.dao().notificationCountBlocking(it.id) } ?: 0
        val runtime = FocusRuntime.current()
        val platform = AndroidFocusPlatform(context)
        return listOf(
            "pid=${Process.myPid()}",
            "persisted=${persisted?.state ?: "None"}",
            "runtime=${when {
                runtime == null -> "None"
                runtime.isUnsupported -> "Unsupported"
                runtime.isReleasing -> "Releasing"
                else -> "Focusing"
            }}",
            "zen=${platform.isZenActive()}",
            "post=${platform.canPostSummary()}",
            "listener=${platform.readiness().notificationAccess}",
            "policy=${platform.readiness().notificationPolicyAccess}",
            "exact=${platform.readiness().exactAlarms}",
            "notifications=$notificationCount",
        ).joinToString(";")
    }

    private companion object {
        const val ActionReset = "com.example.nonoti.debug.RESET"
        const val ActionStart = "com.example.nonoti.debug.START"
        const val ActionQuery = "com.example.nonoti.debug.QUERY"
    }
}
