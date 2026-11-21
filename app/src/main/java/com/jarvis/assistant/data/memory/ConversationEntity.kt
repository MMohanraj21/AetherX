package com.jarvis.assistant.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val conversationId: String,
    val title: String = "Conversation",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val summary: String = "",
    val turnCount: Int = 0
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            return sdf.format(Date(startTime))
        }
}
