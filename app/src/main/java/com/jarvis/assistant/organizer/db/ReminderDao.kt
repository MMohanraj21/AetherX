package com.jarvis.assistant.organizer.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM zara_reminders ORDER BY triggerTimeMs ASC")
    fun getAllRemindersFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM zara_reminders WHERE isCompleted = 0 ORDER BY triggerTimeMs ASC")
    suspend fun getPendingReminders(): List<ReminderEntity>

    @Query("SELECT * FROM zara_reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE zara_reminders SET isCompleted = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("DELETE FROM zara_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM zara_reminders WHERE title LIKE '%' || :title || '%'")
    suspend fun deleteReminderByTitle(title: String): Int
}
