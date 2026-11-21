package com.jarvis.assistant.organizer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyBriefingManager(
    private val context: Context,
    private val notesManager: NotesManager,
    private val reminderManager: ReminderManager,
    private val taskManager: TaskManager
) {
    suspend fun getDailyBriefing(language: String): String = withContext(Dispatchers.IO) {
        val tasks = taskManager.getPendingTasks()
        val reminders = reminderManager.getPendingReminders()
        val recentNotes = notesManager.getAllNotes().take(3)
        val calendarEvents = CalendarManager.getTodayEvents(context)
        val todayStr = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

        val isTamil = language.contains("Tamil", ignoreCase = true) || language.contains("தமிழ்")

        if (isTamil) {
            val sb = StringBuilder()
            sb.append("வணக்கம்! இன்றைய ($todayStr) திட்டம்:\n\n")

            if (calendarEvents.isNotEmpty()) {
                sb.append("📅 இன்றைய கேலெண்டர் நிகழ்வுகள் (${calendarEvents.size}):\n")
                calendarEvents.forEachIndexed { i, ev ->
                    sb.append("${i + 1}. ${ev.title} (${ev.formattedTimeRange})\n")
                }
                sb.append("\n")
            }

            if (tasks.isNotEmpty()) {
                sb.append("📋 இன்றைய பணிகள் (${tasks.size}):\n")
                tasks.forEachIndexed { i, t -> sb.append("${i + 1}. ${t.title} [${t.priority}]\n") }
            } else {
                sb.append("📋 இன்றைய பணிகள் எதுவும் நிலுவையில் இல்லை.\n")
            }

            if (reminders.isNotEmpty()) {
                sb.append("\n⏰ வரவிருக்கும் நினைவூட்டல்கள்:\n")
                reminders.forEachIndexed { i, r ->
                    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(r.triggerTimeMs))
                    sb.append("${i + 1}. ${r.title} - $time\n")
                }
            }

            if (recentNotes.isNotEmpty()) {
                sb.append("\n📝 சமீபத்திய குறிப்புகள்:\n")
                recentNotes.forEach { n -> sb.append("• ${n.title}: ${n.content.take(50)}\n") }
            }
            sb.toString()
        } else {
            val sb = StringBuilder()
            sb.append("Good day! Here is your daily plan for $todayStr:\n\n")

            if (calendarEvents.isNotEmpty()) {
                sb.append("📅 Today's Calendar Schedule (${calendarEvents.size}):\n")
                calendarEvents.forEachIndexed { i, ev ->
                    sb.append("${i + 1}. ${ev.title} (${ev.formattedTimeRange})\n")
                }
                sb.append("\n")
            }

            if (tasks.isNotEmpty()) {
                sb.append("📋 Pending Tasks (${tasks.size}):\n")
                tasks.forEachIndexed { i, t -> sb.append("${i + 1}. ${t.title} (${t.priority})\n") }
            } else {
                sb.append("📋 No pending tasks for today.\n")
            }

            if (reminders.isNotEmpty()) {
                sb.append("\n⏰ Upcoming Reminders:\n")
                reminders.forEachIndexed { i, r ->
                    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(r.triggerTimeMs))
                    sb.append("${i + 1}. ${r.title} at $time\n")
                }
            }

            if (recentNotes.isNotEmpty()) {
                sb.append("\n📝 Recent Notes:\n")
                recentNotes.forEach { n -> sb.append("• ${n.title}: ${n.content.take(60)}\n") }
            }
            sb.toString()
        }
    }
}
