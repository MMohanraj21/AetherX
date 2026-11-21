package com.jarvis.assistant.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.ui.home.MainActivity

/**
 * Controller singleton for native Vivo Origin Island (SuperX Status Capsule) integration.
 *
 * Utilizes the verified Android 16 (API 36) Promoted Ongoing Notification mechanism:
 * - Posts to a dedicated silent IMPORTANCE_HIGH channel ("zara_origin_island").
 * - Promoted into Vivo's top punch-hole capsule via platform Notification.ProgressStyle()
 *   and setShortCriticalText().
 * - Attached directly to the active Microphone Foreground Service to ensure zero audio
 *   disruption, no duplicated notifications, and immediate promotion by SuperX.
 */
object OriginIslandController {
    private const val TAG = "OriginIslandController"
    const val CHANNEL_ID = "zara_origin_island"
    const val NOTIFICATION_ID = 41 // Exact Foreground Service notification ID

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

    fun startListening(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateState(ctx, "Listening")
    }

    fun startThinking(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateState(ctx, "Thinking")
    }

    fun startSpeaking(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateState(ctx, "Speaking")
    }

    fun dismiss(context: Context? = null) {
        val service = JarvisConversationService.activeInstance
        if (service != null) {
            // Revert foreground notification to standard quiet standby notification
            // without requestPromotedOngoing, causing Vivo SuperX to immediately collapse the capsule
            service.postStandardStandbyNotification()
        } else {
            val ctx = context ?: return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        }
        Log.i(TAG, "Origin Island capsule dismissed cleanly.")
    }

    fun updateState(context: Context, verb: String) {
        ensureChannel(context)
        val notification = buildPromotedNotification(context, verb)
        val service = JarvisConversationService.activeInstance
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (service != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    service.startForeground(NOTIFICATION_ID, notification, types)
                } catch (_: Exception) {
                    try {
                        service.startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
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
        Log.i(TAG, "Promoted Ongoing notification posted: '$verb' to Vivo Origin Island (SuperX)")
    }

    fun buildPromotedNotification(context: Context, verb: String): Notification {
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, JarvisConversationService::class.java).apply {
            action = JarvisConversationService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder.apply {
            setSmallIcon(R.drawable.ic_zara_logo)
            setColor(0xFFFFFFFF.toInt())
            setContentTitle("ZARA AI")
            setContentText(verb)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setContentIntent(contentIntent)
            addAction(stopAction)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setCategory(Notification.CATEGORY_STATUS)
            }
        }

        // Android 16 (API 36) Notification.ProgressStyle via reflection
        try {
            val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
            val progressStyleInstance = progressStyleClass.getConstructor().newInstance()

            try {
                val setProgressMethod = progressStyleClass.getMethod("setProgress", Int::class.javaPrimitiveType)
                setProgressMethod.invoke(progressStyleInstance, 0)
            } catch (_: Exception) {}

            try {
                val setStyledByProgressMethod = progressStyleClass.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType)
                setStyledByProgressMethod.invoke(progressStyleInstance, false)
            } catch (_: Exception) {}

            val setStyleMethod = Notification.Builder::class.java.getMethod(
                "setStyle",
                Class.forName("android.app.Notification\$Style")
            )
            setStyleMethod.invoke(builder, progressStyleInstance)
            Log.d(TAG, "Successfully attached Notification.ProgressStyle")
        } catch (e: Exception) {
            Log.d(TAG, "Notification.ProgressStyle reflection: ${e.message}")
        }

        // Android 16 (API 36) setShortCriticalText via reflection
        try {
            val setShortCriticalTextMethod = Notification.Builder::class.java.getMethod(
                "setShortCriticalText",
                CharSequence::class.java
            )
            setShortCriticalTextMethod.invoke(builder, verb)
            Log.d(TAG, "Successfully setShortCriticalText('$verb')")
        } catch (e: Exception) {
            Log.d(TAG, "setShortCriticalText reflection: ${e.message}")
        }

        // Promoted Ongoing Extras for SuperX / Android 16
        val extras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putString("android.shortCriticalText", verb)
            putBoolean("vivo.summary.ongoing", true)
            putString("vivo.summary.ongoing.text", verb)
            putString("vivo.summary.ongoing.title", "ZARA AI")
        }
        builder.addExtras(extras)

        return builder.build()
    }
}
