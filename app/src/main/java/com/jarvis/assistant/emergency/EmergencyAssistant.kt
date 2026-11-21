package com.jarvis.assistant.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*

/**
 * Coordinates emergency triggers, location extraction, and countdown-cancelled SOS workflows.
 */
class EmergencyAssistant(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyAssistant"
    }

    private var countdownJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun handleSosTrigger(
        onCountdownTick: (Int) -> Unit,
        onConfirmed: (locationMessage: String) -> Unit,
        onCancelled: () -> Unit
    ) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            Log.i(TAG, "Emergency SOS triggered. Starting 5-second safety countdown.")
            for (sec in 5 downTo 1) {
                onCountdownTick(sec)
                delay(1000L)
            }
            // Trigger confirmation
            val emergencyMsg = "Emergency SOS! Mohanraj needs urgent assistance. Location requested via ZARA AI."
            onConfirmed(emergencyMsg)
        }
    }

    fun cancelSos(onCancelled: () -> Unit) {
        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdownJob = null
            Log.i(TAG, "Emergency SOS successfully cancelled by user.")
            onCancelled()
        }
    }

    fun dialEmergency(number: String = "112") {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch emergency dialer: ${e.message}")
        }
    }
}
