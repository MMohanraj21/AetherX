package com.aetherx.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.aetherx.security.PrivacyShield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder(
    private val sampleRate: Int = 16000,
    private val bufferSizeMultiplier: Int = 2
) {
    companion object {
        private const val TAG = "AetherAudioRecorder"
    }

    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    suspend fun startRecording(
        onAudioChunk: (ByteArray, Int) -> Unit,
        onRmsCalculated: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize * bufferSizeMultiplier

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return@withContext
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            Log.i(TAG, "Audio recording started at ${sampleRate}Hz")

            val buffer = ByteArray(bufferSize)
            while (isActive && isRecording.get()) {
                if (PrivacyShield.state.value.microphoneMuted) {
                    // Privacy shield active: zero out bytes before streaming
                    buffer.fill(0)
                    onRmsCalculated(0f)
                    kotlinx.coroutines.delay(50)
                    continue
                }

                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readBytes > 0) {
                    val rms = AudioEnergyMeter.calculateRms(buffer, readBytes)
                    onRmsCalculated(rms)
                    onAudioChunk(buffer.copyOf(readBytes), readBytes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording loop error: ${e.message}", e)
        } finally {
            stopRecording()
        }
    }

    fun stopRecording() {
        if (isRecording.compareAndSet(true, false)) {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                Log.i(TAG, "Audio recording stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
            }
        }
    }
}
