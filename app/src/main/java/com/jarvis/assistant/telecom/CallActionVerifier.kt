package com.jarvis.assistant.telecom

import android.content.Context
import android.telephony.TelephonyManager

/**
 * Verifies whether telephony actions (answer/decline) actually took effect.
 */
class CallActionVerifier(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun isCallStillRinging(): Boolean {
        return telephonyManager?.callState == TelephonyManager.CALL_STATE_RINGING
    }

    fun isCallActive(): Boolean {
        return telephonyManager?.callState == TelephonyManager.CALL_STATE_OFFHOOK
    }

    fun isCallEnded(): Boolean {
        return telephonyManager?.callState == TelephonyManager.CALL_STATE_IDLE
    }
}
