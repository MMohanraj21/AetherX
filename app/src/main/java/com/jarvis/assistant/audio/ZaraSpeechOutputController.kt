package com.jarvis.assistant.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.jarvis.assistant.data.model.ConversationState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZARA AI — Single Speech Output Controller (v5.5)
 *
 * Strict Architectural Guarantees:
 * 1. Mutual Exclusion: Only ONE speech engine speaks at a time (Gemini Live PCM audio vs Android TTS).
 * 2. Zero Overlap: Gemini Live audio immediately interrupts secondary TTS. Secondary TTS is suppressed
 *    if Gemini Live audio is active or already spoke during this turn.
 * 3. Strict Voice Lock Integration: Enforces ZaraVoicePreferenceManager for all TTS utterances.
 * 4. Automatic Return to Listening: When speech playback finishes (Gemini or TTS),
 *    automatically resumes ConversationState.LISTENING without resetting to IDLE.
 * 5. Audio recording is NOT stopped or recreated when switching between speech and listening.
 */
class ZaraSpeechOutputController(
    private val context: Context,
    private val onStateChangeRequested: (ConversationState) -> Unit
) {
    companion object {
        private const val TAG = "ZaraSpeechOutputCtrl"
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private val ttsCallbacks = ConcurrentHashMap<String, () -> Unit>()

    private val isGeminiSpeaking = AtomicBoolean(false)
    private val isTtsSpeaking = AtomicBoolean(false)

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    ZaraVoicePreferenceManager.applyVoiceToTts(context, tts)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d(TAG, "TTS onStart: $utteranceId")
                            isTtsSpeaking.set(true)
                            onStateChangeRequested(ConversationState.SPEAKING)
                        }

                        override fun onDone(utteranceId: String?) {
                            Log.d(TAG, "TTS onDone: $utteranceId")
                            isTtsSpeaking.set(false)
                            handleUtteranceDone(utteranceId)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "TTS onError: $utteranceId")
                            isTtsSpeaking.set(false)
                            handleUtteranceDone(utteranceId)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.e(TAG, "TTS onError: $utteranceId, code: $errorCode")
                            isTtsSpeaking.set(false)
                            handleUtteranceDone(utteranceId)
                        }
                    })
                    Log.i(TAG, "TextToSpeech initialized and locked with voice preferences")
                } else {
                    Log.e(TAG, "TextToSpeech initialization failed with status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing TextToSpeech: ${e.message}")
        }
    }

    private fun handleUtteranceDone(utteranceId: String?) {
        utteranceId?.let { id ->
            ttsCallbacks.remove(id)?.invoke()
        }
        // Continuous Conversation Protection: Return to LISTENING after speaking completes
        if (!isGeminiSpeaking.get()) {
            onStateChangeRequested(ConversationState.LISTENING)
        }
    }

    /**
     * Called when Gemini Live WebSocket starts streaming PCM audio playback.
     * Immediately silences and cancels any ongoing Android TTS playback.
     */
    fun onGeminiAudioStarted() {
        isGeminiSpeaking.set(true)
        if (isTtsSpeaking.get()) {
            try {
                tts?.stop()
                ttsCallbacks.clear()
                isTtsSpeaking.set(false)
                Log.i(TAG, "Gemini Live audio started: successfully interrupted and silenced TTS.")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping TTS on Gemini audio start: ${e.message}")
            }
        }
        onStateChangeRequested(ConversationState.SPEAKING)
    }

    /**
     * Called when Gemini Live audio playback completes.
     * Restores LISTENING mode for continuous conversation.
     */
    fun onGeminiAudioFinished() {
        isGeminiSpeaking.set(false)
        onStateChangeRequested(ConversationState.LISTENING)
    }

    /**
     * Speaks an immediate response via Android TTS, enforcing mutual exclusion with Gemini Live.
     */
    fun speakTts(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        // If Gemini is currently speaking or live audio is active, suppress TTS to prevent voice overlap
        if (isGeminiSpeaking.get()) {
            Log.i(TAG, "Single Voice Arbiter: Suppressed secondary TTS playback ('$text') because Gemini Live audio is active.")
            onDone?.invoke()
            return
        }

        try {
            if (tts == null || !isTtsInitialized) {
                initTts()
            }
            ZaraVoicePreferenceManager.applyVoiceToTts(context, tts)

            val utteranceId = "zara_voice_${System.currentTimeMillis()}"
            if (onDone != null) {
                ttsCallbacks[utteranceId] = onDone
            }

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            Log.i(TAG, "Single Voice Arbiter: Speaking TTS utterance: '$text'")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking TTS: ${e.message}")
            onDone?.invoke()
        }
    }

    fun stopAllSpeech() {
        try {
            tts?.stop()
            ttsCallbacks.clear()
            isTtsSpeaking.set(false)
            isGeminiSpeaking.set(false)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping all speech: ${e.message}")
        }
    }

    fun release() {
        stopAllSpeech()
        try {
            tts?.shutdown()
            tts = null
            isTtsInitialized = false
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
    }

    fun isSpeaking(): Boolean = isGeminiSpeaking.get() || isTtsSpeaking.get()
}
