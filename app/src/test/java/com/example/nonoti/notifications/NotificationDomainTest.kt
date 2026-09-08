package com.example.nonoti.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDomainTest {
    @Test
    fun normalNotificationIsEligibleForCapture() {
        val policy = NotificationEligibilityPolicy(NotificationPolicy(ownPackageName = "com.example.nonoti"))

        val decision = policy.evaluate(notification(key = "key-1"))

        assertTrue(decision.shouldCapture)
        assertNull(decision.exclusionReason)
    }

    @Test
    fun ownAndAlwaysAllowedNotificationsAreExcluded() {
        val policy =
            NotificationEligibilityPolicy(
                NotificationPolicy(
                    ownPackageName = "com.example.nonoti",
                    alwaysAllowedPackages = setOf("com.example.allowed"),
                ),
            )

        val ownDecision = policy.evaluate(notification(key = "own", packageName = "com.example.nonoti"))
        val allowedDecision = policy.evaluate(notification(key = "allowed", packageName = "com.example.allowed"))

        assertEquals(NotificationExclusionReason.OwnNotification, ownDecision.exclusionReason)
        assertEquals(NotificationExclusionReason.AlwaysAllowedApp, allowedDecision.exclusionReason)
        assertFalse(ownDecision.shouldCapture)
        assertFalse(allowedDecision.shouldCapture)
    }

    @Test
    fun unsafeNotificationKindsAndWorkProfileAreExcluded() {
        val policy = NotificationEligibilityPolicy(NotificationPolicy(ownPackageName = "com.example.nonoti"))

        assertExcluded(policy, notification(key = "call", kind = NotificationKind.Call), NotificationExclusionReason.RealTimeStatus)
        assertExcluded(policy, notification(key = "alarm", kind = NotificationKind.Alarm), NotificationExclusionReason.Alarm)
        assertExcluded(policy, notification(key = "emergency", kind = NotificationKind.SystemEmergency), NotificationExclusionReason.SystemEmergency)
        assertExcluded(policy, notification(key = "ongoing", isOngoing = true), NotificationExclusionReason.Ongoing)
        assertExcluded(policy, notification(key = "work", isWorkProfile = true), NotificationExclusionReason.WorkProfile)
        assertExcluded(policy, notification(key = "no-snooze", canSnooze = false), NotificationExclusionReason.CannotSnooze)
    }

    @Test
    fun sameKeyWithinSessionUpdatesSnapshotWithoutIncreasingDistinctCount() {
        val store = InMemoryNotificationBoxStore()

        store.upsert(notification(key = "key-1", title = "before", capturedAtMillis = 10L))
        store.upsert(notification(key = "key-1", title = "after", capturedAtMillis = 20L, updatedAtMillis = 30L))

        val saved = store.notifications(sessionId = "session-1")
        assertEquals(1, store.distinctCount(sessionId = "session-1"))
        assertEquals("after", saved.single().title)
        assertEquals(10L, saved.single().firstCapturedAtMillis)
        assertEquals(30L, saved.single().lastUpdatedAtMillis)
        assertEquals(2, saved.single().updateCount)
    }

    @Test
    fun differentKeysAndSessionsRemainDistinct() {
        val store = InMemoryNotificationBoxStore()

        store.upsert(notification(key = "key-1"))
        store.upsert(notification(key = "key-2"))
        store.upsert(notification(key = "key-1", sessionId = "session-2"))

        assertEquals(2, store.distinctCount(sessionId = "session-1"))
        assertEquals(1, store.distinctCount(sessionId = "session-2"))
    }

    @Test
    fun groupSummaryIsNotStoredWhenAChildNotificationExists() {
        val store = InMemoryNotificationBoxStore()

        store.upsert(notification(key = "child", groupKey = "group-1"))
        val result =
            store.upsert(
                notification(
                    key = "summary",
                    groupKey = "group-1",
                    isGroupSummary = true,
                ),
            )

        assertEquals(NotificationUpsertAction.IgnoredGroupSummary, result.action)
        assertEquals(listOf("child"), store.notifications("session-1").map { it.notificationKey })
        assertEquals(1, store.distinctCount("session-1"))
    }

    @Test
    fun summaryIsStoredAndMarkedWhenNoChildIsAvailable() {
        val store = InMemoryNotificationBoxStore()

        store.upsert(
            notification(
                key = "summary",
                groupKey = "group-1",
                isGroupSummary = true,
            ),
        )

        val saved = store.notifications("session-1").single()
        assertTrue(saved.isGroupSummary)
        assertEquals(1, store.distinctCount("session-1"))
    }

    @Test
    fun childNotificationReplacesPreviouslyStoredSummaryWithoutDoubleCounting() {
        val store = InMemoryNotificationBoxStore()

        store.upsert(
            notification(
                key = "summary",
                groupKey = "group-1",
                isGroupSummary = true,
            ),
        )
        store.upsert(notification(key = "child", groupKey = "group-1"))

        val saved = store.notifications("session-1")
        assertEquals(listOf("child"), saved.map { it.notificationKey })
        assertEquals(1, store.distinctCount("session-1"))
    }

    @Test
    fun cancellationKeepsSnapshotButMarksItAbsentFromSystemTray() {
        val store = InMemoryNotificationBoxStore()
        store.upsert(notification(key = "key-1"))

        assertTrue(store.markCancelled(sessionId = "session-1", notificationKey = "key-1"))
        assertFalse(store.notifications("session-1").single().isInSystemNotificationBar)
        assertEquals(1, store.distinctCount("session-1"))
        assertFalse(store.markCancelled(sessionId = "session-1", notificationKey = "missing"))
    }

    private fun assertExcluded(
        policy: NotificationEligibilityPolicy,
        notification: NotificationSnapshot,
        reason: NotificationExclusionReason,
    ) {
        val decision = policy.evaluate(notification)

        assertFalse(decision.shouldCapture)
        assertEquals(reason, decision.exclusionReason)
    }

    private fun notification(
        key: String,
        sessionId: String = "session-1",
        packageName: String = "com.example.mail",
        title: String? = "Title",
        capturedAtMillis: Long = 1L,
        updatedAtMillis: Long = capturedAtMillis,
        kind: NotificationKind = NotificationKind.Normal,
        groupKey: String? = null,
        isGroupSummary: Boolean = false,
        isWorkProfile: Boolean = false,
        isOngoing: Boolean = false,
        canSnooze: Boolean = true,
    ) =
        NotificationSnapshot(
            sessionId = sessionId,
            notificationKey = key,
            packageName = packageName,
            title = title,
            text = "Body",
            capturedAtMillis = capturedAtMillis,
            updatedAtMillis = updatedAtMillis,
            kind = kind,
            groupKey = groupKey,
            isGroupSummary = isGroupSummary,
            isWorkProfile = isWorkProfile,
            isOngoing = isOngoing,
            canSnooze = canSnooze,
        )
}
