package com.example.nonoti.notifications

interface NotificationBoxStore {
    fun upsert(notification: NotificationSnapshot): NotificationUpsertResult

    fun notifications(sessionId: String): List<BoxNotification>

    fun distinctCount(sessionId: String): Int

    fun distinctCount(sessionId: String, packageName: String): Int

    fun markCancelled(sessionId: String, notificationKey: String): Boolean
}

class InMemoryNotificationBoxStore : NotificationBoxStore {
    private val notifications = linkedMapOf<NotificationIdentity, BoxNotification>()

    @Synchronized
    override fun upsert(notification: NotificationSnapshot): NotificationUpsertResult {
        val identity = NotificationIdentity(notification.sessionId, notification.notificationKey)

        if (notification.isGroupSummary && hasChild(notification)) {
            notifications.remove(identity)
            return NotificationUpsertResult(
                action = NotificationUpsertAction.IgnoredGroupSummary,
                notification = null,
            )
        }

        if (!notification.isGroupSummary && notification.groupKey != null) {
            removeSummaryForGroup(notification)
        }

        val previous = notifications[identity]
        val saved = if (previous == null) {
            notification.toBoxNotification()
        } else {
            previous.updatedFrom(notification)
        }
        notifications[identity] = saved

        return NotificationUpsertResult(
            action = if (previous == null) NotificationUpsertAction.Inserted else NotificationUpsertAction.Updated,
            notification = saved,
        )
    }

    @Synchronized
    override fun notifications(sessionId: String): List<BoxNotification> =
        notifications
            .filterKeys { it.sessionId == sessionId }
            .values
            .toList()

    @Synchronized
    override fun distinctCount(sessionId: String): Int =
        notifications.count { it.key.sessionId == sessionId }

    @Synchronized
    override fun distinctCount(sessionId: String, packageName: String): Int =
        notifications.count { entry ->
            entry.key.sessionId == sessionId && entry.value.packageName == packageName
        }

    @Synchronized
    override fun markCancelled(sessionId: String, notificationKey: String): Boolean {
        val identity = NotificationIdentity(sessionId, notificationKey)
        val previous = notifications[identity] ?: return false
        notifications[identity] = previous.copy(isInSystemNotificationBar = false)
        return true
    }

    private fun hasChild(summary: NotificationSnapshot): Boolean =
        summary.groupKey != null &&
            notifications.values.any { saved ->
                saved.sessionId == summary.sessionId &&
                    saved.packageName == summary.packageName &&
                    saved.groupKey == summary.groupKey &&
                    !saved.isGroupSummary
            }

    private fun removeSummaryForGroup(child: NotificationSnapshot) {
        val groupKey = child.groupKey ?: return
        val summaryIdentities =
            notifications
                .filter { (_, saved) ->
                    saved.sessionId == child.sessionId &&
                        saved.packageName == child.packageName &&
                        saved.groupKey == groupKey &&
                        saved.isGroupSummary
                }
                .keys
                .toList()
        summaryIdentities.forEach(notifications::remove)
    }

    private data class NotificationIdentity(
        val sessionId: String,
        val notificationKey: String,
    )
}

private fun NotificationSnapshot.toBoxNotification(): BoxNotification =
    BoxNotification(
        sessionId = sessionId,
        notificationKey = notificationKey,
        packageName = packageName,
        title = title,
        text = text,
        subText = subText,
        channelId = channelId,
        conversationId = conversationId,
        groupKey = groupKey,
        isGroupSummary = isGroupSummary,
        firstCapturedAtMillis = capturedAtMillis,
        lastUpdatedAtMillis = updatedAtMillis,
        updateCount = 1,
        isInSystemNotificationBar = isInSystemNotificationBar,
    )

private fun BoxNotification.updatedFrom(notification: NotificationSnapshot): BoxNotification =
    copy(
        packageName = notification.packageName,
        title = notification.title,
        text = notification.text,
        subText = notification.subText,
        channelId = notification.channelId,
        conversationId = notification.conversationId,
        groupKey = notification.groupKey,
        isGroupSummary = notification.isGroupSummary,
        lastUpdatedAtMillis = maxOf(lastUpdatedAtMillis, notification.updatedAtMillis),
        updateCount = updateCount + 1,
        isInSystemNotificationBar = notification.isInSystemNotificationBar,
    )
