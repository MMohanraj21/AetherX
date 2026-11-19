package com.aetherx.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.aetherx.security.AetherSecurityManager

class AetherApp : Application() {
    companion object {
        private const val TAG = "AetherApp"
        const val CHANNEL_ASSISTANT_SERVICE = "aether_assistant_channel"
        const val CHANNEL_ALERT = "aether_alert_channel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing AETHER X Quantum Core...")

        // Security & Integrity Audit
        val audit = AetherSecurityManager.audit(this)
        Log.i(TAG, "Integrity Status: ${audit.statusMessage}")

        // Notification Channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_ASSISTANT_SERVICE,
                "AETHER X Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active voice streaming and background presence"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT,
                "AETHER X Security & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical action confirmations and safety notices"
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }
}
