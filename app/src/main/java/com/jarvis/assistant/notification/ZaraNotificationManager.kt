package com.jarvis.assistant.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.ui.home.MainActivity

/**
 * ZARA AI — Single Notification Management Engine (v5.5)
 *
 * Strict Architectural Guarantees:
 * 1. Exactly ONE stable notification ID: NOTIFICATION_ID = 41.
 * 2. Exactly ONE persistent notification channel: CHANNEL_ID = "zara_live_service".
 * 3. Silent & Non-disruptive: IMPORTANCE_LOW, no vibration, no sound, no banner spam.
 * 4. Stable Foreground Lifecycle: startForeground() is called ONCE when promoting the service.
 *    All updates use NotificationManager.notify() to NEVER restart or recreate the foreground service!
 * 5. Full Vivo Origin Island & Android 16 Promoted Ongoing integration:
 *    Embeds shortCriticalText, vivo.summary.ongoing, battery level, and transport/status category.
 * 6. Never triggers service shutdown or conversation state reset on notification updates.
 */
object ZaraNotificationManager {
    private const val TAG = "ZaraNotifManager"

    const val NOTIFICATION_ID = 41
    const val CHANNEL_ID = "zara_live_service"
    private const val CHANNEL_NAME = "Zara AI Live Service"

    const val ACTION_PLAY_PAUSE = "com.jarvis.assistant.island.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.jarvis.assistant.island.ACTION_NEXT"
    const val ACTION_PREVIOUS = "com.jarvis.assistant.island.ACTION_PREVIOUS"

    @Volatile
    private var isForegroundActive = false

    @Volatile
    private var lastState: ConversationState = ConversationState.IDLE

    @Volatile
    private var lastDetail: String? = null

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active voice assistant and Vivo Origin Island status capsule"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
                Log.i(TAG, "Created persistent notification channel: $CHANNEL_ID (IMPORTANCE_LOW, silent)")
            }
        }
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

    /**
     * Builds the authoritative promoted ongoing notification for ZARA AI.
     */
    fun buildNotification(
        context: Context,
        state: ConversationState = lastState,
        detail: String? = lastDetail,
        isStandby: Boolean = false,
        isMedia: Boolean = false,
        mediaTitle: String? = null,
        mediaArtist: String? = null,
        isPlaying: Boolean = false,
        isError: Boolean = false
    ): Notification {
        ensureChannel(context)

        lastState = state
        lastDetail = detail

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

        val (titleText, contentText, shortStatus) = when {
            isMedia -> {
                val track = mediaTitle ?: "Music"
                val artist = mediaArtist ?: "Playback"
                Triple(track, "$track - $artist", if (isPlaying) "Playing" else "Paused")
            }
            isStandby -> {
                Triple("ZARA AI", "Standby • Say \"Hey Zara\"", "Standby")
            }
            state == ConversationState.LISTENING -> {
                Triple("ZARA AI", detail ?: "Listening for speech...", "Listening")
            }
            state == ConversationState.THINKING -> {
                Triple("ZARA AI", detail ?: "Thinking...", "Thinking")
            }
            state == ConversationState.SPEAKING -> {
                Triple("ZARA AI", detail ?: "Speaking...", "Speaking")
            }
            isError -> {
                Triple("ZARA AI", detail ?: "Service Error", "Error")
            }
            else -> {
                Triple("ZARA AI", detail ?: "Ready", "Ready")
            }
        }

        builder.apply {
            setSmallIcon(R.drawable.ic_zara_logo)
            setColor(if (isError) 0xFFEF4444.toInt() else 0xFF00E5FF.toInt())
            setContentTitle(titleText)
            setContentText(contentText)
            if (batteryStr.isNotBlank()) {
                setSubText(batteryStr)
            }
            setOngoing(true)
            setOnlyAlertOnce(true)
            setShowWhen(false)
            setContentIntent(contentIntent)
            setVisibility(Notification.VISIBILITY_PUBLIC)
            setCategory(if (isMedia) Notification.CATEGORY_TRANSPORT else Notification.CATEGORY_STATUS)
        }

        if (isMedia) {
            // Media Controls
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

            if (state == ConversationState.THINKING) {
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
            putString("vivo.summary.ongoing.title", titleText)
            if (batteryStr.isNotBlank()) {
                putString("vivo.summary.ongoing.subtext", batteryStr)
            }
        }
        builder.addExtras(extras)

        return builder.build()
    }

    /**
     * Starts the Foreground Service notification ONCE.
     */
    fun startForegroundService(
        service: Service,
        isStandby: Boolean = false,
        additionalTypes: Int = 0
    ) {
        val notification = buildNotification(
            context = service,
            state = if (isStandby) ConversationState.IDLE else ConversationState.LISTENING,
            isStandby = isStandby
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val baseTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            val combinedTypes = baseTypes or additionalTypes
            try {
                service.startForeground(NOTIFICATION_ID, notification, combinedTypes)
                isForegroundActive = true
                Log.i(TAG, "startForeground initialized with types=$combinedTypes")
                return
            } catch (e: Exception) {
                Log.w(TAG, "startForeground with combined types failed: ${e.message}, falling back")
                try {
                    service.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    isForegroundActive = true
                    return
                } catch (e2: Exception) {
                    Log.w(TAG, "startForeground with mic type failed: ${e2.message}")
                }
            }
        }
        service.startForeground(NOTIFICATION_ID, notification)
        isForegroundActive = true
        Log.i(TAG, "startForeground initialized (legacy/fallback)")
    }

    /**
     * Updates the persistent notification without ever calling startForeground again.
     * Prevents service recreation, audio focus resets, and notification spam.
     */
    fun updateNotification(
        context: Context,
        state: ConversationState = lastState,
        detail: String? = lastDetail,
        isStandby: Boolean = false,
        isMedia: Boolean = false,
        mediaTitle: String? = null,
        mediaArtist: String? = null,
        isPlaying: Boolean = false,
        isError: Boolean = false
    ) {
        val notification = buildNotification(
            context = context,
            state = state,
            detail = detail,
            isStandby = isStandby,
            isMedia = isMedia,
            mediaTitle = mediaTitle,
            mediaArtist = mediaArtist,
            isPlaying = isPlaying,
            isError = isError
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification updated silently (notify ID $NOTIFICATION_ID, state=$state, isStandby=$isStandby)")
    }

    /**
     * Stops and cancels the notification cleanly when the service is intentionally stopped.
     */
    fun stopForegroundService(service: Service) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                service.stopForeground(true)
            }
            val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
            isForegroundActive = false
            Log.i(TAG, "stopForegroundService: clean removal of notification ID $NOTIFICATION_ID")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground notification: ${e.message}")
        }
    }

    fun isForegroundRunning(): Boolean = isForegroundActive
}
