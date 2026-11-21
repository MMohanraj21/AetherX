package com.jarvis.assistant.island

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.ui.home.MainActivity

/**
 * Bridge for genuine native system Island / Live Activity integration (Vivo Origin Island, Aqua Dynamics, etc.).
 *
 * How it works:
 * On genuine supported hardware, the vendor OS compositor captures rich ongoing status notifications
 * marked with CATEGORY_STATUS / CATEGORY_PROGRESS and renders them directly inside the native
 * punch-hole pill.
 *
 * This bridge is ONLY active when [NativeIslandDetector.isNativeIslandSupported] is true.
 * If unsupported, this bridge is completely dormant.
 */
class NativeIslandBridge private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NativeIslandBridge"
        private const val CHANNEL_ID = "zara_native_island"
        private const val NOTIFICATION_ID = 202611

        @Volatile
        private var instance: NativeIslandBridge? = null

        fun getInstance(context: Context): NativeIslandBridge {
            return instance ?: synchronized(this) {
                instance ?: NativeIslandBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createIslandChannel()
    }

    private fun createIslandChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zara Native Island",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live system updates for native Dynamic Island and Origin Island."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Updates the native system Island with ZARA AI's current conversation state or task progress.
     */
    fun updateState(
        state: ConversationState,
        detailText: String? = null,
        progressPercent: Int? = null
    ) {
        when (state) {
            ConversationState.LISTENING -> OriginIslandController.startListening(context)
            ConversationState.THINKING -> OriginIslandController.startThinking(context)
            ConversationState.SPEAKING -> OriginIslandController.startSpeaking(context)
            ConversationState.IDLE -> OriginIslandController.dismiss(context)
        }
    }

    /**
     * Clears the native Island state.
     */
    fun dismiss() {
        OriginIslandController.dismiss(context)
    }
}
