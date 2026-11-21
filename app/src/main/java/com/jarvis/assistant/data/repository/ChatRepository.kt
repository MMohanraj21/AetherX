package com.jarvis.assistant.data.repository

import com.jarvis.assistant.data.memory.ConversationDao
import com.jarvis.assistant.data.memory.ConversationEntity
import com.jarvis.assistant.data.memory.MessageEntity
import com.jarvis.assistant.data.model.ChatTurn
import com.jarvis.assistant.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Manages conversation history:
 * - Layer A (Immediate Context): In-memory MutableStateFlow of recent turns.
 * - Layer C (Conversation Archive): Searchable Room SQLite tables (`conversations`, `messages`).
 * Preserves legacy turns from SharedPreferences without data loss.
 */
class ChatRepository(
    private val preferences: AppPreferences,
    private val conversationDao: ConversationDao? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    private val sessionId: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "_" + UUID.randomUUID().toString().take(6)
    private var sessionTurnCount = 0

    init {
        val legacyTurns = preferences.loadChatHistory()
        _turns.value = legacyTurns

        // Non-destructive migration of legacy SharedPreferences chat turns into Room SQLite
        if (conversationDao != null && legacyTurns.isNotEmpty()) {
            scope.launch {
                try {
                    val legacySessionId = "legacy_archive"
                    conversationDao.insertConversation(
                        ConversationEntity(
                            conversationId = legacySessionId,
                            title = "Previous Saved Conversations",
                            startTime = System.currentTimeMillis() - 86400000L,
                            endTime = System.currentTimeMillis(),
                            turnCount = legacyTurns.size
                        )
                    )
                    val messages = mutableListOf<MessageEntity>()
                    legacyTurns.forEachIndexed { idx, turn ->
                        val baseTime = System.currentTimeMillis() - ((legacyTurns.size - idx) * 60000L)
                        if (turn.userTranscript.isNotBlank()) {
                            messages.add(
                                MessageEntity(
                                    conversationId = legacySessionId,
                                    sender = "user",
                                    content = turn.userTranscript,
                                    timestamp = baseTime
                                )
                            )
                        }
                        if (turn.jarvisResponse.isNotBlank()) {
                            messages.add(
                                MessageEntity(
                                    conversationId = legacySessionId,
                                    sender = "assistant",
                                    content = turn.jarvisResponse,
                                    timestamp = baseTime + 1000L
                                )
                            )
                        }
                    }
                    conversationDao.insertMessages(messages)
                } catch (_: Exception) {}
            }
        }
    }

    @Synchronized
    fun addTurn(userText: String, jarvisText: String) {
        val cleanUser = userText.trim()
        val cleanJarvis = jarvisText.trim()

        if (cleanUser.isEmpty() && cleanJarvis.isEmpty()) return

        val currentList = _turns.value.toMutableList()

        if (currentList.isNotEmpty()) {
            val last = currentList.last()
            if (last.userTranscript == cleanUser && last.jarvisResponse == cleanJarvis) {
                return
            }
        }

        val newTurn = ChatTurn(
            userTranscript = cleanUser.ifEmpty { "(Audio query)" },
            jarvisResponse = cleanJarvis.ifEmpty { "(Audio response)" }
        )

        currentList.add(newTurn)
        if (currentList.size > 100) {
            currentList.removeAt(0)
        }

        _turns.value = currentList
        preferences.saveChatHistory(currentList)

        // Persist to Layer C Room SQLite Conversation Archive
        conversationDao?.let { dao ->
            sessionTurnCount++
            val now = System.currentTimeMillis()
            scope.launch {
                try {
                    dao.insertConversation(
                        ConversationEntity(
                            conversationId = sessionId,
                            title = cleanUser.take(40).ifBlank { "Session $sessionId" },
                            startTime = now - (sessionTurnCount * 10000L),
                            endTime = now,
                            turnCount = sessionTurnCount
                        )
                    )
                    if (cleanUser.isNotBlank()) {
                        dao.insertMessage(
                            MessageEntity(
                                conversationId = sessionId,
                                sender = "user",
                                content = cleanUser,
                                timestamp = now - 500L
                            )
                        )
                    }
                    if (cleanJarvis.isNotBlank()) {
                        dao.insertMessage(
                            MessageEntity(
                                conversationId = sessionId,
                                sender = "assistant",
                                content = cleanJarvis,
                                timestamp = now
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    @Synchronized
    fun clearHistory() {
        _turns.value = emptyList()
        preferences.clearChatHistory()
    }
}
