package com.jarvis.assistant.audio.realtime

import android.content.Context
import android.util.Log
import com.jarvis.assistant.audio.AudioFrequencyData
import kotlinx.coroutines.CoroutineScope
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified Live Audio Engine for ZARA AI v5.0.
 *
 * Coordinates low-latency capture, streaming playback, voice activity detection,
 * adaptive end-of-turn prediction, acoustic echo cancellation, and true voice barge-in.
 */
class LiveAudioEngine(
    private val context: Context,
    private val listener: Listener
) {
    companion object {
        private const val TAG = "LiveAudioEngine"
    }

    interface Listener {
        fun onSpeechStart()
        fun onSpeechChunk(chunk: ByteArray, amplitude: Float, frequencyData: AudioFrequencyData)
        fun onEndOfTurn(audioUtterance: ByteArray)
        fun onBargeIn()
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onPlaybackAmplitude(amplitude: Float)
    }

    private val focusController = AudioFocusController(context)
    private val vad = VoiceActivityDetector()

    private val currentUtteranceStream = ByteArrayOutputStream()
    private val isUtteranceActive = AtomicBoolean(false)
    private var utteranceStartTime = 0L
    private var lastVoicedTime = 0L

    private val playbackManager = AudioPlaybackManager(
        onPlaybackStateChanged = { isPlaying ->
            listener.onPlaybackStateChanged(isPlaying)
        },
        onPlaybackAmplitude = { amp ->
            listener.onPlaybackAmplitude(amp)
        }
    )

    private val captureManager = AudioCaptureManager { chunk, amplitude, frequencyData ->
        handleCapturedChunk(chunk, amplitude, frequencyData)
    }

    private var hasHeadset = false
    private var currentTranscribedText = ""

    fun start(scope: CoroutineScope): Boolean {
        focusController.requestFocus(exclusive = true)
        playbackManager.start()
        val ok = captureManager.start(scope)
        if (!ok) {
            Log.e(TAG, "Failed to start AudioCaptureManager")
        }
        return ok
    }

    fun stop() {
        captureManager.stop()
        playbackManager.release()
        focusController.releaseFocus()
        synchronized(currentUtteranceStream) {
            currentUtteranceStream.reset()
        }
        isUtteranceActive.set(false)
    }

    fun setHeadsetConnected(connected: Boolean) {
        hasHeadset = connected
    }

    fun updateTranscription(partialText: String) {
        currentTranscribedText = partialText
    }

    fun enqueuePlaybackAudio(pcm24k: ByteArray) {
        playbackManager.enqueueAudio(pcm24k)
    }

    fun notifyPlaybackTurnComplete() {
        playbackManager.notifyTurnComplete()
    }

    fun flushPlayback() {
        playbackManager.flush()
    }

    fun isPlaybackActive(): Boolean = playbackManager.isPlaying()

    private fun handleCapturedChunk(chunk: ByteArray, amplitude: Float, frequencyData: AudioFrequencyData) {
        val now = System.currentTimeMillis()
        val isSpeakerActive = playbackManager.isPlaying()

        val analysis = vad.processFrame(chunk, isSpeakerActive, hasHeadset)

        // 1. True Barge-In Detection
        if (analysis.isBargeIn) {
            Log.i(TAG, "True Barge-In triggered! Flushed speaker playback and initiated new user turn.")
            playbackManager.flush()
            startNewUtterance(now)
            listener.onBargeIn()
            return
        }

        // If speaker is actively playing and no barge-in, drop microphone chunks to prevent self-looping
        if (isSpeakerActive) {
            return
        }

        // 2. Speech Start
        if (analysis.isSpeechStart && !isUtteranceActive.get()) {
            startNewUtterance(now)
            listener.onSpeechStart()
        }

        // 3. Active Speech Capture
        if (isUtteranceActive.get()) {
            lastVoicedTime = now
            synchronized(currentUtteranceStream) {
                if (currentUtteranceStream.size() < 480000) { // ~15 seconds max
                    currentUtteranceStream.write(chunk)
                }
            }
            listener.onSpeechChunk(chunk, amplitude, frequencyData)
        } else {
            // Forward chunk to listener for continuous background streaming
            listener.onSpeechChunk(chunk, amplitude, frequencyData)
        }

        // 4. Adaptive End-of-Turn Evaluation
        if (isUtteranceActive.get() && !analysis.isVoiced) {
            val silentDuration = now - lastVoicedTime
            val speechDuration = now - utteranceStartTime
            val isEnd = EndOfTurnDetector.isTurnComplete(silentDuration, currentTranscribedText, speechDuration)

            if (isEnd) {
                isUtteranceActive.set(false)
                val utteranceBytes = synchronized(currentUtteranceStream) {
                    val bytes = currentUtteranceStream.toByteArray()
                    currentUtteranceStream.reset()
                    bytes
                }
                Log.d(TAG, "Adaptive End-of-Turn reached (silentMs=$silentDuration, text='$currentTranscribedText')")
                listener.onEndOfTurn(utteranceBytes)
            }
        }
    }

    private fun startNewUtterance(startTime: Long) {
        isUtteranceActive.set(true)
        utteranceStartTime = startTime
        lastVoicedTime = startTime
        synchronized(currentUtteranceStream) {
            currentUtteranceStream.reset()
            // Prepend pre-speech frames so consonants are not clipped
            val preSpeech = captureManager.extractPreSpeechAudio()
            for (pre in preSpeech) {
                currentUtteranceStream.write(pre)
            }
        }
    }
}
