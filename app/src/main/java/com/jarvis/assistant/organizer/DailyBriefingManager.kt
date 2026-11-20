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
        val todayStr = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

        val isTamil = language.contains("Tamil", ignoreCase = true) || language.contains("தமிழ்")

        if (isTamil) {
            val sb = StringBuilder()
            sb.append("வணக்கம்! இன்றைய (\${todayStr}) திட்டம்:

")
            if (tasks.isNotEmpty()) {
                sb.append("📋 இன்றைய பணிகள் (\${tasks.size}):
")
                tasks.forEachIndexed { i, t -> sb.append("\${i + 1}. \${t.title} [\${t.priority}]
") }
            } else {
                sb.append("📋 இன்றைய பணிகள் எதுவும் நிலுவையில் இல்லை.
")
            }

            if (reminders.isNotEmpty()) {
                sb.append("
⏰ வரவிருக்கும் நினைவூட்டல்கள்:
")
                reminders.forEachIndexed { i, r ->
                    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(r.triggerTimeMs))
                    sb.append("\${i + 1}. \${r.title} - \${time}
")
                }
            }

            if (recentNotes.isNotEmpty()) {
                sb.append("
📝 சமீபத்திய குறிப்புகள்:
")
                recentNotes.forEach { n -> sb.append("• \${n.title}: \${n.content.take(50)}
") }
            }
            sb.toString()
        } else {
            val sb = StringBuilder()
            sb.append("Good day! Here is your daily plan for \${todayStr}:

")
            if (tasks.isNotEmpty()) {
                sb.append("📋 Pending Tasks (\${tasks.size}):
")
                tasks.forEachIndexed { i, t -> sb.append("\${i + 1}. \${t.title} (\${t.priority})
") }
            } else {
                sb.append("📋 No pending tasks for today.
")
            }

            if (reminders.isNotEmpty()) {
                sb.append("
⏰ Upcoming Reminders:
")
                reminders.forEachIndexed { i, r ->
                    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(r.triggerTimeMs))
                    sb.append("\${i + 1}. \${r.title} at \${time}
")
                }
            }

            if (recentNotes.isNotEmpty()) {
                sb.append("
📝 Recent Notes:
")
                recentNotes.forEach { n -> sb.append("• \${n.title}: \${n.content.take(60)}
") }
            }
            sb.toString()
        }
    }
}
