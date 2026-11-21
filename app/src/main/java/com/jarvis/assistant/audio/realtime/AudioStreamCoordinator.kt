package com.jarvis.assistant.audio.realtime

import android.util.Log
import com.jarvis.assistant.network.GeminiLiveWebSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates bidirectional audio streaming between LiveAudioEngine and GeminiLiveWebSocket.
 *
 * Implements pre-connection audio staging (preventing syllable loss while socket connects),
 * atomic turn epoch sequencing, and instant turn abort on user barge-in.
 */
class AudioStreamCoordinator(
    private var webSocket: GeminiLiveWebSocket?
) {
    companion object {
        private const val TAG = "AudioStreamCoordinator"
        private const val MAX_PRECONNECT_QUEUE_SIZE = 60 // ~1.2s audio buffer
    }

    private val preConnectQueue = ConcurrentLinkedQueue<ByteArray>()
    private val activeTurnEpoch = AtomicLong(1L)
    private val isAborted = AtomicBoolean(false)

    fun updateWebSocket(ws: GeminiLiveWebSocket?) {
        this.webSocket = ws
    }

    fun startNewTurn(): Long {
        isAborted.set(false)
        return activeTurnEpoch.incrementAndGet()
    }

    fun getActiveEpoch(): Long = activeTurnEpoch.get()

    /**
     * Streams an uplink 16kHz PCM audio chunk to Gemini Live.
     */
    fun sendAudioChunk(chunk: ByteArray) {
        val ws = webSocket
        if (ws != null && ws.isSessionReady()) {
            // Flush any pre-connect buffered audio first
            while (!preConnectQueue.isEmpty()) {
                val queued = preConnectQueue.poll() ?: break
                ws.sendAudioChunk(queued)
            }
            ws.sendAudioChunk(chunk)
        } else {
            // Socket warming up: retain recent frames
            if (preConnectQueue.size < MAX_PRECONNECT_QUEUE_SIZE) {
                preConnectQueue.offer(chunk.copyOf())
            }
        }
    }

    /**
     * Triggered on user barge-in: aborts in-flight generation on the server
     * and advances the turn epoch so stale downlink audio chunks are discarded.
     */
    fun abortInFlightTurn(): Long {
        val newEpoch = activeTurnEpoch.incrementAndGet()
        isAborted.set(true)
        preConnectQueue.clear()
        Log.i(TAG, "Turn aborted by barge-in. Advanced turn epoch to $newEpoch")
        try {
            webSocket?.sendAudioStreamEnd()
        } catch (e: Exception) {
            Log.w(TAG, "Error notifying server of turn abort: ${e.message}")
        }
        return newEpoch
    }

    fun isTurnAborted(epoch: Long): Boolean {
        return epoch < activeTurnEpoch.get()
    }

    fun clear() {
        preConnectQueue.clear()
    }
}
