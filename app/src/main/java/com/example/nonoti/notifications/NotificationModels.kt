package com.example.nonoti.notifications

enum class NotificationKind {
    Normal,
    Call,
    Alarm,
    Media,
    Navigation,
    SystemEmergency,
    SystemOngoing,
}

data class NotificationSnapshot(
    val sessionId: String,
    val notificationKey: String,
    val packageName: String,
    val capturedAtMillis: Long,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val channelId: String? = null,
    val conversationId: String? = null,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
    val isSystemGeneratedGroupSummary: Boolean = false,
    val updatedAtMillis: Long = capturedAtMillis,
    val kind: NotificationKind = NotificationKind.Normal,
    val isWorkProfile: Boolean = false,
    val isOngoing: Boolean = false,
    val canSnooze: Boolean = true,
    val isInSystemNotificationBar: Boolean = true,
)

data class BoxNotification(
    val sessionId: String,
    val notificationKey: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val subText: String?,
    val channelId: String?,
    val conversationId: String?,
    val groupKey: String?,
    val isGroupSummary: Boolean,
    val firstCapturedAtMillis: Long,
    val lastUpdatedAtMillis: Long,
    val updateCount: Int,
    val isInSystemNotificationBar: Boolean,
)

data class NotificationPolicy(
    val ownPackageName: String,
    val alwaysAllowedPackages: Set<String> = emptySet(),
)

enum class NotificationExclusionReason {
    OwnNotification,
    AlwaysAllowedApp,
    SystemEmergency,
    Ongoing,
    RealTimeStatus,
    Alarm,
    WorkProfile,
    CannotSnooze,
    SystemGeneratedGroupSummary,
}

data class NotificationEligibilityDecision(
    val shouldCapture: Boolean,
    val exclusionReason: NotificationExclusionReason? = null,
)

enum class NotificationUpsertAction {
    Inserted,
    Updated,
    IgnoredGroupSummary,
}

data class NotificationUpsertResult(
    val action: NotificationUpsertAction,
    val notification: BoxNotification?,
)
