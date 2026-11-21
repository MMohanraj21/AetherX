package com.jarvis.assistant.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * BroadcastReceiver listening for telephony state transitions (RINGING, OFFHOOK, IDLE)
 * to intercept incoming phone calls and notify CallAssistantManager.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IncomingCallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        Log.i(TAG, "Telephony state broadcast received: state=$stateStr, hasNumber=${!incomingNumber.isNullOrBlank()}")

        val manager = CallAssistantManager.getInstance(context)
        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                manager.onCallRinging(incomingNumber)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                manager.onCallOffhook()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                manager.onCallIdle()
            }
        }
    }
}
