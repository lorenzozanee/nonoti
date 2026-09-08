package com.example.nonoti.data

import com.example.nonoti.notifications.BoxNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BoxRepository @Inject constructor(private val dao: NonotiDao) {
    val notifications: Flow<List<BoxNotification>> = dao.observeAllNotifications().map { items ->
        items.map { it.toDomain() }
    }

    suspend fun clear() = dao.clearNotifications()

    suspend fun deleteOlderThan(cutoffMillis: Long, activeSessionId: String? = null) =
        dao.deleteNotificationsOlderThan(cutoffMillis, activeSessionId)
}

fun BoxNotification.toEntity() = CapturedNotificationEntity(
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
    firstCapturedAtMillis = firstCapturedAtMillis,
    lastUpdatedAtMillis = lastUpdatedAtMillis,
    updateCount = updateCount,
    isInSystemNotificationBar = isInSystemNotificationBar,
)

private fun CapturedNotificationEntity.toDomain() = BoxNotification(
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
    firstCapturedAtMillis = firstCapturedAtMillis,
    lastUpdatedAtMillis = lastUpdatedAtMillis,
    updateCount = updateCount,
    isInSystemNotificationBar = isInSystemNotificationBar,
)
