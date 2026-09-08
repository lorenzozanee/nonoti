package com.example.nonoti.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.nonoti.notifications.NonotiNotificationListenerService

enum class PermissionOnboardingStep {
    NotificationAccess,
    DoNotDisturb,
    ExactAlarms,
    PostNotifications,
}

data class PermissionReadiness(
    val postNotifications: Boolean,
    val notificationAccess: Boolean,
    val dndAccess: Boolean,
    val exactAlarms: Boolean,
)

object FirstRunPermissionFlow {
    fun next(
        readiness: PermissionReadiness,
        attempted: Set<PermissionOnboardingStep>,
    ): PermissionOnboardingStep? = PermissionOnboardingStep.entries.firstOrNull { step ->
        step !in attempted && !readiness.isGranted(step)
    }

    private fun PermissionReadiness.isGranted(step: PermissionOnboardingStep): Boolean = when (step) {
        PermissionOnboardingStep.NotificationAccess -> notificationAccess
        PermissionOnboardingStep.DoNotDisturb -> dndAccess
        PermissionOnboardingStep.ExactAlarms -> exactAlarms
        PermissionOnboardingStep.PostNotifications -> postNotifications
    }
}

class FirstRunPermissionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun isComplete(): Boolean = preferences.getBoolean(Complete, false)

    fun attempted(): Set<PermissionOnboardingStep> = preferences.getStringSet(Attempted, emptySet()).orEmpty()
        .mapNotNullTo(mutableSetOf()) { value -> PermissionOnboardingStep.entries.firstOrNull { it.name == value } }

    fun markAttempted(step: PermissionOnboardingStep) {
        preferences.edit().putStringSet(Attempted, (attempted() + step).mapTo(mutableSetOf()) { it.name }).apply()
    }

    fun markComplete() {
        preferences.edit().putBoolean(Complete, true).apply()
    }

    companion object {
        private const val PreferencesName = "first_run_permissions"
        private const val Attempted = "attempted"
        private const val Complete = "complete"
    }
}

fun PermissionOnboardingStep.settingsIntent(context: Context): Intent {
    val packageUri = Uri.parse("package:${context.packageName}")
    val intent = when (this) {
        PermissionOnboardingStep.PostNotifications -> error("Notification posting uses a runtime permission")
        PermissionOnboardingStep.NotificationAccess -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, NonotiNotificationListenerService::class.java).flattenToString(),
            )
        PermissionOnboardingStep.DoNotDisturb -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        PermissionOnboardingStep.ExactAlarms -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(packageUri)
    }
    return intent
}
