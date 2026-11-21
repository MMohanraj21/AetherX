package com.jarvis.assistant.ui.diagnostics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.audio.WakeWordManager
import com.jarvis.assistant.audio.diagnostics.VoicePipelineDiagnostics
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.databinding.ActivityVoiceDiagnosticsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dedicated Voice Diagnostics Screen.
 * Displays real-time status of:
 * - Microphone permission & hardware state
 * - Vosk wake-word engine state
 * - Gemini Live WebSocket connection & protocol state
 * - Network connectivity
 * - End-to-end response latency
 * - Human-readable error messages
 * - Live structured request telemetry logs
 */
class VoiceDiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)

        setupUI()
        observeDiagnostics()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnClearLogs.setOnClickListener {
            VoicePipelineDiagnostics.clearLogs()
        }

        binding.btnTestRetry.setOnClickListener {
            VoicePipelineDiagnostics.addLog(
                VoicePipelineDiagnostics.currentTurnId,
                "DIAGNOSTIC_TEST",
                "User triggered manual pipeline test & reconnection retry."
            )
            val intent = Intent(this, JarvisConversationService::class.java).apply {
                action = JarvisConversationService.ACTION_START_CONVERSATION
            }
            ContextCompat.startForegroundService(this, intent)
        }

        checkSystemPermissions()
        checkNetworkStatus()
    }

    private fun checkSystemPermissions() {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMic) {
            binding.tvMicPermission.text = "GRANTED"
            binding.tvMicPermission.setTextColor(Color.rgb(0, 230, 118))
        } else {
            binding.tvMicPermission.text = "DENIED"
            binding.tvMicPermission.setTextColor(Color.rgb(255, 64, 129))
        }
    }

    private fun checkNetworkStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)

        val isConnected = caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )

        if (isConnected) {
            val typeStr = when {
                caps!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "CONNECTED • WI-FI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CONNECTED • CELLULAR"
                else -> "CONNECTED"
            }
            binding.tvNetworkStatus.text = typeStr
            binding.tvNetworkStatus.setTextColor(Color.rgb(0, 230, 118))
        } else {
            binding.tvNetworkStatus.text = "INTERNET UNAVAILABLE"
            binding.tvNetworkStatus.setTextColor(Color.rgb(255, 64, 129))
        }
    }

    private fun observeDiagnostics() {
        // Observe Mic / Conversation Session
        lifecycleScope.launch {
            JarvisConversationService.isSessionOn.collectLatest { sessionOn ->
                if (sessionOn) {
                    binding.tvMicStatus.text = "ACTIVE STREAMING • 16kHz"
                    binding.tvMicStatus.setTextColor(Color.rgb(0, 230, 118))
                } else {
                    binding.tvMicStatus.text = "STANDBY MONITORING"
                    binding.tvMicStatus.setTextColor(Color.rgb(0, 229, 255))
                }
            }
        }

        // Observe Vosk Wake Word State
        lifecycleScope.launch {
            WakeWordManager.currentState.collectLatest { cycleState ->
                binding.tvWakeWordStatus.text = cycleState.name
                binding.tvWakeWordStatus.setTextColor(Color.rgb(0, 229, 255))
            }
        }

        // Observe Gemini Connection Status
        lifecycleScope.launch {
            JarvisConversationService.connectionStatus.collectLatest { status ->
                binding.tvGeminiStatus.text = status.uppercase()
                when {
                    status.contains("LIVE", ignoreCase = true) -> {
                        binding.tvGeminiStatus.setTextColor(Color.rgb(0, 230, 118))
                    }
                    status.contains("ERROR", ignoreCase = true) -> {
                        binding.tvGeminiStatus.setTextColor(Color.rgb(255, 64, 129))
                    }
                    else -> {
                        binding.tvGeminiStatus.setTextColor(Color.rgb(0, 229, 255))
                    }
                }
            }
        }

        // Observe Latency
        lifecycleScope.launch {
            VoicePipelineDiagnostics.lastLatencyMs.collectLatest { ms ->
                binding.tvLatency.text = if (ms > 0) "${ms} ms" else "N/A"
            }
        }

        // Observe Last Error
        lifecycleScope.launch {
            VoicePipelineDiagnostics.lastError.collectLatest { err ->
                binding.tvLastError.text = err
                if (err != "None" && err.isNotBlank()) {
                    binding.tvLastError.setTextColor(Color.rgb(255, 64, 129))
                } else {
                    binding.tvLastError.setTextColor(Color.rgb(180, 200, 230))
                }
            }
        }

        // Observe Structured Logs
        lifecycleScope.launch {
            VoicePipelineDiagnostics.diagnosticLogs.collectLatest { logs ->
                if (logs.isEmpty()) {
                    binding.tvLogs.text = "Waiting for voice pipeline events..."
                } else {
                    val sb = StringBuilder()
                    for (entry in logs.take(20)) {
                        sb.append("[${entry.formattedTime}] [${entry.requestId}] [${entry.stage}]\n${entry.message}\n\n")
                    }
                    binding.tvLogs.text = sb.toString().trimEnd()
                }
            }
        }
    }
}