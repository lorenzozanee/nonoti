package com.example.nonoti.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCaptureOrderTest {
    @Test
    fun snapshotIsPersistedBeforeTheSystemNotificationIsSnoozed() {
        val events = mutableListOf<String>()

        val result = NotificationCaptureOrder.run(
            persist = { events += "persist"; true },
            snooze = { events += "snooze"; true },
        )

        assertEquals(NotificationCaptureResult.Captured, result)
        assertEquals(listOf("persist", "snooze"), events)
    }

    @Test
    fun persistenceFailureLeavesTheSourceNotificationUnsnoozed() {
        val events = mutableListOf<String>()

        val result = NotificationCaptureOrder.run(
            persist = { events += "persist"; false },
            snooze = { events += "snooze"; true },
        )

        assertEquals(NotificationCaptureResult.PersistenceFailed, result)
        assertEquals(listOf("persist"), events)
    }
}
