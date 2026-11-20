package com.jarvis.assistant.organizer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jarvis.assistant.organizer.db.ReminderEntity
import com.jarvis.assistant.organizer.db.ZaraOrganizerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderManager(private val context: Context) {
    private val reminderDao = ZaraOrganizerDatabase.getInstance(context).reminderDao()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    suspend fun createReminder(
        title: String,
        triggerTimeMs: Long,
        repeatIntervalMs: Long = 0L,
        category: String = "General"
    ): ReminderEntity = withContext(Dispatchers.IO) {
        val entity = ReminderEntity(
            title = title,
            triggerTimeMs = triggerTimeMs,
            repeatIntervalMs = repeatIntervalMs,
            category = category
        )
        val id = reminderDao.insertReminder(entity)
        val saved = entity.copy(id = id)
        scheduleSystemAlarm(saved)
        saved
    }

    suspend fun getPendingReminders(): List<ReminderEntity> = withContext(Dispatchers.IO) {
        reminderDao.getPendingReminders()
    }

    suspend fun cancelReminder(title: String): Boolean = withContext(Dispatchers.IO) {
        val count = reminderDao.deleteReminderByTitle(title)
        count > 0
    }

    private fun scheduleSystemAlarm(reminder: ReminderEntity) {
        try {
            if (alarmManager == null) return
            val intent = Intent(context, ZaraReminderReceiver::class.java).apply {
                putExtra("EXTRA_ID", reminder.id)
                putExtra("EXTRA_TITLE", reminder.title)
                putExtra("EXTRA_IS_ALARM", false)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerTimeMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.triggerTimeMs, pendingIntent)
            }
            Log.i("ReminderManager", "Scheduled reminder ID \${reminder.id} at \${reminder.triggerTimeMs}")
        } catch (e: Exception) {
            Log.e("ReminderManager", "Failed to schedule reminder alarm: \${e.message}")
        }
    }
}
