package com.jarvis.assistant.data.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_summaries",
    indices = [
        Index("conversationId")
    ]
)
data class ConversationSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String,
    val summaryText: String,
    val keyTakeaways: String = "",
    val topics: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
