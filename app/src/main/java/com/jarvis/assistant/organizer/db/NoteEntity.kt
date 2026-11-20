package com.jarvis.assistant.organizer.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zara_notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "General",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
