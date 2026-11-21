package com.jarvis.assistant.audio.diagnostics

import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic tracker for the end-to-end voice pipeline:
 * MICROPHONE -> AUDIO CAPTURE -> PCM BUFFER -> GEMINI AUDIO INPUT ->
 * USER TURN DETECTION -> MODEL RESPONSE -> AUDIO PLAYBACK -> LISTENING
 */
object VoicePipelineDiagnostics {

    private const val TAG = "VoiceDiagnostics"

    enum class PipelineStage {
        MICROPHONE_INIT,
        AUDIO_CAPTURE,
        PCM_BUFFER,
        GEMINI_AUDIO_INPUT,
        USER_TURN_DETECTION,
        MODEL_REQUEST_START,
        FIRST_RESPONSE_CHUNK,
        AUDIO_PLAYBACK_START,
        AUDIO_PLAYBACK_END,
        LISTENING_RECOVERED
    }

    data class StageMetric(
        val stage: PipelineStage,
        val timestamp: Long = System.currentTimeMillis(),
        val details: String = ""
    )

    private val activeSessionId = AtomicLong(System.currentTimeMillis())
    private var currentTurnId = UUID.randomUUID().toString().take(8)
    private val turnStageTimestamps = ConcurrentHashMap<PipelineStage, Long>()

    fun startNewTurn(): String {
        currentTurnId = UUID.randomUUID().toString().take(8)
        turnStageTimestamps.clear()
        markStage(PipelineStage.USER_TURN_DETECTION, "New user turn started")
        return currentTurnId
    }

    fun markStage(stage: PipelineStage, details: String = "") {
        val now = System.currentTimeMillis()
        turnStageTimestamps[stage] = now
        val elapsedFromStart = turnStageTimestamps[PipelineStage.USER_TURN_DETECTION]?.let { now - it } ?: 0L
        Log.i(TAG, "[$currentTurnId] [STAGE: $stage] (+${elapsedFromStart}ms) $details")
    }

    fun recordFirstChunkLatency(): Long {
        val start = turnStageTimestamps[PipelineStage.MODEL_REQUEST_START] ?: return -1L
        val now = System.currentTimeMillis()
        val latency = now - start
        markStage(PipelineStage.FIRST_RESPONSE_CHUNK, "First audio chunk latency: ${latency}ms")
        return latency
    }

    fun logFailure(failedStage: PipelineStage, reason: String) {
        Log.e(TAG, "[$currentTurnId] [PIPELINE FAILURE at $failedStage]: $reason")
    }

    fun getTurnLatencySummary(): String {
        val sb = StringBuilder()
        sb.append("Turn [$currentTurnId] Stage Latencies: ")
        val start = turnStageTimestamps[PipelineStage.USER_TURN_DETECTION] ?: return "No active turn data"
        for ((stage, time) in turnStageTimestamps.entries.sortedBy { it.value }) {
            sb.append("${stage.name}=+${time - start}ms; ")
        }
        return sb.toString()
    }
}
