package com.example.nonoti

import android.app.NotificationManager
import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nonoti.notifications.AndroidFocusPlatform
import com.example.nonoti.notifications.CompatibilityStore
import com.example.nonoti.notifications.CompatibilityTestController
import com.example.nonoti.notifications.FocusRuntime
import com.example.nonoti.notifications.FocusStartResult
import com.example.nonoti.notifications.NonotiPlatform
import com.example.nonoti.notifications.NonotiNotificationListenerService
import com.example.nonoti.focus.FocusSession
import com.example.nonoti.focus.FocusState
import com.example.nonoti.data.FocusSessionEntity
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityFlowInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as NonotiApplication
    private val externalPublisher = ComponentName(
        "com.example.nonoti.test",
        "com.example.nonoti.ExternalNotificationPublisherReceiver",
    )
    private val externalIdSeed = System.nanoTime().hashCode()

    @Before
    fun setUp() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
        CompatibilityStore(context).clear()
        FocusRuntime.current()?.let { com.example.nonoti.notifications.NonotiPlatform.failOpen(context, it.id) }
        AndroidFocusPlatform(context).disableZen()
        FocusRuntime.stop()
        runBlocking {
            application.database.dao().focusBlocks().forEach { block ->
                application.database.dao().deleteFocusBlock(block.startMinute, block.endMinute)
            }
            application.database.dao().clearFocusSession()
            application.database.dao().clearNotifications()
        }
        NotificationListenerService.requestRebind(
            ComponentName(context, NonotiNotificationListenerService::class.java),
        )
    }

    @After
    fun tearDown() {
        context.sendBroadcast(
            Intent(ExternalNotificationPublisherReceiver.ACTION_CANCEL_ALL).setComponent(externalPublisher),
        )
        AndroidFocusPlatform(context).disableZen()
        FocusRuntime.stop()
        runBlocking { application.database.dao().clearFocusSession() }
    }

    @Test
    fun summaryRetryUpdatesOneNotificationWithoutRepeatingAlert() {
        val platform = AndroidFocusPlatform(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val now = Instant.now()
        val session = FocusSession("summary-retry", now.minusSeconds(600), now)

        platform.sendSummary(session, 2)
        eventually(5_000) { manager.activeNotifications.any { it.id == session.id.hashCode() } }
        platform.sendSummary(session, 3)

        eventually(5_000) {
            val summaries = manager.activeNotifications.filter { it.notification.channelId == "focus-summary" }
            summaries.size == 1 && summaries.single().notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)?.toString() ==
                context.resources.getQuantityString(R.plurals.summary_text, 3, 3)
        }
        val summary = manager.activeNotifications.single { it.id == session.id.hashCode() }.notification
        assertTrue(summary.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test
    fun controlledNotificationIsCapturedAndReleasedWithOneSummary() {
        val platform = AndroidFocusPlatform(context)
        eventually(10_000) { platform.readiness().notificationAccess }
        val readiness = platform.readiness().copy(compatibility = true)
        assertTrue("Missing requirements: ${readiness.missingRequirements}", readiness.isReady)

        assertTrue(
            "Compatibility start failed; readiness=${platform.readiness().copy(compatibility = true).missingRequirements}, zen=${platform.isZenActive()}",
            CompatibilityTestController.start(context),
        )
        eventually(5_000) { FocusRuntime.current() != null && platform.isZenActive() }
        eventually(45_000) { CompatibilityStore(context).isTestCompleted() && FocusRuntime.current() == null }

        val captured = runBlocking { application.boxRepository.notifications.first() }
        assertEquals(listOf("nonoti compatibility test"), captured.map { it.title })
        assertFalse(platform.isZenActive())
        val summaries = context.getSystemService(NotificationManager::class.java).activeNotifications
            .count {
                it.notification.channelId == "focus-summary" &&
                    it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Focus finished"
            }
        assertEquals(1, summaries)
    }

    @Test
    fun clockChangeReleasesManualSessionWithoutLeavingZenOrSessionState() {
        val platform = AndroidFocusPlatform(context)
        eventually(10_000) { platform.readiness().notificationAccess }
        CompatibilityStore(context).apply {
            beginTest()
            markTestCompleted()
            markPassed()
        }
        val now = Instant.now()
        val session = FocusSession("manual-clock-change", now, now.plusSeconds(600))

        assertEquals(FocusStartResult.Started, NonotiPlatform.start(context, session, platform.readiness()))
        eventually(5_000) { FocusRuntime.current()?.id == session.id && platform.isZenActive() }

        NonotiPlatform.handleClockChange(context)

        eventually(15_000) { FocusRuntime.current() == null && !platform.isZenActive() }
        assertNull(runBlocking { application.database.dao().focusSession() })
    }

    @Test
    fun restoreUserClosedZenIsNotReenabledDuringRestore() {
        val platform = AndroidFocusPlatform(context)
        eventually(10_000) { platform.readiness().notificationAccess }
        CompatibilityStore(context).apply {
            beginTest()
            markTestCompleted()
            markPassed()
        }
        val now = System.currentTimeMillis()
        runBlocking {
            application.database.dao().upsertFocusSession(
                FocusSessionEntity(
                    id = "restore-user-disabled-zen",
                    startMillis = now - 60_000,
                    endMillis = now + 600_000,
                    state = FocusState.Focusing,
                ),
            )
        }
        FocusRuntime.stop()
        assertFalse(platform.isZenActive())

        NonotiPlatform.restore(context)

        val restored = runBlocking { application.database.dao().focusSession() }
        assertEquals(FocusState.Unsupported, restored?.state)
        assertFalse(platform.isZenActive())
        assertTrue(FocusRuntime.current()?.isUnsupported == true)
    }

    @Test
    fun restoreWithoutPersistedSessionDisablesStaleOwnZenRule() {
        val platform = AndroidFocusPlatform(context)
        startReadyFocus("stale-zen")
        runBlocking { application.database.dao().clearFocusSession() }
        FocusRuntime.stop()
        assertTrue(platform.isZenActive())

        NonotiPlatform.restore(context)

        eventually(5_000) { !platform.isZenActive() }
        assertNull(runBlocking { application.database.dao().focusSession() })
    }

    @Test
    fun releaseTimeoutClosesZenButPersistsUnsupportedSession() {
        val platform = AndroidFocusPlatform(context)
        eventually(10_000) { platform.readiness().notificationAccess }
        CompatibilityStore(context).apply {
            beginTest()
            markTestCompleted()
            markPassed()
        }
        val now = Instant.now()
        val session = FocusSession("release-timeout", now, now.plusSeconds(600))
        assertEquals(FocusStartResult.Started, NonotiPlatform.start(context, session, platform.readiness()))
        FocusRuntime.markSnoozed(session.id, "never-restored")

        NonotiPlatform.finish(context, session.id, releaseTimedOut = true)

        val persisted = runBlocking { application.database.dao().focusSession() }
        assertEquals(FocusState.Unsupported, persisted?.state)
        assertFalse(platform.isZenActive())
        assertTrue(FocusRuntime.current()?.isUnsupported == true)
    }

    @Test
    fun releasingRestoreWithoutListenerFailsOpenInsteadOfAssumingRestored() {
        val platform = AndroidFocusPlatform(context)
        val session = startReadyFocus("releasing-without-listener")
        runBlocking {
            val persisted = requireNotNull(application.database.dao().focusSession())
            application.database.dao().upsertFocusSession(persisted.copy(state = FocusState.Releasing))
        }
        FocusRuntime.stop()
        FocusRuntime.setListenerConnected(false)

        try {
            NonotiPlatform.restore(context)

            eventually(7_000) {
                val persisted = runBlocking { application.database.dao().focusSession() }
                persisted?.id == session.id && persisted.state == FocusState.Unsupported && !platform.isZenActive()
            }
        } finally {
            FocusRuntime.setListenerConnected(true)
        }
    }

    @Test
    fun activeFocusExtensionReschedulesWithoutReenablingZen() {
        val platform = AndroidFocusPlatform(context)
        eventually(10_000) { platform.readiness().notificationAccess }
        CompatibilityStore(context).apply {
            beginTest()
            markTestCompleted()
            markPassed()
        }
        val now = Instant.now()
        val session = FocusSession("extend-existing", now, now.plusSeconds(600))
        assertEquals(FocusStartResult.Started, NonotiPlatform.start(context, session, platform.readiness()))
        val extendedEnd = now.plusSeconds(1_200)

        assertTrue(NonotiPlatform.extendTo(context, extendedEnd))

        val persisted = runBlocking { application.database.dao().focusSession() }
        assertEquals(extendedEnd.toEpochMilli(), persisted?.endMillis)
        assertTrue(platform.isZenActive())
    }

    @Test
    fun externalPackageNotificationIsCapturedUpdatedAndCancelled() {
        val session = startReadyFocus("external-notification", durationSeconds = 150)

        publishExternal(id = 501, title = "First")
        eventually(10_000) {
            capturedFromExternal().singleOrNull()?.title == "First"
        }

        publishExternal(id = 501, title = "Updated")
        eventually(70_000) {
            capturedFromExternal().singleOrNull()?.let { notification ->
                notification.title == "Updated" && notification.updateCount == 2
            } == true
        }

        context.sendBroadcast(
            Intent(ExternalNotificationPublisherReceiver.ACTION_CANCEL)
                .setComponent(externalPublisher)
                .putExtra(ExternalNotificationPublisherReceiver.EXTRA_ID, externalId(501)),
        )
        eventually(70_000) {
            capturedFromExternal().singleOrNull()?.isInSystemNotificationBar == false
        }
        assertEquals(session.id, capturedFromExternal().single().sessionId)
    }

    @Test
    fun externalRealtimeAndOngoingNotificationsAreExcluded() {
        startReadyFocus("external-exclusions")

        publishExternal(id = 510, title = "Call", category = Notification.CATEGORY_CALL)
        publishExternal(id = 511, title = "Alarm", category = Notification.CATEGORY_ALARM)
        publishExternal(id = 512, title = "Media", category = Notification.CATEGORY_TRANSPORT)
        publishExternal(id = 513, title = "Navigation", category = Notification.CATEGORY_NAVIGATION)
        publishExternal(id = 514, title = "Ongoing", ongoing = true)

        SystemClock.sleep(2_000)
        val captured = capturedFromExternal()
        assertTrue(
            "Unexpected captured notifications: ${captured.map { listOf(it.title, it.groupKey, it.isGroupSummary, it.channelId) }}",
            captured.isEmpty(),
        )
    }

    @Test
    fun externalGroupChildReplacesPreviouslyCapturedSummary() {
        startReadyFocus("external-group")

        publishExternal(id = 520, title = "Summary", group = "messages", groupSummary = true)
        eventually(10_000) {
            capturedFromExternal().singleOrNull()?.isGroupSummary == true
        }

        publishExternal(id = 521, title = "Child", group = "messages")
        eventuallyWithState(70_000, state = {
            capturedFromExternal().map {
                listOf(it.title, it.groupKey, it.isGroupSummary, it.updateCount)
            }.toString()
        }) {
            capturedFromExternal().singleOrNull()?.let { notification ->
                notification.title == "Child" && !notification.isGroupSummary
            } == true
        }
    }

    private fun startReadyFocus(id: String, durationSeconds: Long = 600): FocusSession {
        val platform = AndroidFocusPlatform(context)
        eventually(10_000) { platform.readiness().notificationAccess }
        CompatibilityStore(context).apply {
            beginTest()
            markTestCompleted()
            markPassed()
        }
        val now = Instant.now()
        val session = FocusSession(id, now, now.plusSeconds(durationSeconds))
        assertEquals(FocusStartResult.Started, NonotiPlatform.start(context, session, platform.readiness()))
        eventually(5_000) { FocusRuntime.current()?.id == id && platform.isZenActive() }
        return session
    }

    private fun publishExternal(
        id: Int,
        title: String,
        category: String? = null,
        ongoing: Boolean = false,
        group: String? = null,
        groupSummary: Boolean = false,
    ) {
        context.sendBroadcast(
            Intent(ExternalNotificationPublisherReceiver.ACTION_POST)
                .setComponent(externalPublisher)
                .putExtra(ExternalNotificationPublisherReceiver.EXTRA_ID, externalId(id))
                .putExtra(ExternalNotificationPublisherReceiver.EXTRA_TITLE, title)
                .putExtra(ExternalNotificationPublisherReceiver.EXTRA_TEXT, "External body")
                .putExtra(ExternalNotificationPublisherReceiver.EXTRA_CATEGORY, category)
                .putExtra(ExternalNotificationPublisherReceiver.EXTRA_ONGOING, ongoing)
                .putExtra(ExternalNotificationPublisherReceiver.EXTRA_GROUP, group)
                .putExtra(ExternalNotificationPublisherReceiver.EXTRA_GROUP_SUMMARY, groupSummary),
        )
    }

    private fun capturedFromExternal() = runBlocking {
        application.boxRepository.notifications.first().filter { it.packageName == "com.example.nonoti.test" }
    }

    private fun externalId(logicalId: Int): Int = externalIdSeed xor logicalId

    private fun eventually(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline && !condition()) {
            SystemClock.sleep(250)
        }
        assertTrue("Condition was not met within ${timeoutMillis}ms", condition())
    }

    private fun eventuallyWithState(
        timeoutMillis: Long,
        state: () -> String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline && !condition()) {
            SystemClock.sleep(250)
        }
        assertTrue("Condition was not met within ${timeoutMillis}ms; state=${state()}", condition())
    }
}
