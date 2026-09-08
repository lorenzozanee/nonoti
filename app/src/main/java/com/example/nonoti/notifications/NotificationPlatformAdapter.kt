package com.example.nonoti.notifications

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification

object NotificationPlatformAdapter {
    fun toSnapshot(sbn: StatusBarNotification, sessionId: String, nowMillis: Long): NotificationSnapshot {
        val notification = sbn.notification
        val extras = notification.extras
        return NotificationSnapshot(
            sessionId = sessionId,
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            capturedAtMillis = nowMillis,
            title = extras.getCharSequence("android.title")?.toString(),
            text = extras.getCharSequence("android.text")?.toString(),
            subText = extras.getCharSequence("android.subText")?.toString(),
            channelId = notification.channelId,
            groupKey = notification.group,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isSystemGeneratedGroupSummary = notification.flags and FlagAutogroupSummary != 0,
            kind = notification.category.toNotificationKind(sbn.isOngoing),
            isWorkProfile = sbn.user != Process.myUserHandle(),
            isOngoing = sbn.isOngoing,
            canSnooze = sbn.isClearable,
        )
    }

    private fun String?.toNotificationKind(ongoing: Boolean): NotificationKind = when (this) {
        Notification.CATEGORY_CALL -> NotificationKind.Call
        Notification.CATEGORY_ALARM -> NotificationKind.Alarm
        Notification.CATEGORY_TRANSPORT -> NotificationKind.Media
        Notification.CATEGORY_NAVIGATION -> NotificationKind.Navigation
        Notification.CATEGORY_SYSTEM -> NotificationKind.SystemEmergency
        else -> if (ongoing) NotificationKind.SystemOngoing else NotificationKind.Normal
    }

    private const val FlagAutogroupSummary = 0x00000400
}
