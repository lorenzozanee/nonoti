package com.example.nonoti.notifications

import android.Manifest
import android.app.AppOpsManager
import android.content.Context

object FocusCapabilityGuard {
    fun failedSession(activeSessionId: String?, canPostSummary: Boolean): String? =
        activeSessionId?.takeIf { !canPostSummary }

    fun check(context: Context) {
        val platform = AndroidFocusPlatform(context.applicationContext)
        failedSession(FocusRuntime.current()?.id, platform.canPostSummary())?.let { sessionId ->
            CompatibilityStore(context).invalidate()
            Thread { NonotiPlatform.failOpen(context.applicationContext, sessionId) }.start()
        }
    }
}

class NotificationPermissionMonitor(private val context: Context) {
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val listener = AppOpsManager.OnOpChangedListener { _, packageName ->
        if (packageName == context.packageName) FocusCapabilityGuard.check(context)
    }

    fun start() {
        val operation = AppOpsManager.permissionToOp(Manifest.permission.POST_NOTIFICATIONS) ?: return
        appOps?.startWatchingMode(operation, context.packageName, listener)
    }
}
