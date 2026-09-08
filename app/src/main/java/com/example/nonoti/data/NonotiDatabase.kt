package com.example.nonoti.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.example.nonoti.focus.FocusState

@Entity(tableName = "focus_blocks", primaryKeys = ["startMinute", "endMinute"])
data class FocusBlockEntity(
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
)

@Entity(tableName = "captured_notifications", primaryKeys = ["sessionId", "notificationKey"])
data class CapturedNotificationEntity(
    val sessionId: String,
    val notificationKey: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val subText: String?,
    val channelId: String?,
    val conversationId: String?,
    val groupKey: String?,
    val isGroupSummary: Boolean,
    val firstCapturedAtMillis: Long,
    val lastUpdatedAtMillis: Long,
    val updateCount: Int,
    val isInSystemNotificationBar: Boolean,
)

@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val state: FocusState,
    val failureReason: String? = null,
    val summarySent: Boolean = false,
    @androidx.room.PrimaryKey val singletonId: Int = 1,
)

@Dao
interface NonotiDao {
    @Query("SELECT * FROM focus_blocks WHERE enabled = 1 ORDER BY startMinute")
    fun observeFocusBlocks(): Flow<List<FocusBlockEntity>>

    @Query("SELECT * FROM focus_blocks WHERE enabled = 1 ORDER BY startMinute")
    suspend fun focusBlocks(): List<FocusBlockEntity>

    @Query("SELECT * FROM focus_blocks WHERE enabled = 1 ORDER BY startMinute")
    fun focusBlocksBlocking(): List<FocusBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFocusBlock(block: FocusBlockEntity)

    @Query("DELETE FROM focus_blocks WHERE startMinute = :startMinute AND endMinute = :endMinute")
    suspend fun deleteFocusBlock(startMinute: Int, endMinute: Int)

    @Query("SELECT * FROM captured_notifications WHERE sessionId = :sessionId ORDER BY firstCapturedAtMillis DESC")
    fun observeNotifications(sessionId: String): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT * FROM captured_notifications ORDER BY firstCapturedAtMillis DESC")
    fun observeAllNotifications(): Flow<List<CapturedNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(notification: CapturedNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNotificationBlocking(notification: CapturedNotificationEntity)

    @Query("SELECT * FROM captured_notifications WHERE sessionId = :sessionId AND notificationKey = :notificationKey LIMIT 1")
    fun notificationBlocking(sessionId: String, notificationKey: String): CapturedNotificationEntity?

    @Transaction
    fun upsertNotificationBlocking(notification: CapturedNotificationEntity) {
        val previous = notificationBlocking(notification.sessionId, notification.notificationKey)
        val merged = if (previous == null) {
            notification.copy(updateCount = 1)
        } else {
            notification.copy(
                firstCapturedAtMillis = minOf(previous.firstCapturedAtMillis, notification.firstCapturedAtMillis),
                lastUpdatedAtMillis = maxOf(previous.lastUpdatedAtMillis, notification.lastUpdatedAtMillis),
                updateCount = previous.updateCount + 1,
            )
        }
        insertNotificationBlocking(merged)
    }

    @Query("UPDATE captured_notifications SET isInSystemNotificationBar = 0 WHERE sessionId = :sessionId AND notificationKey = :notificationKey")
    suspend fun markNotificationCancelled(sessionId: String, notificationKey: String)

    @Query("UPDATE captured_notifications SET isInSystemNotificationBar = 0 WHERE sessionId = :sessionId AND notificationKey = :notificationKey")
    fun markNotificationCancelledBlocking(sessionId: String, notificationKey: String)

    @Query("DELETE FROM captured_notifications WHERE sessionId = :sessionId AND packageName = :packageName AND groupKey = :groupKey AND isGroupSummary = 1")
    fun deleteGroupSummaries(sessionId: String, packageName: String, groupKey: String)

    @Query("DELETE FROM captured_notifications")
    suspend fun clearNotifications()

    @Query("SELECT COUNT(*) FROM captured_notifications WHERE sessionId = :sessionId")
    fun notificationCountBlocking(sessionId: String): Int

    @Query("DELETE FROM captured_notifications WHERE lastUpdatedAtMillis < :cutoffMillis AND (:activeSessionId IS NULL OR sessionId != :activeSessionId)")
    suspend fun deleteNotificationsOlderThan(cutoffMillis: Long, activeSessionId: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFocusSession(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_session WHERE singletonId = 1")
    suspend fun focusSession(): FocusSessionEntity?

    @Query("DELETE FROM focus_session")
    suspend fun clearFocusSession()
}

@Database(entities = [FocusBlockEntity::class, CapturedNotificationEntity::class, FocusSessionEntity::class], version = 2, exportSchema = true)
abstract class NonotiDatabase : RoomDatabase() {
    abstract fun dao(): NonotiDao
}
