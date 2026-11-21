package com.jarvis.assistant.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_contexts")
data class TaskContextEntity(
    @PrimaryKey
    val taskId: String,
    val title: String,
    val goal: String,
    val status: String, // "ACTIVE", "PAUSED", "COMPLETED", "CANCELLED"
    val contextJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
