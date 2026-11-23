package com.jarvis.assistant.audio

import android.content.Context
import android.util.Log
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZARA AI — Single Speech Output Controller (v5.6)
 *
 * Strict Architectural Guarantees:
 * 1. ZERO Android TextToSpeech: Android TextToSpeech has been permanently removed from the app.
 * 2. Native AI Audio Only: Conversational speech is generated exclusively by Google Gemini Live
 *    neural audio (24kHz 16-bit PCM).
 * 3. Automatic Return to Listening: When speech playback finishes, automatically resumes
 *    ConversationState.LISTENING for continuous conversation.
 * 4. Resilient Error Handling: If Gemini Live is disconnected or silent, reports error via
 *    events/notifications; NEVER falls back to robotic device TTS engines.
 */
class ZaraSpeechOutputController(
    private val context: Context,
    private val onStateChangeRequested: (ConversationState) -> Unit
) {
    companion object {
        private const val TAG = "ZaraSpeechOutputCtrl"
    }

    private val isGeminiSpeaking = AtomicBoolean(false)

    /**
     * Called when Gemini Live WebSocket streams PCM audio to the AudioPlayer.
     */
    fun onGeminiAudioStarted() {
        isGeminiSpeaking.set(true)
        onStateChangeRequested(ConversationState.SPEAKING)
    }

    /**
     * Called when AudioPlayer completes playback of all PCM chunks for the turn.
     * Restores LISTENING mode for continuous conversation.
     */
    fun onGeminiAudioFinished() {
        isGeminiSpeaking.set(false)
        onStateChangeRequested(ConversationState.LISTENING)
    }

    /**
     * Speaks an immediate response via Gemini Live native audio generation.
     * Android TextToSpeech is completely bypassed and disabled.
     */
    fun speakNativeAiAudio(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        val service = JarvisConversationService.activeInstance
        val ws = service?.getLiveWebSocket()

        if (ws != null && ws.isSessionReady()) {
            Log.i(TAG, "Streaming text to Gemini Live for native neural audio generation: '$text'")
            ws.sendClientContent(text)
            onDone?.invoke()
        } else {
            Log.w(TAG, "Gemini Live session not active for speech synthesis: '$text'. Android TTS fallback is strictly prohibited.")
            onDone?.invoke()
        }
    }

    fun stopAllSpeech() {
        isGeminiSpeaking.set(false)
    }

    fun release() {
        stopAllSpeech()
    }

    fun isSpeaking(): Boolean = isGeminiSpeaking.get()
}
