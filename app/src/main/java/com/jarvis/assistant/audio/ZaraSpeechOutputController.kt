package com.jarvis.assistant.audio

import android.content.Context
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.network.GeminiRestClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZARA AI — Single Speech Output Controller (v5.6)
 *
 * Strict Architectural Guarantees:
 * 1. ZERO Android TextToSpeech: Android TextToSpeech has been permanently removed from the app.
 * 2. Native AI Audio Only: Conversational speech is generated exclusively by Google Gemini Live
 *    neural audio (24kHz 16-bit PCM) or Gemini REST neural speech synthesis fallback.
 * 3. Automatic Return to Listening: When speech playback finishes, automatically resumes
 *    ConversationState.LISTENING for continuous conversation.
 * 4. Resilient Error Handling: If Gemini Live is disconnected or silent, seamlessly uses Gemini REST
 *    neural audio synthesis; NEVER falls back to robotic device TTS engines.
 */
class ZaraSpeechOutputController(
    private val context: Context,
    private val onStateChangeRequested: (ConversationState) -> Unit
) {
    companion object {
        private const val TAG = "ZaraSpeechOutputCtrl"
    }

    private val isGeminiSpeaking = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)

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
     * Speaks an immediate response via Gemini Live native audio generation,
     * or Gemini REST neural speech synthesis if Live WebSocket is warming up.
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
            Log.i(TAG, "Gemini Live session not ready. Synthesizing neural audio via Gemini REST API: '$text'")
            val app = context.applicationContext as? JarvisApp
            val apiKey = app?.preferences?.apiKey?.trim().orEmpty()
            val voiceName = ZaraVoicePreferenceManager.getGeminiVoiceName(context)

            if (apiKey.isNotBlank() && service != null) {
                scope.launch {
                    try {
                        val result = GeminiRestClient.synthesizeSpeech(
                            apiKey = apiKey,
                            voiceName = voiceName,
                            text = text
                        )
                        if (result.isSuccess) {
                            val audioBytes = result.getOrNull()
                            if (audioBytes != null && audioBytes.isNotEmpty()) {
                                onGeminiAudioStarted()
                                service.playNativeAudio(audioBytes)
                                val durationMs = (audioBytes.size * 1000L) / (AudioPlayer.SAMPLE_RATE * 2)
                                delay(durationMs + 250)
                                onGeminiAudioFinished()
                            }
                        } else {
                            Log.w(TAG, "GeminiRestClient.synthesizeSpeech failed: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in REST speech fallback: ${e.message}", e)
                    } finally {
                        withContext(Dispatchers.Main) {
                            onDone?.invoke()
                        }
                    }
                }
            } else {
                Log.w(TAG, "Cannot synthesize speech: API key missing or service inactive.")
                onDone?.invoke()
            }
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
