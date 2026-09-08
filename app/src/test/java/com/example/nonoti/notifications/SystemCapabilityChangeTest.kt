package com.example.nonoti.notifications

import android.app.AlarmManager
import com.example.nonoti.focus.FocusReadiness
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCapabilityChangeTest {
    @Test
    fun exactAlarmRevocationRequiresFailOpen() {
        val readiness = FocusReadiness.allReady().copy(exactAlarms = false)

        assertTrue(
            SystemCapabilityChange.shouldFailOpen(
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                readiness,
            ),
        )
    }

    @Test
    fun exactAlarmGrantDoesNotFailOpen() {
        assertFalse(
            SystemCapabilityChange.shouldFailOpen(
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                FocusReadiness.allReady(),
            ),
        )
    }

    @Test
    fun interruptionFilterChangeFailsOpenWhenOwnZenIsNoLongerActive() {
        assertTrue(InterruptionFilterChange.shouldFailOpen("session", ownZenActive = false))
    }

    @Test
    fun interruptionFilterChangeIsIgnoredOutsideFocusOrWhileOwnZenRemainsActive() {
        assertFalse(InterruptionFilterChange.shouldFailOpen(null, ownZenActive = false))
        assertFalse(InterruptionFilterChange.shouldFailOpen("session", ownZenActive = true))
    }
}
