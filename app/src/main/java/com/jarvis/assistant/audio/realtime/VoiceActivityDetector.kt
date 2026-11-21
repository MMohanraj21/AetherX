package com.jarvis.assistant.audio.realtime

import com.jarvis.assistant.audio.AudioUtils
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real-time Voice Activity Detector (VAD) for ZARA AI v5.0.
 *
 * Employs adaptive noise floor tracking, root-mean-square (RMS) energy evaluation,
 * and hysteresis to detect genuine human voice onset while rejecting ambient acoustic noise.
 */
class VoiceActivityDetector(
    private val minThreshold: Float = 0.035f,
    private val noiseFloorMultiplier: Float = 2.4f,
    private val minVoicedFramesForSpeech: Int = 3,
    private val bargeInThresholdQuiet: Float = 0.20f,
    private val bargeInThresholdSpeaker: Float = 0.28f
) {
    private var ambientNoiseFloor = 0.025f
    private val consecutiveVoicedFrames = AtomicInteger(0)
    private var lastSpeechDetectedTime = 0L

    /**
     * Analyzes a 16kHz PCM audio chunk and determines whether it contains voiced speech.
     */
    fun processFrame(
        chunk: ByteArray,
        isSpeakerActive: Boolean,
        hasHeadset: Boolean
    ): FrameAnalysis {
        val now = System.currentTimeMillis()
        val amplitude = AudioUtils.calculateRmsNormalized(chunk, chunk.size)

        // Adaptive noise floor tracking when room is quiet
        if (amplitude < 0.10f) {
            ambientNoiseFloor = (ambientNoiseFloor * 0.97f + amplitude * 0.03f).coerceIn(0.015f, 0.09f)
        }

        // When assistant is speaking, use higher threshold to distinguish near-field user speech
        // from hardware acoustic echo cancellation residual floor.
        if (isSpeakerActive) {
            val bargeInThreshold = if (hasHeadset) bargeInThresholdQuiet else bargeInThresholdSpeaker
            val isVoiced = amplitude > bargeInThreshold
            val count = if (isVoiced) consecutiveVoicedFrames.incrementAndGet() else {
                consecutiveVoicedFrames.set(0)
                0
            }
            val isBargeInTriggered = count >= 2
            if (isBargeInTriggered) {
                lastSpeechDetectedTime = now
            }
            return FrameAnalysis(
                amplitude = amplitude,
                isVoiced = isVoiced,
                isSpeechStart = isBargeInTriggered,
                isBargeIn = isBargeInTriggered,
                consecutiveVoicedFrames = count
            )
        }

        // Standard user speaking mode: dynamic threshold based on ambient noise floor
        val dynamicThreshold = maxOf(minThreshold, ambientNoiseFloor * noiseFloorMultiplier)
        val isVoiced = amplitude > dynamicThreshold

        val count = if (isVoiced) {
            lastSpeechDetectedTime = now
            consecutiveVoicedFrames.incrementAndGet()
        } else {
            // Gradual decay so brief 20ms inter-word dip doesn't reset voiced counter immediately
            consecutiveVoicedFrames.updateAndGet { if (it > 0) it - 1 else 0 }
        }

        val isSpeechStart = count == minVoicedFramesForSpeech

        return FrameAnalysis(
            amplitude = amplitude,
            isVoiced = isVoiced,
            isSpeechStart = isSpeechStart,
            isBargeIn = false,
            consecutiveVoicedFrames = count
        )
    }

    fun reset() {
        consecutiveVoicedFrames.set(0)
    }

    fun getLastSpeechTime(): Long = lastSpeechDetectedTime

    data class FrameAnalysis(
        val amplitude: Float,
        val isVoiced: Boolean,
        val isSpeechStart: Boolean,
        val isBargeIn: Boolean,
        val consecutiveVoicedFrames: Int
    )
}
