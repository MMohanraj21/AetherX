package com.jarvis.assistant.organizer

import android.content.Context
import com.jarvis.assistant.organizer.db.TaskEntity
import com.jarvis.assistant.organizer.db.ZaraOrganizerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskManager(context: Context) {
    private val taskDao = ZaraOrganizerDatabase.getInstance(context).taskDao()

    suspend fun addTask(
        title: String,
        dueDateMs: Long = 0L,
        priority: String = "MEDIUM"
    ): TaskEntity = withContext(Dispatchers.IO) {
        val task = TaskEntity(
            title = title,
            dueDateMs = dueDateMs,
            priority = priority
        )
        val id = taskDao.insertTask(task)
        task.copy(id = id)
    }

    suspend fun getPendingTasks(): List<TaskEntity> = withContext(Dispatchers.IO) {
        taskDao.getPendingTasks()
    }

    suspend fun completeTask(title: String): Boolean = withContext(Dispatchers.IO) {
        taskDao.markCompletedByTitle(title) > 0
    }

    suspend fun completeTaskById(id: Long) = withContext(Dispatchers.IO) {
        taskDao.markCompleted(id)
    }
}
