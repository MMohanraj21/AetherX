package com.jarvis.assistant.audio.realtime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.jarvis.assistant.audio.AudioUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Low-latency audio playback manager for ZARA AI v5.0.
 *
 * Plays 24kHz 16-bit mono PCM streamed from Gemini Live with zero buffer underrun
 * and instantaneous atomic flush on user barge-in.
 */
class AudioPlaybackManager(
    private val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit,
    private val onPlaybackAmplitude: (amplitude: Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioPlaybackManager"
        const val SAMPLE_RATE = 24000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    data class AudioEpochChunk(
        val epoch: Long,
        val data: ByteArray
    )

    private var audioTrack: AudioTrack? = null
    private val trackLock = Any()
    private val audioQueue = LinkedBlockingQueue<AudioEpochChunk>()
    private val isRunning = AtomicBoolean(false)
    private val isPlayingState = AtomicBoolean(false)
    private val isTurnComplete = AtomicBoolean(false)
    private var playbackThread: Thread? = null

    // Turn epoch counter: incremented on every user turn or barge-in interruption
    private val currentTurnEpoch = AtomicLong(1L)

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize * 6, 64000)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
    }

    fun startNewTurn(): Long {
        val newEpoch = currentTurnEpoch.incrementAndGet()
        isTurnComplete.set(false)
        return newEpoch
    }

    fun getCurrentEpoch(): Long = currentTurnEpoch.get()

    fun notifyTurnComplete() {
        isTurnComplete.set(true)
    }

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        playbackThread = Thread {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {}

            var silenceCounter = 0
            while (isRunning.get()) {
                try {
                    // Not currently playing: wait for chunks
                    if (!isPlayingState.get()) {
                        val first = audioQueue.poll(30, TimeUnit.MILLISECONDS)
                        if (first != null && first.data.isNotEmpty()) {
                            // Verify chunk matches active turn epoch
                            if (first.epoch < currentTurnEpoch.get()) {
                                continue // Discard obsolete chunk from previous turn
                            }
                            isTurnComplete.set(false)
                            isPlayingState.set(true)
                            silenceCounter = 0
                            onPlaybackStateChanged(true)

                            synchronized(trackLock) {
                                audioTrack?.let { track ->
                                    if (track.state == AudioTrack.STATE_INITIALIZED && track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                        try { track.play() } catch (e: Exception) { Log.e(TAG, "Error calling play(): ${e.message}") }
                                    }
                                }
                            }

                            writeToTrack(first.data)
                            val amplitude = AudioUtils.calculateRmsNormalized(first.data, first.data.size)
                            onPlaybackAmplitude(amplitude)
                        }
                        continue
                    }

                    // Currently playing: stream next chunks
                    val chunk = audioQueue.poll(20, TimeUnit.MILLISECONDS)
                    if (chunk != null && chunk.data.isNotEmpty()) {
                        if (chunk.epoch < currentTurnEpoch.get()) {
                            continue // Discard obsolete chunk
                        }
                        silenceCounter = 0
                        val amplitude = AudioUtils.calculateRmsNormalized(chunk.data, chunk.data.size)
                        onPlaybackAmplitude(amplitude)
                        writeToTrack(chunk.data)
                    } else {
                        silenceCounter++
                        val shouldFinish = (isTurnComplete.get() && silenceCounter >= 8) || (silenceCounter >= 35)
                        if (shouldFinish && isPlayingState.get()) {
                            isPlayingState.set(false)
                            onPlaybackStateChanged(false)
                            onPlaybackAmplitude(0f)
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in playback worker: ${e.message}")
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun enqueueAudio(pcmData: ByteArray, epoch: Long = currentTurnEpoch.get()) {
        if (epoch < currentTurnEpoch.get()) {
            return // Drop stale audio immediately
        }
        if (pcmData.size >= 16) {
            val validLen = pcmData.size and 1.inv()
            val aligned = if (validLen == pcmData.size) pcmData else pcmData.copyOf(validLen)
            audioQueue.offer(AudioEpochChunk(epoch, aligned))
        }
    }

    /**
     * Instantly flushes audio playback upon barge-in interruption.
     */
    fun flush() {
        currentTurnEpoch.incrementAndGet()
        audioQueue.clear()
        isTurnComplete.set(true)
        synchronized(trackLock) {
            try {
                audioTrack?.let {
                    if (it.state == AudioTrack.STATE_INITIALIZED) {
                        try { it.pause() } catch (_: Exception) {}
                        try { it.flush() } catch (_: Exception) {}
                        try { it.stop() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing AudioTrack: ${e.message}")
            }
        }
        if (isPlayingState.getAndSet(false)) {
            onPlaybackStateChanged(false)
            onPlaybackAmplitude(0f)
        }
    }

    fun isPlaying(): Boolean = isPlayingState.get() || !audioQueue.isEmpty()

    fun release() {
        isRunning.set(false)
        playbackThread?.interrupt()
        playbackThread = null
        audioQueue.clear()

        synchronized(trackLock) {
            try {
                audioTrack?.let {
                    if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        it.stop()
                    }
                    it.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
            } finally {
                audioTrack = null
            }
        }
    }

    private fun writeToTrack(chunk: ByteArray) {
        synchronized(trackLock) {
            var track = audioTrack
            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                initAudioTrack()
                track = audioTrack
            }
            track?.let { t ->
                if (t.state == AudioTrack.STATE_INITIALIZED && t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    try { t.play() } catch (_: Exception) {}
                }
                val written = t.write(chunk, 0, chunk.size)
                if (written < 0) {
                    Log.w(TAG, "AudioTrack write failed ($written). Reinitializing AudioTrack...")
                    try { t.release() } catch (_: Exception) {}
                    initAudioTrack()
                    audioTrack?.let { reloaded ->
                        try {
                            if (reloaded.state == AudioTrack.STATE_INITIALIZED) {
                                reloaded.play()
                                reloaded.write(chunk, 0, chunk.size)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error playing recreated track: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
