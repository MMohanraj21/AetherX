package com.jarvis.assistant.telecom

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Registers telephony listeners and broadcast receivers across Android API versions
 * to ensure reliable incoming call detection on Vivo OriginOS 6 (Android 16 API 36).
 */
class IncomingCallMonitor(private val context: Context, private val onStateChanged: (Int, String?) -> Unit) {

    companion object {
        private const val TAG = "IncomingCallMonitor"
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var dynamicReceiver: IncomingCallReceiver? = null
    private var isMonitoring = false

    fun start() {
        if (isMonitoring) return
        isMonitoring = true

        // 1. Register TelephonyCallback / PhoneStateListener
        registerTelephonyCallback()

        // 2. Register dynamic BroadcastReceiver for ACTION_PHONE_STATE
        registerDynamicReceiver()

        Log.i(TAG, "IncomingCallMonitor successfully started.")
    }

    fun stop() {
        if (!isMonitoring) return
        isMonitoring = false

        dynamicReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
            dynamicReceiver = null
        }

        Log.i(TAG, "IncomingCallMonitor stopped.")
    }

    private fun registerTelephonyCallback() {
        if (telephonyManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            onStateChanged(state, null)
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        onStateChanged(state, phoneNumber)
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TelephonyCallback registration failed: ${e.message}")
        }
    }

    private fun registerDynamicReceiver() {
        try {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            dynamicReceiver = IncomingCallReceiver()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(dynamicReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(dynamicReceiver, filter)
            }
            Log.d(TAG, "Dynamic IncomingCallReceiver registered successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic receiver registration failed: ${e.message}")
        }
    }
}
