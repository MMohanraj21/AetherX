package com.jarvis.assistant.organizer.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zara_reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerTimeMs: Long,
    val repeatIntervalMs: Long = 0L,
    val category: String = "General",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
