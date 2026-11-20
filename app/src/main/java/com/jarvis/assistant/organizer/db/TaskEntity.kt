package com.jarvis.assistant.organizer.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zara_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueDateMs: Long = 0L,
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
