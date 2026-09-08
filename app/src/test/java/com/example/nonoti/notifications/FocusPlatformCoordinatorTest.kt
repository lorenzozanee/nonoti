package com.example.nonoti.notifications

import com.example.nonoti.focus.FocusReadiness
import com.example.nonoti.focus.FocusSession
import com.example.nonoti.focus.FocusState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPlatformCoordinatorTest {
    private val now = Instant.parse("2026-09-07T10:00:00Z")
    private val session = FocusSession("session-1", now, now.plusSeconds(3600))

    @Test
    fun startIsRejectedWhenReadinessIsIncomplete() {
        val port = RecordingPlatformPort()
        val result = FocusPlatformCoordinator(port).start(session, FocusReadiness.allReady().copy(postNotifications = false))

        assertEquals(FocusStartResult.Rejected, result)
        assertEquals(emptyList<String>(), port.events)
    }

    @Test
    fun startEnablesZenBeforeSchedulingEnd() {
        val port = RecordingPlatformPort()
        val result = FocusPlatformCoordinator(port).start(session, FocusReadiness.allReady())

        assertEquals(FocusStartResult.Started, result)
        assertEquals(listOf("zen:on", "alarm:session-1"), port.events)
    }

    @Test
    fun finishReleasesZenAndSendsSummaryOnlyOnce() {
        val port = RecordingPlatformPort()
        val coordinator = FocusPlatformCoordinator(port)
        coordinator.start(session, FocusReadiness.allReady())

        assertEquals(FocusState.Idle, coordinator.finish(session.id, distinctCount = 4))
        assertEquals(FocusState.Idle, coordinator.finish(session.id, distinctCount = 4))
        assertEquals(listOf("zen:on", "alarm:session-1", "zen:off", "summary:session-1:4"), port.events)
    }

    @Test
    fun zenFailureLeavesSessionUnsupportedAndDoesNotScheduleAlarm() {
        val port = RecordingPlatformPort(zenCanEnable = false)
        val coordinator = FocusPlatformCoordinator(port)

        assertEquals(FocusStartResult.Unsupported, coordinator.start(session, FocusReadiness.allReady()))
        assertEquals(listOf("zen:failed"), port.events)
        assertFalse(coordinator.isActive(session.id))
    }

    @Test
    fun alarmFailureRollsBackZenAndLeavesSessionUnsupported() {
        val port = RecordingPlatformPort(alarmCanSchedule = false)

        val result = FocusPlatformCoordinator(port).start(session, FocusReadiness.allReady())

        assertEquals(FocusStartResult.Unsupported, result)
        assertEquals(listOf("zen:on", "alarm:failed", "zen:off"), port.events)
    }

    @Test
    fun finishRemainsUnsupportedWhenZenCannotBeDisabled() {
        val port = RecordingPlatformPort(zenCanDisable = false)
        val coordinator = FocusPlatformCoordinator(port)
        coordinator.start(session, FocusReadiness.allReady())

        assertEquals(FocusState.Unsupported, coordinator.finish(session.id, distinctCount = 4))
        assertEquals(listOf("zen:on", "alarm:session-1", "zen:off-failed"), port.events)
        assertFalse(coordinator.isActive(session.id))
    }

    private class RecordingPlatformPort(
        private val zenCanEnable: Boolean = true,
        private val zenCanDisable: Boolean = true,
        private val alarmCanSchedule: Boolean = true,
    ) : FocusPlatformPort {
        val events = mutableListOf<String>()

        override fun enableZen(): Boolean {
            events += if (zenCanEnable) "zen:on" else "zen:failed"
            return zenCanEnable
        }

        override fun disableZen(): Boolean {
            events += if (zenCanDisable) "zen:off" else "zen:off-failed"
            return zenCanDisable
        }

        override fun scheduleEnd(session: FocusSession): Boolean {
            events += if (alarmCanSchedule) "alarm:${session.id}" else "alarm:failed"
            return alarmCanSchedule
        }

        override fun sendSummary(session: FocusSession, distinctCount: Int) {
            events += "summary:${session.id}:$distinctCount"
        }
    }
}
