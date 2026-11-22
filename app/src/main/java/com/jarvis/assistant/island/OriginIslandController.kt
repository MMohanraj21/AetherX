package com.jarvis.assistant.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.media.UniversalMediaController
import com.jarvis.assistant.ui.home.MainActivity

/**
 * Controller singleton for native Vivo Origin Island (Vivo T5 Pro / OriginOS 6 / Android 16 API 36).
 *
 * Strict Architectural Rules:
 * - NO floating overlay windows (WindowManager.addView).
 * - NO fake island popups or duplicated bubbles.
 * - Bridges directly to the native Vivo Origin Island compositor via Android 16 Promoted
 *   Ongoing Notification on the active Microphone Foreground Service (Notification ID 41).
 * - Integrates Voice Assistant Status, Media Controls (MediaStyle), Smart Notifications,
 *   and Battery Quick Info.
 */
object OriginIslandController {
    private const val TAG = "OriginIslandController"
    const val CHANNEL_ID = "zara_origin_island"
    const val NOTIFICATION_ID = 41 // Exact Foreground Service notification ID

    const val ACTION_PLAY_PAUSE = "com.jarvis.assistant.island.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.jarvis.assistant.island.ACTION_NEXT"
    const val ACTION_PREVIOUS = "com.jarvis.assistant.island.ACTION_PREVIOUS"

    @Volatile
    private var isChannelCreated = false

    private fun ensureChannel(context: Context) {
        if (isChannelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zara Origin Island",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Native status capsule for Vivo Origin Island / Android 16 Promoted Ongoing"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            isChannelCreated = true
            Log.i(TAG, "Dedicated Origin Island notification channel initialized.")
        }
    }

    /**
     * Updates Origin Island for Voice Assistant lifecycle states.
     */
    fun updateVoiceState(context: Context, state: ConversationState, detail: String? = null) {
        val (title, text) = when (state) {
            ConversationState.LISTENING -> "Zara Listening..." to (detail ?: "Listening for speech")
            ConversationState.THINKING -> "Zara Thinking..." to (detail ?: "Processing query")
            ConversationState.SPEAKING -> "Zara Speaking..." to (detail ?: "Streaming response")
            ConversationState.IDLE -> "Zara Assistant" to (detail ?: "Ready • Tap or speak")
        }
        postPromotedNotification(context, title, text, state)
    }

    fun startListening(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateVoiceState(ctx, ConversationState.LISTENING)
    }

    fun startThinking(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateVoiceState(ctx, ConversationState.THINKING)
    }

    fun startSpeaking(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateVoiceState(ctx, ConversationState.SPEAKING)
    }

    /**
     * Updates Origin Island with active music metadata and playback controls.
     */
    fun updateMusicState(context: Context, trackTitle: String, artist: String?, isPlaying: Boolean) {
        postPromotedNotification(
            context = context,
            title = trackTitle,
            text = artist ?: "Music Playback",
            voiceState = null,
            isMedia = true,
            isPlaying = isPlaying
        )
    }

    /**
     * Posts a smart notification alert (task completed, connection status, error).
     */
    fun updateSmartNotification(context: Context, title: String, message: String, isError: Boolean = false) {
        postPromotedNotification(
            context = context,
            title = title,
            text = message,
            voiceState = null,
            isError = isError
        )
    }

    fun dismiss(context: Context? = null) {
        val service = JarvisConversationService.activeInstance
        if (service != null) {
            service.postStandardStandbyNotification()
        } else {
            val ctx = context ?: return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        }
        Log.i(TAG, "Origin Island capsule dismissed cleanly.")
    }

    private fun getBatterySummary(context: Context): String {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter) ?: return ""
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (level >= 0 && scale > 0) {
                val pct = level * 100 / scale
                if (isCharging) "$pct% ⚡" else "$pct%"
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun postPromotedNotification(
        context: Context,
        title: String,
        text: String,
        voiceState: ConversationState? = null,
        isMedia: Boolean = false,
        isPlaying: Boolean = false,
        isError: Boolean = false
    ) {
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val batteryStr = getBatterySummary(context)
        val shortStatus = when {
            isMedia -> if (isPlaying) "Playing" else "Paused"
            voiceState == ConversationState.LISTENING -> "Listening"
            voiceState == ConversationState.THINKING -> "Thinking"
            voiceState == ConversationState.SPEAKING -> "Speaking"
            isError -> "Error"
            else -> "Ready"
        }

        builder.apply {
            setSmallIcon(R.drawable.ic_zara_logo)
            setColor(if (isError) 0xFFEF4444.toInt() else 0xFF00E5FF.toInt())
            setContentTitle(title)
            setContentText(text)
            if (batteryStr.isNotBlank()) {
                setSubText(batteryStr)
            }
            setOngoing(true)
            setOnlyAlertOnce(true)
            setContentIntent(contentIntent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setCategory(if (isMedia) Notification.CATEGORY_TRANSPORT else Notification.CATEGORY_STATUS)
            }
        }

        // Attach action buttons
        if (isMedia) {
            // Media Controls: Previous, Play/Pause, Next
            val prevIntent = PendingIntent.getService(
                context, 201,
                Intent(context, JarvisConversationService::class.java).apply { action = ACTION_PREVIOUS },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIntent = PendingIntent.getService(
                context, 202,
                Intent(context, JarvisConversationService::class.java).apply { action = ACTION_PLAY_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIntent = PendingIntent.getService(
                context, 203,
                Intent(context, JarvisConversationService::class.java).apply { action = ACTION_NEXT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    prevIntent
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPauseIntent
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextIntent
                ).build()
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val mediaStyle = Notification.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                    builder.style = mediaStyle
                } catch (_: Exception) {}
            }
        } else {
            // Stop Action
            val stopIntent = Intent(context, JarvisConversationService::class.java).apply {
                action = JarvisConversationService.ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                context, 101, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stopAction = Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            ).build()
            builder.addAction(stopAction)

            if (voiceState == ConversationState.THINKING) {
                builder.setProgress(100, 50, true)
            }
        }

        // Android 16 (API 36) setShortCriticalText via reflection
        try {
            val setShortCriticalTextMethod = Notification.Builder::class.java.getMethod(
                "setShortCriticalText",
                CharSequence::class.java
            )
            setShortCriticalTextMethod.invoke(builder, shortStatus)
        } catch (_: Exception) {}

        // Promoted Ongoing Extras for Vivo Origin Island / SuperX / Android 16
        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putString("android.shortCriticalText", shortStatus)
            putBoolean("vivo.summary.ongoing", true)
            putString("vivo.summary.ongoing.text", shortStatus)
            putString("vivo.summary.ongoing.title", title)
            if (batteryStr.isNotBlank()) {
                putString("vivo.summary.ongoing.subtext", batteryStr)
            }
        }
        builder.addExtras(extras)

        val notification = builder.build()
        val service = JarvisConversationService.activeInstance
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (service != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    service.startForeground(NOTIFICATION_ID, notification, types)
                } catch (_: Exception) {
                    try {
                        service.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    } catch (_: Exception) {
                        notificationManager?.notify(NOTIFICATION_ID, notification)
                    }
                }
            } else {
                service.startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "Promoted Ongoing notification posted: '$shortStatus' to Vivo Origin Island")
    }
}
