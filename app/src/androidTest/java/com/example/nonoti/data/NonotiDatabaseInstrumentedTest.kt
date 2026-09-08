package com.example.nonoti.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nonoti.notifications.InMemoryNotificationBoxStore
import com.example.nonoti.notifications.NotificationKind
import com.example.nonoti.notifications.NotificationSnapshot
import com.example.nonoti.focus.FocusState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NonotiDatabaseInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "nonoti-instrumented-test.db"
    private lateinit var database: NonotiDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun focusBlocksPersistSortedAndDisabledBlocksStayHidden() = runBlocking {
        database.dao().upsertFocusBlock(FocusBlockEntity(14 * 60, 17 * 60))
        database.dao().upsertFocusBlock(FocusBlockEntity(9 * 60, 11 * 60 + 30))
        database.dao().upsertFocusBlock(FocusBlockEntity(7 * 60, 8 * 60, enabled = false))

        database.close()
        database = openDatabase()

        assertEquals(
            listOf(9 * 60 to 11 * 60 + 30, 14 * 60 to 17 * 60),
            database.dao().focusBlocks().map { it.startMinute to it.endMinute },
        )
    }

    @Test
    fun notificationUpdateRetainsFirstCaptureAndPersistsCancellation() = runBlocking {
        val store = InMemoryNotificationBoxStore()
        val first = snapshot(title = "First", capturedAt = 1_000, updatedAt = 1_000)
        val update = snapshot(title = "Updated", capturedAt = 2_000, updatedAt = 2_000)
        database.dao().upsertNotification(store.upsert(first).notification!!.toEntity())
        database.dao().upsertNotification(store.upsert(update).notification!!.toEntity())

        database.dao().markNotificationCancelled("session", "key")
        val saved = BoxRepository(database.dao()).notifications.first().single()

        assertEquals("Updated", saved.title)
        assertEquals(1_000, saved.firstCapturedAtMillis)
        assertEquals(2_000, saved.lastUpdatedAtMillis)
        assertEquals(2, saved.updateCount)
        assertFalse(saved.isInSystemNotificationBar)
    }

    @Test
    fun notificationUpdateAfterProcessRestartRetainsFirstCaptureAndUpdateCount() = runBlocking {
        val firstProcess = InMemoryNotificationBoxStore()
        val secondProcess = InMemoryNotificationBoxStore()
        val first = snapshot(title = "First", capturedAt = 1_000, updatedAt = 1_000)
        val update = snapshot(title = "Updated", capturedAt = 2_000, updatedAt = 2_000)

        database.dao().upsertNotificationBlocking(firstProcess.upsert(first).notification!!.toEntity())
        database.dao().upsertNotificationBlocking(secondProcess.upsert(update).notification!!.toEntity())

        val saved = BoxRepository(database.dao()).notifications.first().single()
        assertEquals("Updated", saved.title)
        assertEquals(1_000, saved.firstCapturedAtMillis)
        assertEquals(2_000, saved.lastUpdatedAtMillis)
        assertEquals(2, saved.updateCount)
    }

    @Test
    fun retentionAndClearDeleteOnlyExpectedNotifications() = runBlocking {
        database.dao().upsertNotification(store(snapshot("Old", 1_000, 1_000)))
        database.dao().upsertNotification(store(snapshot("New", 3_000, 3_000, key = "new")))

        database.dao().deleteNotificationsOlderThan(2_000, activeSessionId = null)
        assertEquals(listOf("New"), BoxRepository(database.dao()).notifications.first().map { it.title })

        database.dao().clearNotifications()
        assertTrue(BoxRepository(database.dao()).notifications.first().isEmpty())
    }

    @Test
    fun groupSummaryIsRemovedWhenChildArrives() = runBlocking {
        database.dao().upsertNotification(
            store(snapshot("Summary", 1_000, 1_000, key = "summary").copy(groupKey = "mail", isGroupSummary = true)),
        )

        database.dao().deleteGroupSummaries("session", "com.example.source", "mail")
        database.dao().upsertNotification(
            store(snapshot("Child", 2_000, 2_000, key = "child").copy(groupKey = "mail")),
        )

        assertEquals(listOf("Child"), BoxRepository(database.dao()).notifications.first().map { it.title })
    }

    @Test
    fun retentionNeverDeletesTheActiveSession() = runBlocking {
        database.dao().upsertNotification(store(snapshot("Active", 1_000, 1_000)))
        database.dao().upsertNotification(
            store(snapshot("Expired", 1_000, 1_000, key = "expired").copy(sessionId = "old-session")),
        )

        BoxRepository(database.dao()).deleteOlderThan(2_000, activeSessionId = "session")

        assertEquals(listOf("Active"), BoxRepository(database.dao()).notifications.first().map { it.title })
    }

    @Test
    fun focusSessionStateSurvivesDatabaseReopen() = runBlocking {
        database.dao().upsertFocusSession(
            FocusSessionEntity(
                id = "session",
                startMillis = 1_000,
                endMillis = 2_000,
                state = FocusState.Releasing,
            ),
        )

        database.close()
        database = openDatabase()

        assertEquals(FocusState.Releasing, database.dao().focusSession()?.state)
        assertEquals("session", database.dao().focusSession()?.id)
    }

    private fun openDatabase(): NonotiDatabase =
        Room.databaseBuilder(context, NonotiDatabase::class.java, databaseName).build()

    private fun store(snapshot: NotificationSnapshot) =
        InMemoryNotificationBoxStore().upsert(snapshot).notification!!.toEntity()

    private fun snapshot(
        title: String,
        capturedAt: Long,
        updatedAt: Long,
        key: String = "key",
    ) = NotificationSnapshot(
        sessionId = "session",
        notificationKey = key,
        packageName = "com.example.source",
        capturedAtMillis = capturedAt,
        updatedAtMillis = updatedAt,
        title = title,
        text = "Body",
        kind = NotificationKind.Normal,
        canSnooze = true,
    )
}
