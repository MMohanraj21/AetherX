package com.jarvis.assistant.telecom

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService

/**
 * Executes call answer and rejection commands through official Android Telecom APIs
 * with Accessibility click fallback and media button fallback.
 */
class CallControlManager(private val context: Context) {

    companion object {
        private const val TAG = "CallControlManager"
    }

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val verifier = CallActionVerifier(context)

    fun answerRingingCall(): Boolean {
        if (!verifier.isCallStillRinging()) {
            Log.w(TAG, "Cannot answer: Call is no longer ringing.")
            return false
        }

        // Layer 1: Official Android TelecomManager API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (CallPermissionManager.hasAnswerCallsPermission(context)) {
                try {
                    telecomManager?.acceptRingingCall()
                    Log.i(TAG, "acceptRingingCall dispatched via TelecomManager")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "TelecomManager acceptRingingCall threw: ${e.message}")
                }
            }
        }

        // Layer 2: Accessibility In-Call UI Click Fallback
        if (tryAccessibilityClickAnswer()) {
            Log.i(TAG, "Answer executed via Accessibility UI Click")
            return true
        }

        // Layer 3: Headset hook key event emulation fallback
        return tryEmulateHeadsetHook()
    }

    fun declineRingingCall(): Boolean {
        if (!verifier.isCallStillRinging()) {
            Log.w(TAG, "Cannot decline: Call is no longer ringing.")
            return false
        }

        // Layer 1: Official Android TelecomManager API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (CallPermissionManager.hasAnswerCallsPermission(context)) {
                try {
                    val ended = telecomManager?.endCall() == true
                    Log.i(TAG, "endCall dispatched via TelecomManager: $ended")
                    if (ended) return true
                } catch (e: Exception) {
                    Log.w(TAG, "TelecomManager endCall threw: ${e.message}")
                }
            }
        }

        // Layer 2: Accessibility In-Call UI Click Fallback
        if (tryAccessibilityClickDecline()) {
            Log.i(TAG, "Decline executed via Accessibility UI Click")
            return true
        }

        return false
    }

    private fun tryAccessibilityClickAnswer(): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        val root = service.getActiveRootNode() ?: return false
        val answerLabels = listOf("Answer", "Accept", "Attend", "Pick up", "பதில்", "ஏற்க")
        for (label in answerLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                node.parent?.let { parent ->
                    if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun tryAccessibilityClickDecline(): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        val root = service.getActiveRootNode() ?: return false
        val declineLabels = listOf("Decline", "Reject", "Dismiss", "Cut", "நிராகரி", "மறு")
        for (label in declineLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                node.parent?.let { parent ->
                    if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun tryEmulateHeadsetHook(): Boolean {
        return try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            Log.i(TAG, "Dispatched KEYCODE_HEADSETHOOK media event as call answer fallback")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed headset hook emulation: ${e.message}")
            false
        }
    }
}
