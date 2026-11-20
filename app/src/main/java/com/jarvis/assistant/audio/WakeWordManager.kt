package com.jarvis.assistant.audio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State machine representing the voice cycle of ZARA AI.
 *
 * Sequence:
 * IDLE -> WAKE_WORD_DETECTED -> ACTIVATING -> LISTENING -> PROCESSING -> SPEAKING -> RESUMING_WAKE_WORD -> IDLE
 */
enum class VoiceCycleState {
    IDLE,
    WAKE_WORD_DETECTED,
    ACTIVATING,
    LISTENING,
    PROCESSING,
    SPEAKING,
    RESUMING_WAKE_WORD
}

/**
 * Single source of truth for ZARA AI Wake Word state and lifecycle management.
 * Guarantees that the Always Ready (Wake Word) setting remains persistently enabled
 * across Activity navigation, screen changes, and background operation.
 */
object WakeWordManager {
    private const val TAG = "WakeWordManager"

    private val _currentState = MutableStateFlow(VoiceCycleState.IDLE)
    val currentState: StateFlow<VoiceCycleState> = _currentState.asStateFlow()

    private val _isWakeWordActive = MutableStateFlow(false)
    val isWakeWordActive: StateFlow<Boolean> = _isWakeWordActive.asStateFlow()

    fun isEnabled(context: Context): Boolean {
        return (context.applicationContext as? JarvisApp)?.preferences?.isWakeWordEnabled ?: true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext as? JarvisApp ?: return
        val prev = app.preferences.isWakeWordEnabled
        if (prev != enabled) {
            app.preferences.isWakeWordEnabled = enabled
            if (enabled) {
                logEvent("USER_ENABLED_WAKE_WORD", "User turned Always Ready ON")
                startWakeWordService(context)
            } else {
                logEvent("WAKEWORD_PREFERENCE_CHANGED", "User turned Always Ready OFF")
                stopWakeWordService(context)
            }
        }
    }

    fun transitionTo(newState: VoiceCycleState, reason: String = "") {
        val oldState = _currentState.value
        if (oldState != newState) {
            _currentState.value = newState
            Log.i(TAG, "STATE_TRANSITION: $oldState -> $newState ($reason)")
        }
    }

    fun updateServiceWakeActive(active: Boolean) {
        _isWakeWordActive.value = active
        Log.d(TAG, "WAKEWORD_ACTIVE_CHANGED: active=$active")
    }

    fun startWakeWordService(context: Context) {
        try {
            val intent = Intent(context, JarvisConversationService::class.java).apply {
                action = JarvisConversationService.ACTION_ENTER_STANDBY
            }
            ContextCompat.startForegroundService(context, intent)
            logEvent("SERVICE_STARTED", "Dispatched ACTION_ENTER_STANDBY to JarvisConversationService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake word service: ${e.message}", e)
        }
    }

    fun stopWakeWordService(context: Context) {
        try {
            val intent = Intent(context, JarvisConversationService::class.java).apply {
                action = JarvisConversationService.ACTION_STOP_ALL
            }
            ContextCompat.startForegroundService(context, intent)
            logEvent("SERVICE_STOPPED", "Dispatched ACTION_STOP_ALL to JarvisConversationService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop wake word service: ${e.message}", e)
        }
    }

    fun logEvent(event: String, details: String = "") {
        Log.i(TAG, "[$event] $details")
    }
}
