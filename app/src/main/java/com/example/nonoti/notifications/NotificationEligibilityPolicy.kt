package com.example.nonoti.notifications

class NotificationEligibilityPolicy(
    private val policy: NotificationPolicy,
) {
    fun evaluate(notification: NotificationSnapshot): NotificationEligibilityDecision {
        val reason = exclusionReason(notification)
        return if (reason == null) {
            NotificationEligibilityDecision(shouldCapture = true)
        } else {
            NotificationEligibilityDecision(shouldCapture = false, exclusionReason = reason)
        }
    }

    fun isEligible(notification: NotificationSnapshot): Boolean = evaluate(notification).shouldCapture

    private fun exclusionReason(notification: NotificationSnapshot): NotificationExclusionReason? =
        when {
            notification.packageName == policy.ownPackageName -> NotificationExclusionReason.OwnNotification
            notification.packageName in policy.alwaysAllowedPackages -> NotificationExclusionReason.AlwaysAllowedApp
            notification.isSystemGeneratedGroupSummary -> NotificationExclusionReason.SystemGeneratedGroupSummary
            notification.kind == NotificationKind.SystemEmergency -> NotificationExclusionReason.SystemEmergency
            notification.isWorkProfile -> NotificationExclusionReason.WorkProfile
            notification.kind == NotificationKind.Alarm -> NotificationExclusionReason.Alarm
            notification.kind == NotificationKind.Call ||
                notification.kind == NotificationKind.Media ||
                notification.kind == NotificationKind.Navigation -> NotificationExclusionReason.RealTimeStatus
            notification.kind == NotificationKind.SystemOngoing || notification.isOngoing ->
                NotificationExclusionReason.Ongoing
            !notification.canSnooze -> NotificationExclusionReason.CannotSnooze
            else -> null
        }
}
