package com.jarvis.assistant.conversation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * ZARA AI — Continuous Conversation Manager
 *
 * Implements the "Talk to Me Until I Explicitly Say Stop" architecture:
 * 1. Infinite turn loop: IDLE -> WAKE -> LISTENING -> TRANSCRIBING -> THINKING -> SPEAKING -> LISTENING (LOOP)
 * 2. Eliminates all arbitrary timeouts (15s, 30s, 60s, 30m).
 * 3. Distinguishes barge-in interruptions ("wait", "hold on", "stop talking")
 *    from explicit session stop commands ("Zara stop", "Goodbye", "Podhum", "Niruthi", "Thoongu").
 * 4. Generates courteous farewell responses in English, Tamil, and Hindi.
 * 5. Handles phone call pause and resume without losing session context.
 * 6. Tracks session metrics (turns, duration, user activity).
 */
object ContinuousConversationManager {

    private const val TAG = "ContinuousConvMgr"

    enum class SessionState {
        IDLE,
        WAKE_DETECTED,
        LISTENING,
        TRANSCRIBING,
        THINKING,
        SPEAKING,
        PAUSED_PHONE_CALL,
        STOP_REQUESTED
    }

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _isContinuousModeActive = MutableStateFlow(true)
    val isContinuousModeActive: StateFlow<Boolean> = _isContinuousModeActive.asStateFlow()

    private var sessionStartTime: Long = 0L
    private var totalTurnsCount: Int = 0
    private var lastUserSpeechTime: Long = 0L
    private var isCallPaused: Boolean = false

    fun startSession(): Long {
        sessionStartTime = System.currentTimeMillis()
        totalTurnsCount = 0
        lastUserSpeechTime = sessionStartTime
        isCallPaused = false
        _sessionState.value = SessionState.LISTENING
        Log.i(TAG, "Continuous Conversation Session STARTED at $sessionStartTime. Infinite turn loop active.")
        return sessionStartTime
    }

    fun onUserSpeechDetected() {
        lastUserSpeechTime = System.currentTimeMillis()
        _sessionState.value = SessionState.TRANSCRIBING
    }

    fun onThinking() {
        _sessionState.value = SessionState.THINKING
    }

    fun onSpeaking() {
        _sessionState.value = SessionState.SPEAKING
    }

    fun onPlaybackFinished() {
        totalTurnsCount++
        _sessionState.value = SessionState.LISTENING
        Log.i(TAG, "Turn #$totalTurnsCount completed. Transitioned immediately to LISTENING. Waiting for user's next utterance.")
    }

    fun onPhoneCallStarted() {
        if (!isCallPaused) {
            isCallPaused = true
            _sessionState.value = SessionState.PAUSED_PHONE_CALL
            Log.i(TAG, "Phone call detected. Active continuous conversation PAUSED.")
        }
    }

    fun onPhoneCallEnded() {
        if (isCallPaused) {
            isCallPaused = false
            _sessionState.value = SessionState.LISTENING
            lastUserSpeechTime = System.currentTimeMillis()
            Log.i(TAG, "Phone call ended. Continuous conversation RESUMED in LISTENING mode.")
        }
    }

    fun isPhoneCallPaused(): Boolean = isCallPaused

    fun getSessionMetrics(): Map<String, Any> {
        val now = System.currentTimeMillis()
        val durationMs = if (sessionStartTime > 0) now - sessionStartTime else 0L
        return mapOf(
            "durationSeconds" to (durationMs / 1000L),
            "totalTurns" to totalTurnsCount,
            "currentState" to _sessionState.value.name,
            "isCallPaused" to isCallPaused
        )
    }

    fun reset() {
        sessionStartTime = 0L
        totalTurnsCount = 0
        lastUserSpeechTime = 0L
        isCallPaused = false
        _sessionState.value = SessionState.IDLE
        Log.i(TAG, "Continuous Conversation Session RESET.")
    }

    /**
     * Determines if user utterance is an EXPLICIT session termination command.
     * This commands Zara to end the conversation session and return to standby.
     */
    fun isExplicitSessionStopCommand(text: String): Boolean {
        val clean = text.lowercase(Locale.ROOT).trim()
        if (clean.isBlank()) return false

        // Whole-word phrase matcher
        fun containsPhrase(haystack: String, needle: String): Boolean {
            val pattern = "(?<![\\p{L}\\p{N}])" + Regex.escape(needle) + "(?![\\p{L}\\p{N}])"
            return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(haystack)
        }

        val stopPhrases = listOf(
            // English explicit stop commands
            "zara stop", "stop zara", "stop listening", "stop conversation",
            "stop session", "goodbye zara", "goodbye", "good bye zara",
            "good bye", "bye zara", "bye bye zara", "deactivate zara",
            "deactivate", "go to sleep", "sleep zara", "that's all for now",
            "thats all for now", "that is all for now", "that's all zara",
            "thats all zara", "that's all", "thats all", "that is all",
            "we are done", "we're done", "i'm done", "im done",
            "done for now", "end session", "end conversation",
            "close conversation", "go to background", "go background",
            "go in background", "go to standby", "enter standby",
            "standby mode", "take a break",

            // Tamil explicit stop commands
            "podhum", "pothum", "podhum zara", "pothum zara",
            "nillu", "niruthu", "niruthi vai", "niruthu zara", "nillu zara",
            "mudithukol", "mudichuko", "mudichukko", "mudithu vidu",
            "thoongu", "thoongu zara", "vidaiperu", "varen zara",
            "pesatha", "pesathinga", "seri podhum", "ithu podhum",

            // Hindi / Urdu stop commands
            "ruk jao zara", "ruk jao", "alvida", "alvida zara",
            "bas karo zara", "bas karo", "so jao", "so jao zara",
            "band karo", "chalo bye", "background me jao", "background mein jao"
        )

        val matched = stopPhrases.firstOrNull { containsPhrase(clean, it) } ?: return false

        // Guard against negation words before the stop command (e.g., "don't stop listening", "never stop")
        val matchIndex = clean.indexOf(matched)
        if (matchIndex > 0) {
            val precedingWords = clean.take(matchIndex).trim().split(Regex("\\s+")).takeLast(3)
            val negationWords = setOf("not", "dont", "don't", "never", "without", "mat", "nahi")
            if (precedingWords.any { it in negationWords }) {
                return false
            }
        }

        return true
    }

    /**
     * Generates an appropriate, courteous farewell message based on language detected.
     */
    fun getFarewellResponse(text: String): String {
        val clean = text.lowercase(Locale.ROOT).trim()

        // Check Tamil
        val tamilMarkers = listOf("podhum", "pothum", "nillu", "niruthu", "mudithukol", "mudichuko", "thoongu", "vidaiperu", "varen", "pesatha")
        if (tamilMarkers.any { clean.contains(it) }) {
            return "சரி மோகன்ராஜ், நான் ஸ்டேண்ட்பைக்கு போறேன். தேவைப்பட்டா கூப்பிடுங்க!"
        }

        // Check Hindi
        val hindiMarkers = listOf("ruk jao", "alvida", "bas karo", "so jao", "band karo")
        if (hindiMarkers.any { clean.contains(it) }) {
            return "Alvida Mohanraj. Main standby mein ja rahi hoon, zaroorat padne par bula lena!"
        }

        // Default English
        return "Goodbye Mohanraj! I'll be right here in standby whenever you need me."
    }
}
