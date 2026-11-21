package com.jarvis.assistant.telecom

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.island.OriginIslandController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages incoming call interception, contact name resolution, vocal announcement via ZARA TTS,
 * and hands-free voice answering/rejecting in English, Tamil, and Tanglish.
 */
class CallAssistantManager(private val context: Context) {

    companion object {
        private const val TAG = "CallAssistantManager"

        @Volatile
        private var instance: CallAssistantManager? = null

        fun getInstance(context: Context): CallAssistantManager {
            return instance ?: synchronized(this) {
                instance ?: CallAssistantManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val callerIdManager = CallerIdentificationManager(context)
    private val callControlManager = CallControlManager(context)
    private val actionVerifier = CallActionVerifier(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _incomingCallerName = MutableStateFlow<String?>(null)
    val incomingCallerName: StateFlow<String?> = _incomingCallerName.asStateFlow()

    private var incomingCallMonitor: IncomingCallMonitor? = null
    private var isRinging = false
    private var lastRingingTimestamp = 0L

    fun startListening() {
        if (incomingCallMonitor == null) {
            incomingCallMonitor = IncomingCallMonitor(context) { state, number ->
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> onCallRinging(number)
                    TelephonyManager.CALL_STATE_OFFHOOK -> onCallOffhook()
                    TelephonyManager.CALL_STATE_IDLE -> onCallIdle()
                }
            }.also { it.start() }
            Log.i(TAG, "CallAssistantManager monitoring started")
        }
    }

    fun stopListening() {
        incomingCallMonitor?.stop()
        incomingCallMonitor = null
    }

    fun onCallRinging(rawNumber: String?) {
        val now = System.currentTimeMillis()
        if (isRinging && (now - lastRingingTimestamp < 3000L)) {
            // Deduplicate rapid repeat events
            return
        }
        isRinging = true
        lastRingingTimestamp = now

        scope.launch {
            val callerName = callerIdManager.resolveCallerName(rawNumber)
            _incomingCallerName.value = callerName
            Log.i(TAG, "Incoming call ringing from: $callerName")

            val service = JarvisConversationService.activeInstance
            if (service != null) {
                OriginIslandController.startSpeaking(context, "$callerName Calling")
                val announcement = "Sir, $callerName is calling. Would you like to attend or reject?"
                service.speakImmediateVoiceResponse(announcement) {
                    OriginIslandController.startListening(context)
                }
            }
        }
    }

    fun onCallOffhook() {
        if (isRinging) {
            Log.i(TAG, "Call picked up / answered.")
            isRinging = false
            _incomingCallerName.value = null
        }
    }

    fun onCallIdle() {
        if (isRinging) {
            Log.i(TAG, "Call ended or missed.")
            isRinging = false
            _incomingCallerName.value = null
        }
    }

    /**
     * Interprets voice commands for answering or declining the incoming call.
     * Supports English, Tamil, and Tanglish.
     */
    fun handleCallVoiceCommand(command: String, onFeedback: (String) -> Unit): Boolean {
        if (!isRinging && !actionVerifier.isCallStillRinging()) {
            return false
        }
        val lower = command.lowercase().trim()

        val isAttend = lower.contains("attend") || lower.contains("answer") ||
                lower.contains("pick up") || lower.contains("pickup") ||
                lower.contains("edunga") || lower.contains("attend pannunga") ||
                lower.contains("call edunga") || lower.contains("call attend")

        val isReject = lower.contains("reject") || lower.contains("decline") ||
                lower.contains("cut") || lower.contains("reject pannunga") ||
                lower.contains("vendaam") || lower.contains("call cut pannu") ||
                lower.contains("call reject") || lower.contains("cut pannunga")

        if (isAttend) {
            val answered = callControlManager.answerRingingCall()
            isRinging = false
            _incomingCallerName.value = null
            if (answered) {
                onFeedback("Attending the call.")
            } else {
                onFeedback("Call answering permission is needed. Please grant it in ZARA permissions.")
            }
            return true
        }

        if (isReject) {
            val declined = callControlManager.declineRingingCall()
            isRinging = false
            _incomingCallerName.value = null
            if (declined) {
                onFeedback("Call declined.")
            } else {
                onFeedback("Could not decline the call automatically.")
            }
            return true
        }

        return false
    }

    fun hasActiveRingingCall(): Boolean {
        return isRinging || actionVerifier.isCallStillRinging()
    }
}
