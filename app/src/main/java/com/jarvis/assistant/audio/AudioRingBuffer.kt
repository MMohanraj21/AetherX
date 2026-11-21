package com.jarvis.assistant.audio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe circular PCM audio buffer that retains the most recent [capacityMs]
 * milliseconds of 16kHz 16-bit mono audio (32 bytes per millisecond).
 *
 * Used for post-wake audio preservation so single-sentence utterances
 * like "Hey ZARA, Open WhatsApp" never discard the command spoken after the wake phrase.
 */
class AudioRingBuffer(
    private val capacityMs: Int = 3000,
    private val sampleRate: Int = 16000,
    private val bytesPerSample: Int = 2
) {
    // 16000 samples/sec * 2 bytes = 32000 bytes/sec = 32 bytes/ms
    private val bytesPerMs = (sampleRate * bytesPerSample) / 1000
    private val maxBytes = capacityMs * bytesPerMs
    private val buffer = ByteArray(maxBytes)

    private var head = 0
    private var totalBytesWritten = 0L
    private val lock = ReentrantLock()

    fun write(chunk: ByteArray, length: Int = chunk.size) {
        if (length <= 0) return
        lock.withLock {
            var bytesWritten = 0
            while (bytesWritten < length) {
                val availableToEnd = maxBytes - head
                val toWrite = minOf(length - bytesWritten, availableToEnd)
                System.arraycopy(chunk, bytesWritten, buffer, head, toWrite)
                head = (head + toWrite) % maxBytes
                bytesWritten += toWrite
            }
            totalBytesWritten += length
        }
    }

    /**
     * Extracts the most recent [durationMs] of recorded audio.
     */
    fun getRecentAudio(durationMs: Int): ByteArray {
        lock.withLock {
            val requestedBytes = minOf(durationMs * bytesPerMs, maxBytes)
            val availableBytes = minOf(totalBytesWritten, maxBytes.toLong()).toInt()
            val bytesToRead = minOf(requestedBytes, availableBytes)
            if (bytesToRead <= 0) return ByteArray(0)

            val result = ByteArray(bytesToRead)
            var startPos = (head - bytesToRead) % maxBytes
            if (startPos < 0) startPos += maxBytes

            val firstChunkLen = minOf(bytesToRead, maxBytes - startPos)
            System.arraycopy(buffer, startPos, result, 0, firstChunkLen)

            val remaining = bytesToRead - firstChunkLen
            if (remaining > 0) {
                System.arraycopy(buffer, 0, result, firstChunkLen, remaining)
            }
            return result
        }
    }

    /**
     * Slices the most recent [durationMs] of audio into fixed-size chunks (default 1280 bytes / 40ms)
     * suitable for immediate enqueueing into Gemini Live streaming buffers.
     */
    fun getRecentAudioChunks(durationMs: Int, chunkSize: Int = 1280): List<ByteArray> {
        val raw = getRecentAudio(durationMs)
        if (raw.isEmpty()) return emptyList()

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < raw.size) {
            val length = minOf(chunkSize, raw.size - offset)
            val chunk = ByteArray(length)
            System.arraycopy(raw, offset, chunk, 0, length)
            chunks.add(chunk)
            offset += length
        }
        return chunks
    }

    fun clear() {
        lock.withLock {
            head = 0
            totalBytesWritten = 0L
            buffer.fill(0)
        }
    }
}
