package com.jarvis.assistant.audio.diagnostics

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic tracker and structured telemetry for the end-to-end voice pipeline:
 * MICROPHONE -> AUDIO CAPTURE -> PCM BUFFER -> GEMINI AUDIO INPUT ->
 * USER TURN DETECTION -> MODEL RESPONSE -> AUDIO PLAYBACK -> LISTENING
 */
object VoicePipelineDiagnostics {

    private const val TAG = "VoiceDiagnostics"

    enum class PipelineStage {
        MICROPHONE_INIT,
        WAKE_WORD_DETECTED,
        AUDIO_CAPTURE,
        PCM_BUFFER,
        SPEECH_RECOGNITION,
        TRANSCRIPT_GENERATED,
        GEMINI_AUDIO_INPUT,
        USER_TURN_DETECTION,
        MODEL_REQUEST_START,
        AI_REQUEST_SUBMITTED,
        GEMINI_CONNECTED,
        FIRST_RESPONSE_CHUNK,
        RESPONSE_RECEIVED,
        RESPONSE_COMPLETED,
        AUDIO_PLAYBACK_START,
        TTS_PLAYBACK,
        AUDIO_PLAYBACK_END,
        LISTENING_RECOVERED,
        FAILURE_REASON
    }

    data class DiagnosticLogEntry(
        val id: String = UUID.randomUUID().toString().take(6),
        val requestId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val stage: String,
        val message: String
    ) {
        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    private val activeSessionId = AtomicLong(System.currentTimeMillis())
    var currentTurnId = UUID.randomUUID().toString().take(8)
        private set
    private val turnStageTimestamps = ConcurrentHashMap<PipelineStage, Long>()

    // Observable Diagnostics StateFlows for UI
    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    private val _lastRequestTimestamp = MutableStateFlow<Long>(0L)
    val lastRequestTimestamp: StateFlow<Long> = _lastRequestTimestamp.asStateFlow()

    private val _lastResponseTimestamp = MutableStateFlow<Long>(0L)
    val lastResponseTimestamp: StateFlow<Long> = _lastResponseTimestamp.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow<Long>(0L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _lastError = MutableStateFlow<String>("None")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    private val _lastErrorCode = MutableStateFlow<String>("")
    val lastErrorCode: StateFlow<String> = _lastErrorCode.asStateFlow()

    fun startNewTurn(): String {
        currentTurnId = UUID.randomUUID().toString().take(8)
        turnStageTimestamps.clear()
        _lastRequestTimestamp.value = System.currentTimeMillis()
        markStage(PipelineStage.USER_TURN_DETECTION, "New user turn started [ID: $currentTurnId]")
        return currentTurnId
    }

    fun markStage(stage: PipelineStage, details: String = "") {
        val now = System.currentTimeMillis()
        turnStageTimestamps[stage] = now
        val elapsedFromStart = turnStageTimestamps[PipelineStage.USER_TURN_DETECTION]?.let { now - it } ?: 0L
        Log.i(TAG, "[$currentTurnId] [STAGE: $stage] (+${elapsedFromStart}ms) $details")
        addLog(currentTurnId, stage.name, "(+${elapsedFromStart}ms) $details")
    }

    fun recordFirstChunkLatency(): Long {
        val start = turnStageTimestamps[PipelineStage.MODEL_REQUEST_START] ?: return -1L
        val now = System.currentTimeMillis()
        val latency = now - start
        _lastResponseTimestamp.value = now
        _lastLatencyMs.value = latency
        markStage(PipelineStage.FIRST_RESPONSE_CHUNK, "First audio chunk latency: ${latency}ms")
        return latency
    }

    fun recordError(message: String, code: String = "") {
        _lastError.value = message
        _lastErrorCode.value = code
        Log.e(TAG, "[$currentTurnId] Diagnostic error logged: [$code] $message")
        addLog(currentTurnId, "ERROR", "[$code] $message")
    }

    fun logFailure(failedStage: PipelineStage, reason: String) {
        recordError(reason, failedStage.name)
        Log.e(TAG, "[$currentTurnId] [PIPELINE FAILURE at $failedStage]: $reason")
    }

    fun addLog(requestId: String, stage: String, message: String) {
        val entry = DiagnosticLogEntry(
            requestId = requestId,
            stage = stage,
            message = message
        )
        val current = _diagnosticLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 50) {
            _diagnosticLogs.value = current.take(50)
        } else {
            _diagnosticLogs.value = current
        }
    }

    fun clearLogs() {
        _diagnosticLogs.value = emptyList()
        _lastError.value = "None"
        _lastErrorCode.value = ""
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