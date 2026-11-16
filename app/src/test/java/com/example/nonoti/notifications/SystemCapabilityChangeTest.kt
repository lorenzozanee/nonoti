package com.example.nonoti.notifications

import android.app.AlarmManager
import android.app.NotificationManager
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

    @Test
    fun zenRuleStatusChangeFailsOpenOnlyWhenOwnRuleBecomesInactive() {
        assertTrue(
            ZenRuleStatusChange.shouldFailOpen(
                isOwnRule = true,
                status = 5,
                ownZenActive = false,
            ),
        )
        assertTrue(
            ZenRuleStatusChange.shouldFailOpen(
                isOwnRule = true,
                status = NotificationManager.AUTOMATIC_RULE_STATUS_DISABLED,
                ownZenActive = false,
            ),
        )
        assertTrue(
            ZenRuleStatusChange.shouldFailOpen(
                isOwnRule = true,
                status = NotificationManager.AUTOMATIC_RULE_STATUS_REMOVED,
                ownZenActive = false,
            ),
        )
    }

    @Test
    fun zenRuleStatusChangeIsIgnoredWhileOwnRuleRemainsActiveOrForOtherRules() {
        assertFalse(
            ZenRuleStatusChange.shouldFailOpen(
                isOwnRule = true,
                status = 5,
                ownZenActive = true,
            ),
        )
        assertFalse(
            ZenRuleStatusChange.shouldFailOpen(
                isOwnRule = false,
                status = 5,
                ownZenActive = false,
            ),
        )
        assertFalse(
            ZenRuleStatusChange.shouldFailOpen(
                isOwnRule = true,
                status = NotificationManager.AUTOMATIC_RULE_STATUS_ENABLED,
                ownZenActive = false,
            ),
        )
    }
}
