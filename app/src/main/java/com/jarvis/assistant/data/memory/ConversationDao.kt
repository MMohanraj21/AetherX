package com.jarvis.assistant.data.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // Conversations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY startTime DESC")
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY startTime DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE conversationId = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("DELETE FROM conversations WHERE conversationId = :id")
    suspend fun deleteConversationById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    // Messages
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversationFlow(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchMessages(query: String, limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 100): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    // Conversation Summaries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: ConversationSummaryEntity): Long

    @Query("SELECT * FROM conversation_summaries WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getSummaryForConversation(conversationId: String): ConversationSummaryEntity?

    @Query("SELECT * FROM conversation_summaries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSummaries(limit: Int = 20): List<ConversationSummaryEntity>

    // Task Contexts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskContext(task: TaskContextEntity)

    @Update
    suspend fun updateTaskContext(task: TaskContextEntity)

    @Query("SELECT * FROM task_contexts WHERE status = 'ACTIVE' ORDER BY updatedAt DESC")
    suspend fun getActiveTasks(): List<TaskContextEntity>

    @Query("SELECT * FROM task_contexts WHERE taskId = :taskId LIMIT 1")
    suspend fun getTaskContextById(taskId: String): TaskContextEntity?

    @Query("DELETE FROM task_contexts WHERE taskId = :taskId")
    suspend fun deleteTaskContext(taskId: String)
}
