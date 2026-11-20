package com.jarvis.assistant.organizer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.home.MainActivity

class ZaraReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val CHANNEL_ID = "zara_reminders_channel"
        private const val CHANNEL_NAME = "Zara Reminders & Alarms"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra("EXTRA_TITLE") ?: "Reminder"
        val isAlarm = intent?.getBooleanExtra("EXTRA_IS_ALARM", false) ?: false
        val reminderId = intent?.getLongExtra("EXTRA_ID", System.currentTimeMillis()) ?: System.currentTimeMillis()

        Log.i("ZaraReminderReceiver", "Triggered reminder: '\$title' (isAlarm=\$isAlarm)")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications for Zara Alarms & Reminders"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION), null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (isAlarm) "✦ ZARA ALARM" else "✦ ZARA REMINDER")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, true)

        notificationManager.notify(reminderId.toInt(), builder.build())
    }
}
