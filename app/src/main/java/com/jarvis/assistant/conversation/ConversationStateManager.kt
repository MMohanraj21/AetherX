package com.jarvis.assistant.conversation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages active multi-turn conversational state, active topics, resolved entities,
 * and pending clarifications across turns.
 */
object ConversationStateManager {

    private const val TAG = "ConvStateManager"

    data class ResolvedEntity(
        val type: EntityType,
        val value: String,
        val originalMention: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class EntityType {
        PERSON_CONTACT,
        APPLICATION,
        SONG_MEDIA,
        LOCATION,
        FILE,
        DATE_TIME
    }

    data class PendingClarification(
        val clarificationId: String = UUID.randomUUID().toString(),
        val intentType: String,
        val missingField: String,
        val questionAsked: String,
        val partialPayload: Map<String, String> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _activeConversationId = MutableStateFlow(UUID.randomUUID().toString())
    val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()

    private val _activeTopic = MutableStateFlow<String?>(null)
    val activeTopic: StateFlow<String?> = _activeTopic.asStateFlow()

    private val recentEntities = mutableListOf<ResolvedEntity>()
    private val _pendingClarification = MutableStateFlow<PendingClarification?>(null)
    val pendingClarification: StateFlow<PendingClarification?> = _pendingClarification.asStateFlow()

    private var lastTurnTimestamp = System.currentTimeMillis()

    fun startNewSession(): String {
        val newId = UUID.randomUUID().toString()
        _activeConversationId.value = newId
        _activeTopic.value = null
        _pendingClarification.value = null
        synchronized(recentEntities) {
            recentEntities.clear()
        }
        lastTurnTimestamp = System.currentTimeMillis()
        Log.i(TAG, "Started new conversational session: $newId")
        return newId
    }

    fun setTopic(topic: String) {
        _activeTopic.value = topic
        lastTurnTimestamp = System.currentTimeMillis()
    }

    fun recordEntity(type: EntityType, value: String, originalMention: String) {
        synchronized(recentEntities) {
            recentEntities.removeAll { it.type == type && it.value.equals(value, ignoreCase = true) }
            recentEntities.add(0, ResolvedEntity(type, value, originalMention))
            if (recentEntities.size > 15) {
                recentEntities.removeAt(recentEntities.lastIndex)
            }
        }
        lastTurnTimestamp = System.currentTimeMillis()
        Log.d(TAG, "Recorded entity: [$type] '$value' (mention: '$originalMention')")
    }

    fun getLatestEntity(type: EntityType): ResolvedEntity? {
        synchronized(recentEntities) {
            val now = System.currentTimeMillis()
            // Entities valid for up to 5 minutes
            return recentEntities.firstOrNull { it.type == type && (now - it.timestamp < 300_000L) }
        }
    }

    fun setPendingClarification(clarification: PendingClarification) {
        _pendingClarification.value = clarification
        lastTurnTimestamp = System.currentTimeMillis()
        Log.i(TAG, "Set pending clarification for intent '${clarification.intentType}', missing: ${clarification.missingField}")
    }

    fun clearPendingClarification() {
        _pendingClarification.value = null
    }

    fun hasPendingClarification(): Boolean {
        val p = _pendingClarification.value ?: return false
        val isValid = (System.currentTimeMillis() - p.timestamp) < 60_000L // 60s timeout
        if (!isValid) {
            _pendingClarification.value = null
            return false
        }
        return true
    }

    fun getRecentEntitiesSnapshot(): List<ResolvedEntity> {
        synchronized(recentEntities) {
            return recentEntities.toList()
        }
    }

    /**
     * Generates a context injection snippet containing current topic and recent entities
     * to inject into Gemini Live setup instructions upon reconnection.
     */
    fun generateContextPrompt(): String {
        val sb = StringBuilder()
        val topic = _activeTopic.value
        if (!topic.isNullOrBlank()) {
            sb.append("ACTIVE_CONVERSATION_TOPIC: $topic\n")
        }
        val entities = getRecentEntitiesSnapshot()
        if (entities.isNotEmpty()) {
            sb.append("RECENT_CONVERSATION_ENTITIES:\n")
            for (e in entities.take(6)) {
                sb.append("- [${e.type}]: ${e.value} (mentioned as: \"${e.originalMention}\")\n")
            }
        }
        return sb.toString().trim()
    }
}
