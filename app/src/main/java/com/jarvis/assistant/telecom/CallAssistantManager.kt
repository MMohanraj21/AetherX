package com.jarvis.assistant.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.automation.contacts.ContactResolver
import com.jarvis.assistant.background.JarvisConversationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages incoming call detection, contact caller announcement, and authorized voice-activated
 * answering/rejecting in English, Tamil, and Tanglish.
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

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val contactResolver = ContactResolver(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _incomingCallerName = MutableStateFlow<String?>(null)
    val incomingCallerName: StateFlow<String?> = _incomingCallerName.asStateFlow()

    private var isRinging = false
    private var activeRingingNumber: String? = null

    fun startListening() {
        if (telephonyManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallState(state, null)
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state, phoneNumber)
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
            Log.i(TAG, "CallAssistantManager listening for telephony events")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register call state listener: ${e.message}")
        }
    }

    private fun handleCallState(state: Int, rawNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isRinging = true
                activeRingingNumber = rawNumber
                scope.launch {
                    val caller = resolveCallerIdentity(rawNumber)
                    _incomingCallerName.value = caller
                    val service = JarvisConversationService.activeInstance
                    if (service != null) {
                        val announcement = "Sir, $caller is calling. Would you like to attend or reject?"
                        service.speakImmediateVoiceResponse(announcement) {
                            com.jarvis.assistant.island.OriginIslandController.startListening(context)
                        }
                    }
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                isRinging = false
                _incomingCallerName.value = null
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isRinging) {
                    isRinging = false
                    _incomingCallerName.value = null
                    Log.i(TAG, "Incoming call missed or disconnected by caller.")
                }
            }
        }
    }

    private fun resolveCallerIdentity(number: String?): String {
        if (number.isNullOrBlank()) return "Unknown Caller"
        return try {
            when (val res = contactResolver.resolveContact(number)) {
                is com.jarvis.assistant.automation.contacts.ContactResolutionResult.SingleMatch -> res.contact.name
                is com.jarvis.assistant.automation.contacts.ContactResolutionResult.MultipleMatches -> res.candidates.firstOrNull()?.name ?: number
                else -> number
            }
        } catch (_: Exception) {
            number
        }
    }

    /**
     * Interprets voice commands for answering or rejecting the incoming call.
     */
    fun handleCallVoiceCommand(command: String, onFeedback: (String) -> Unit): Boolean {
        if (!isRinging) return false
        val lower = command.lowercase().trim()

        val isAttend = lower.contains("attend") || lower.contains("answer") ||
                lower.contains("pick up") || lower.contains("edunga") ||
                lower.contains("attend pannunga") || lower.contains("call edunga")

        val isReject = lower.contains("reject") || lower.contains("decline") ||
                lower.contains("cut") || lower.contains("reject pannunga") ||
                lower.contains("vendaam") || lower.contains("call cut pannu")

        if (isAttend) {
            val answered = acceptCall()
            _incomingCallerName.value = null
            isRinging = false
            onFeedback(if (answered) "Attending the call." else "Permission required to answer calls directly.")
            return true
        }

        if (isReject) {
            val rejected = endCall()
            _incomingCallerName.value = null
            isRinging = false
            onFeedback(if (rejected) "Call declined." else "Could not decline the call automatically.")
            return true
        }

        return false
    }

    private fun acceptCall(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                return try {
                    telecomManager?.acceptRingingCall()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error accepting ringing call: ${e.message}")
                    false
                }
            }
        }
        return false
    }

    private fun endCall(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                return try {
                    telecomManager?.endCall()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error ending call via TelecomManager: ${e.message}")
                    false
                }
            }
        }
        return false
    }
}
