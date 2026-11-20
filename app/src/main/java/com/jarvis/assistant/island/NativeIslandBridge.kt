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
        if (!NativeIslandDetector.isNativeIslandSupported(context)) {
            // Strictly disabled on unsupported devices: no popups, no overlays
            return
        }

        try {
            val (title, defaultText) = when (state) {
                ConversationState.LISTENING -> "Zara is Listening" to "Listening to your voice..."
                ConversationState.THINKING -> "Zara is Thinking" to "Processing response..."
                ConversationState.SPEAKING -> "Zara is Speaking" to "Speaking answer..."
                ConversationState.IDLE -> "Zara AI" to "Ready"
            }

            val text = detailText ?: defaultText

            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(state != ConversationState.IDLE)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            if (progressPercent != null && progressPercent in 0..100) {
                builder.setProgress(100, progressPercent, false)
            } else if (state == ConversationState.THINKING) {
                builder.setProgress(100, 0, true)
            }

            // Provide Stop action when active
            if (state != ConversationState.IDLE) {
                val stopIntent = PendingIntent.getService(
                    context,
                    1,
                    Intent(context, JarvisConversationService::class.java).apply {
                        action = JarvisConversationService.ACTION_ENTER_STANDBY
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Pause", stopIntent)
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Native Island updated: state=$state, text=$text")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update Native Island notification: ${e.message}")
        }
    }

    /**
     * Clears the native Island state.
     */
    fun dismiss() {
        if (!NativeIslandDetector.isNativeIslandSupported(context)) return
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
