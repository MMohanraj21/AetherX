package com.aetherx.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WakeWordDetector(
    private val targetWakeWord: String = "hey aether"
) {
    private val _wakeEvents = MutableSharedFlow<String>()
    val wakeEvents: SharedFlow<String> = _wakeEvents.asSharedFlow()

    private var consecutiveHotCount = 0

    suspend fun processFrame(energy: Float, transcribedHypothesis: String? = null) {
        // Mode 1: ASR hypothesis match
        if (transcribedHypothesis != null) {
            val normalized = transcribedHypothesis.lowercase().trim()
            if (normalized.contains(targetWakeWord) || normalized.contains("aether") || normalized.contains("jarvis")) {
                _wakeEvents.emit(targetWakeWord)
                return
            }
        }

        // Mode 2: Energy envelope trigger fallback
        if (energy > 0.45f) {
            consecutiveHotCount++
            if (consecutiveHotCount >= 4) { // Sustained vocal burst
                consecutiveHotCount = 0
                // Trigger wake detection
                _wakeEvents.emit("voice_detected")
            }
        } else {
            consecutiveHotCount = 0
        }
    }
}
