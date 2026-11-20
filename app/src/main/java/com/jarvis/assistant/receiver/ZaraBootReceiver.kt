package com.jarvis.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.util.PermissionHelper

/**
 * BroadcastReceiver triggered when the device boots or when the application is updated.
 * Automatically initializes Zara AI's offline wake-word service in standby mode
 * so that "Hey Zara" works immediately without requiring the user to open the app manually.
 */
class ZaraBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ZaraBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "onReceive: action=$action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            if (PermissionHelper.hasRecordAudioPermission(context) &&
                PermissionHelper.hasNotificationPermission(context)
            ) {
                try {
                    val serviceIntent = Intent(context, JarvisConversationService::class.java).apply {
                        this.action = JarvisConversationService.ACTION_ENTER_STANDBY
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                    Log.i(TAG, "Zara background wake-word service started successfully on boot/update.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start Zara background service: ${e.message}", e)
                }
            } else {
                Log.w(TAG, "Permissions not yet granted; skipping automatic service launch on boot.")
            }
        }
    }
}
