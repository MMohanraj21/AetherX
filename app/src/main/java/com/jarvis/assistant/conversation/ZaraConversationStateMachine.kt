package com.jarvis.assistant.conversation

import android.content.Context
import android.util.Log
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.island.OriginIslandController
import com.jarvis.assistant.notification.ZaraNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * ZARA AI — Single Authoritative Conversation State Machine (v5.5.1)
 *
 * Enforces Absolute State Sequence:
 * IDLE -> LISTENING -> PROCESSING -> SPEAKING -> LISTENING
 *
 * Rules:
 * 1. LISTENING activates only when microphone is actively capturing user input or session starts.
 * 2. PROCESSING activates when a valid user utterance is being evaluated / sent to model.
 * 3. SPEAKING remains locked and active throughout the entire audio playback duration.
 * 4. SPEAKING must NEVER switch to LISTENING because of incoming text chunks, partial transcripts,
 *    or intermediate network packet buffer underruns.
 * 5. LISTENING resumes only after speech playback has genuinely finished (or genuine confirmed barge-in).
 * 6. Duplicate, stale, or out-of-order state events are strictly dropped.
 * 7. Synchronizes UI, Origin Island, and Ongoing Notification atomically.
 */
class ZaraConversationStateMachine(
    private val context: Context,
    private val onStateChangedListener: ((ConversationState) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ZaraStateMachine"
    }

    enum class EventType {
        SESSION_START,
        SESSION_STOP,
        USER_SPEECH_START,
        USER_SPEECH_END,
        PROCESSING_START,
        PLAYBACK_START,
        PLAYBACK_CHUNK_RECEIVED,
        PLAYBACK_FINISH,
        BARGE_IN_CONFIRMED,
        ERROR_OCCURRED,
        TIMEOUT_OCCURRED
    }

    data class StateTransitionEvent(
        val sessionId: String,
        val turnId: Long,
        val eventType: EventType,
        val source: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    @Volatile
    var currentSessionId: String = UUID.randomUUID().toString()
        private set

    private val turnCounter = AtomicLong(0)
    val currentTurnId: Long
        get() = turnCounter.get()

    private val stateLock = Any()

    /**
     * Starts a new conversation session. Resets turn ID and transitions to LISTENING.
     */
    fun startSession(reason: String): String = synchronized(stateLock) {
        currentSessionId = UUID.randomUUID().toString()
        turnCounter.set(0)
        Log.i(TAG, "[$currentSessionId] Session started: reason=$reason")
        transitionTo(
            targetState = ConversationState.LISTENING,
            event = StateTransitionEvent(
                sessionId = currentSessionId,
                turnId = turnCounter.get(),
                eventType = EventType.SESSION_START,
                source = reason
            )
        )
        currentSessionId
    }

    /**
     * Ends the conversation session and returns to IDLE.
     */
    fun endSession(reason: String) = synchronized(stateLock) {
        Log.i(TAG, "[$currentSessionId] Session ended: reason=$reason")
        transitionTo(
            targetState = ConversationState.IDLE,
            event = StateTransitionEvent(
                sessionId = currentSessionId,
                turnId = turnCounter.get(),
                eventType = EventType.SESSION_STOP,
                source = reason
            )
        )
    }

    /**
     * Increments the turn counter and signals speech capture has started.
     */
    fun onUserSpeechStart(source: String): Long = synchronized(stateLock) {
        val newTurn = turnCounter.incrementAndGet()
        Log.d(TAG, "[$currentSessionId:Turn#$newTurn] User speech started ($source)")
        transitionTo(
            targetState = ConversationState.LISTENING,
            event = StateTransitionEvent(
                sessionId = currentSessionId,
                turnId = newTurn,
                eventType = EventType.USER_SPEECH_START,
                source = source
            )
        )
        newTurn
    }

    /**
     * Signals user speech ended and turn is processing.
     */
    fun onProcessingStart(turnId: Long, source: String) = synchronized(stateLock) {
        if (!validateTurn("onProcessingStart", turnId)) return
        Log.i(TAG, "[$currentSessionId:Turn#$turnId] Processing user utterance ($source)")
        transitionTo(
            targetState = ConversationState.PROCESSING,
            event = StateTransitionEvent(
                sessionId = currentSessionId,
                turnId = turnId,
                eventType = EventType.PROCESSING_START,
                source = source
            )
        )
    }

    /**
     * Signals audio playback has started (Gemini Live PCM or Android TTS).
     * Locks state in SPEAKING.
     */
    fun onPlaybackStart(turnId: Long, source: String) = synchronized(stateLock) {
        if (!validateTurn("onPlaybackStart", turnId)) return
        Log.i(TAG, "[$currentSessionId:Turn#$turnId] Playback started: $source")
        transitionTo(
            targetState = ConversationState.SPEAKING,
            event = StateTransitionEvent(
                sessionId = currentSessionId,
                turnId = turnId,
                eventType = EventType.PLAYBACK_START,
                source = source
            )
        )
    }

    /**
     * Signals audio chunk received while speaking. Ensures SPEAKING remains locked.
     */
    fun onPlaybackChunk(turnId: Long, source: String) = synchronized(stateLock) {
        if (!validateTurn("onPlaybackChunk", turnId)) return
        if (_state.value != ConversationState.SPEAKING) {
            transitionTo(
                targetState = ConversationState.SPEAKING,
                event = StateTransitionEvent(
                    sessionId = currentSessionId,
                    turnId = turnId,
                    eventType = EventType.PLAYBACK_CHUNK_RECEIVED,
                    source = source
                )
            )
        }
    }

    /**
     * Signals audio playback has genuinely completed for the current turn.
     * Restores LISTENING mode for unlimited continuous conversation.
     */
    fun onPlaybackFinish(turnId: Long, source: String, continueListening: Boolean = true) = synchronized(stateLock) {
        if (!validateTurn("onPlaybackFinish", turnId)) return
        Log.i(TAG, "[$currentSessionId:Turn#$turnId] Playback finished: $source, continueListening=$continueListening")
        val target = if (continueListening) ConversationState.LISTENING else ConversationState.IDLE
        transitionTo(
            targetState = target,
            event = StateTransitionEvent(
                sessionId = currentSessionId,
                turnId = turnId,
                eventType = EventType.PLAYBACK_FINISH,
                source = source
            )
        )
    }

    /**
     * Confirmed user barge-in (speech detected during playback with verified threshold).
     * Aborts playback and immediately transitions to LISTENING.
     */
    fun onBargeInConfirmed(turnId: Long, source: String) = synchronized(stateLock) {
        if (!validateTurn("onBargeInConfirmed", turnId)) return
        Log.i(TAG, "[$currentSessionId:Turn#$turnId] Confirmed barge-in: $source")
        transitionTo(
            targetState = ConversationState.LISTENING,
            event = StateTransitionEvent(
                sessionId = currentSessionId,
                turnId = turnId,
                eventType = EventType.BARGE_IN_CONFIRMED,
                source = source
            )
        )
    }

    /**
     * Validates whether a turnId belongs to the current or recent turn.
     */
    private fun validateTurn(action: String, turnId: Long): Boolean {
        val current = turnCounter.get()
        if (turnId > 0 && turnId < current - 1) {
            Log.w(TAG, "[$currentSessionId] Discarding stale turn event '$action' for turn#$turnId (current=$current)")
            return false
        }
        return true
    }

    /**
     * Authoritative transition validator and dispatcher.
     */
    private fun transitionTo(targetState: ConversationState, event: StateTransitionEvent) {
        val currentState = _state.value

        // 1. Duplicate suppression
        if (currentState == targetState) {
            Log.d(TAG, "[$currentSessionId:Turn#${event.turnId}] Duplicate state '${targetState.name}' suppressed (${event.eventType})")
            return
        }

        // 2. Strict State Transition Validation Rules:
        // Rule: SPEAKING cannot be cancelled by a partial transcript or audio gap
        if (currentState == ConversationState.SPEAKING && targetState == ConversationState.LISTENING) {
            val isAuthorizedExit = event.eventType == EventType.PLAYBACK_FINISH ||
                    event.eventType == EventType.BARGE_IN_CONFIRMED ||
                    event.eventType == EventType.SESSION_STOP ||
                    event.eventType == EventType.TIMEOUT_OCCURRED
            if (!isAuthorizedExit) {
                Log.w(TAG, "[$currentSessionId:Turn#${event.turnId}] REJECTED transition SPEAKING -> LISTENING from unauthorized event: ${event.eventType} (${event.source})")
                return
            }
        }

        // Rule: PROCESSING cannot transition to LISTENING unless cancelled or timed out
        if (currentState == ConversationState.PROCESSING && targetState == ConversationState.LISTENING) {
            val isAuthorizedExit = event.eventType == EventType.USER_SPEECH_START ||
                    event.eventType == EventType.TIMEOUT_OCCURRED ||
                    event.eventType == EventType.BARGE_IN_CONFIRMED ||
                    event.eventType == EventType.SESSION_STOP
            if (!isAuthorizedExit) {
                Log.w(TAG, "[$currentSessionId:Turn#${event.turnId}] REJECTED transition PROCESSING -> LISTENING from unauthorized event: ${event.eventType}")
                return
            }
        }

        Log.i(TAG, "STATE TRANSITION: [${currentState.name}] -> [${targetState.name}] via ${event.eventType} (Turn#${event.turnId}, source='${event.source}')")
        _state.value = targetState

        // Synchronize notification and island
        try {
            ZaraNotificationManager.updateNotification(
                context = context,
                state = targetState,
                isStandby = (targetState == ConversationState.IDLE)
            )
            OriginIslandController.updateVoiceState(context, targetState)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating notification/island on transition: ${e.message}")
        }

        // Notify external listener
        try {
            onStateChangedListener?.invoke(targetState)
        } catch (e: Exception) {
            Log.w(TAG, "Error in onStateChangedListener: ${e.message}")
        }
    }
}
