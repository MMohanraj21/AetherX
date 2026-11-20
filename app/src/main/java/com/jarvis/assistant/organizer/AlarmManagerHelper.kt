package com.jarvis.assistant.organizer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import java.util.Calendar

object AlarmManagerHelper {
    private const val TAG = "AlarmManagerHelper"

    fun createAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        message: String = "Zara Alarm",
        days: ArrayList<Int>? = null,
        skipUi: Boolean = true
    ): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                if (!days.isNullOrEmpty()) {
                    putExtra(AlarmClock.EXTRA_DAYS, days)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Alarm set via AlarmClock intent: \$hour:\$minute '\$message'")
            true
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_SET_ALARM failed, falling back to AlarmManager: \${e.message}")
            createFallbackAlarm(context, hour, minute, message)
        }
    }

    fun createRelativeAlarm(context: Context, minutesFromNow: Int, message: String): Boolean {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutesFromNow)
        }
        return createAlarm(
            context = context,
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            message = message
        )
    }

    fun showAlarms(context: Context): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot show alarms: \${e.message}")
            false
        }
    }

    fun dismissAlarm(context: Context, search: String = ""): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                    if (search.isNotBlank()) {
                        putExtra(AlarmClock.EXTRA_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
                        putExtra(AlarmClock.EXTRA_MESSAGE, search)
                    }
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                showAlarms(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot dismiss alarm: \${e.message}")
            false
        }
    }

    private fun createFallbackAlarm(context: Context, hour: Int, minute: Int, message: String): Boolean {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val intent = Intent(context, ZaraReminderReceiver::class.java).apply {
                putExtra("EXTRA_TITLE", message)
                putExtra("EXTRA_IS_ALARM", true)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (hour * 60 + minute),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
            Log.i(TAG, "Fallback precision alarm scheduled at \${cal.time}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule fallback alarm: \${e.message}")
            false
        }
    }
}
