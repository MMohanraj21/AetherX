package com.jarvis.assistant.organizer.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM zara_tasks ORDER BY isCompleted ASC, createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM zara_tasks WHERE isCompleted = 0 ORDER BY createdAt DESC")
    suspend fun getPendingTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE zara_tasks SET isCompleted = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("UPDATE zara_tasks SET isCompleted = 1 WHERE title LIKE '%' || :title || '%'")
    suspend fun markCompletedByTitle(title: String): Int

    @Query("DELETE FROM zara_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)
}
