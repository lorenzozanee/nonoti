package com.example.nonoti.data

import com.example.nonoti.focus.DailyFocusBlock
import com.example.nonoti.focus.FocusBlockError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusBlockRepositoryTest {
    @Test
    fun savesValidBlockAndExposesItAsDomainModel() = runTest {
        val dao = FakeNonotiDao()
        val repository = FocusBlockRepository(dao)

        assertEquals(null, repository.save(DailyFocusBlock(540, 600)))
        assertEquals(listOf(DailyFocusBlock(540, 600)), repository.blocks.first())
    }

    @Test
    fun rejectsBlockThatTouchesExistingBlockWithoutWriting() = runTest {
        val dao = FakeNonotiDao().apply { blocks.value = listOf(FocusBlockEntity(600, 660)) }
        val repository = FocusBlockRepository(dao)

        assertEquals(FocusBlockError.TouchesExisting, repository.save(DailyFocusBlock(540, 600)))
        assertTrue(dao.writes.isEmpty())
    }

    @Test
    fun deletesOnlyRequestedBlock() = runTest {
        val dao = FakeNonotiDao()
        val repository = FocusBlockRepository(dao)
        repository.save(DailyFocusBlock(540, 600))

        repository.delete(DailyFocusBlock(540, 600))

        assertTrue(dao.blocks.value.isEmpty())
        assertEquals(listOf(540 to 600), dao.deletes)
    }

    private class FakeNonotiDao : NonotiDao {
        val blocks = MutableStateFlow<List<FocusBlockEntity>>(emptyList())
        val writes = mutableListOf<FocusBlockEntity>()
        val deletes = mutableListOf<Pair<Int, Int>>()

        override fun observeFocusBlocks() = blocks
        override suspend fun focusBlocks() = blocks.value
        override fun focusBlocksBlocking() = blocks.value
        override suspend fun upsertFocusBlock(block: FocusBlockEntity) {
            writes += block
            blocks.value = blocks.value.filterNot { it.startMinute == block.startMinute && it.endMinute == block.endMinute } + block
        }
        override suspend fun deleteFocusBlock(startMinute: Int, endMinute: Int) {
            deletes += startMinute to endMinute
            blocks.value = blocks.value.filterNot { it.startMinute == startMinute && it.endMinute == endMinute }
        }
        override fun observeNotifications(sessionId: String) = MutableStateFlow(emptyList<CapturedNotificationEntity>())
        override fun observeAllNotifications() = MutableStateFlow(emptyList<CapturedNotificationEntity>())
        override suspend fun upsertNotification(notification: CapturedNotificationEntity) = Unit
        override fun insertNotificationBlocking(notification: CapturedNotificationEntity) = Unit
        override fun notificationBlocking(sessionId: String, notificationKey: String): CapturedNotificationEntity? = null
        override fun upsertNotificationBlocking(notification: CapturedNotificationEntity) = Unit
        override suspend fun markNotificationCancelled(sessionId: String, notificationKey: String) = Unit
        override fun markNotificationCancelledBlocking(sessionId: String, notificationKey: String) = Unit
        override fun deleteGroupSummaries(sessionId: String, packageName: String, groupKey: String) = Unit
        override suspend fun clearNotifications() = Unit
        override fun notificationCountBlocking(sessionId: String) = 0
        override suspend fun deleteNotificationsOlderThan(cutoffMillis: Long, activeSessionId: String?) = Unit
        override suspend fun upsertFocusSession(session: FocusSessionEntity) = Unit
        override suspend fun focusSession(): FocusSessionEntity? = null
        override suspend fun clearFocusSession() = Unit
    }
}
