package com.jarvis.assistant.organizer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Calendar
import java.util.regex.Pattern

/**
 * High-performance parser and dispatcher for Zara AI Smart Utilities:
 * - Alarms
 * - Notes
 * - Reminders
 * - Timers
 * - Calendar Events
 * - Tasks
 * - Daily Briefing
 *
 * Supports Tamil, English, and Tanglish voice commands.
 */
class ZaraUtilityCommandParser(private val context: Context) {
    companion object {
        private const val TAG = "ZaraUtilityParser"
    }

    private val notesManager = NotesManager(context)
    private val reminderManager = ReminderManager(context)
    private val taskManager = TaskManager(context)
    private val dailyBriefingManager = DailyBriefingManager(context, notesManager, reminderManager, taskManager)

    /**
     * Executes an organizer action invoked via Gemini tool calling or natural command parser.
     */
    suspend fun executeTool(name: String, args: JSONObject): JSONObject {
        val result = JSONObject()
        try {
            when (name) {
                "manage_alarm" -> {
                    val action = args.optString("action", "create").lowercase()
                    val hour = args.optInt("hour", -1)
                    val minute = args.optInt("minute", 0)
                    val message = args.optString("message", "Zara Alarm")
                    val minutesFromNow = args.optInt("minutes_from_now", 0)

                    if (action == "show" || action == "view") {
                        val ok = AlarmManagerHelper.showAlarms(context)
                        result.put("success", ok)
                        result.put("message", if (ok) "Showing alarms" else "Cannot open alarms")
                    } else if (action == "cancel" || action == "delete" || action == "dismiss") {
                        val ok = AlarmManagerHelper.dismissAlarm(context, message)
                        result.put("success", ok)
                        result.put("message", "Alarm dismissed or opened alarm app to cancel.")
                    } else if (minutesFromNow > 0) {
                        val ok = AlarmManagerHelper.createRelativeAlarm(context, minutesFromNow, message)
                        result.put("success", ok)
                        result.put("message", "Alarm set for $minutesFromNow minutes from now.")
                    } else if (hour >= 0) {
                        val ok = AlarmManagerHelper.createAlarm(context, hour, minute, message)
                        val timeStr = String.format("%02d:%02d", hour, minute)
                        result.put("success", ok)
                        result.put("message", "Alarm set for $timeStr.")
                    } else {
                        result.put("success", false)
                        result.put("message", "Please specify the time for the alarm.")
                    }
                }

                "manage_note" -> {
                    val action = args.optString("action", "create").lowercase()
                    val title = args.optString("title", "Quick Note")
                    val content = args.optString("content", "")
                    val category = args.optString("category", "General")

                    when (action) {
                        "create", "add", "save" -> {
                            val note = notesManager.createNote(title, content, category)
                            result.put("success", true)
                            result.put("note_id", note.id)
                            result.put("message", "Note '${note.title}' saved successfully.")
                        }
                        "search" -> {
                            val query = args.optString("query", title)
                            val notes = notesManager.searchNotes(query)
                            result.put("success", true)
                            result.put("count", notes.size)
                            val summary = notes.joinToString("; ") { "${it.title}: ${it.content.take(40)}" }
                            result.put("message", if (notes.isNotEmpty()) "Found notes: $summary" else "No matching notes found.")
                        }
                        "get_latest", "read_last" -> {
                            val latest = notesManager.getLatestNote()
                            result.put("success", latest != null)
                            if (latest != null) {
                                result.put("message", "Latest note '${latest.title}': ${latest.content}")
                            } else {
                                result.put("message", "No notes found.")
                            }
                        }
                        "get_all", "list" -> {
                            val notes = notesManager.getAllNotes()
                            result.put("success", true)
                            result.put("count", notes.size)
                            val summary = notes.take(5).joinToString(", ") { it.title }
                            result.put("message", if (notes.isNotEmpty()) "You have ${notes.size} notes: $summary" else "You have no notes.")
                        }
                        "delete" -> {
                            val ok = notesManager.deleteNoteByTitle(title)
                            result.put("success", ok)
                            result.put("message", if (ok) "Deleted note '$title'" else "Note '$title' not found.")
                        }
                        else -> {
                            result.put("success", false)
                            result.put("message", "Unknown note action: $action")
                        }
                    }
                }

                "manage_reminder" -> {
                    val action = args.optString("action", "create").lowercase()
                    val title = args.optString("title", "Reminder")
                    val minutesFromNow = args.optInt("minutes_from_now", 0)
                    val hour = args.optInt("hour", -1)
                    val minute = args.optInt("minute", 0)

                    when (action) {
                        "create", "set", "add" -> {
                            val cal = Calendar.getInstance()
                            if (minutesFromNow > 0) {
                                cal.add(Calendar.MINUTE, minutesFromNow)
                            } else if (hour >= 0) {
                                cal.set(Calendar.HOUR_OF_DAY, hour)
                                cal.set(Calendar.MINUTE, minute)
                                cal.set(Calendar.SECOND, 0)
                                if (cal.before(Calendar.getInstance())) {
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                }
                            } else {
                                cal.add(Calendar.MINUTE, 30) // Default 30 mins
                            }
                            val reminder = reminderManager.createReminder(title, cal.timeInMillis)
                            result.put("success", true)
                            result.put("reminder_id", reminder.id)
                            result.put("message", "Reminder set for '$title'.")
                        }
                        "list", "get_all" -> {
                            val reminders = reminderManager.getPendingReminders()
                            result.put("success", true)
                            result.put("count", reminders.size)
                            val summary = reminders.joinToString(", ") { it.title }
                            result.put("message", if (reminders.isNotEmpty()) "Upcoming reminders: $summary" else "No pending reminders.")
                        }
                        "cancel", "delete" -> {
                            val ok = reminderManager.cancelReminder(title)
                            result.put("success", ok)
                            result.put("message", if (ok) "Cancelled reminder '$title'" else "Reminder '$title' not found.")
                        }
                    }
                }

                "manage_timer" -> {
                    val lengthSeconds = args.optInt("length_seconds", 60)
                    val message = args.optString("message", "Zara Timer")
                    val ok = TimerManager.startTimer(context, lengthSeconds, message)
                    result.put("success", ok)
                    val mins = lengthSeconds / 60
                    val secs = lengthSeconds % 60
                    val formatted = if (mins > 0 && secs > 0) "$mins minutes $secs seconds" else if (mins > 0) "$mins minutes" else "$secs seconds"
                    result.put("message", if (ok) "Timer started for $formatted." else "Failed to start timer.")
                }

                "manage_calendar" -> {
                    val action = args.optString("action", "create").lowercase()
                    when (action) {
                        "list", "get_today", "today" -> {
                            val events = CalendarManager.getTodayEvents(context)
                            result.put("success", true)
                            result.put("count", events.size)
                            if (events.isEmpty()) {
                                result.put("message", "Your calendar is clear today. No events scheduled.")
                            } else {
                                val summary = events.joinToString("; ") { "${it.title} at ${it.formattedTimeRange}" }
                                result.put("message", "Today's events: $summary")
                            }
                        }
                        "check_conflict", "conflict" -> {
                            val hour = args.optInt("hour", 10)
                            val minute = args.optInt("minute", 0)
                            val durationHours = args.optInt("duration_hours", 1)
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                            }
                            val startMs = cal.timeInMillis
                            val endMs = startMs + (durationHours * 3600_000L)
                            val conflicts = CalendarManager.checkConflict(context, startMs, endMs)
                            result.put("success", true)
                            result.put("has_conflict", conflicts.isNotEmpty())
                            if (conflicts.isEmpty()) {
                                result.put("message", "Time slot is completely free. No conflicts.")
                            } else {
                                val conflictSummary = conflicts.joinToString(", ") { "${it.title} (${it.formattedTimeRange})" }
                                result.put("message", "Conflict detected with: $conflictSummary")
                            }
                        }
                        else -> {
                            val title = args.optString("title", "Event")
                            val hour = args.optInt("hour", 10)
                            val minute = args.optInt("minute", 0)
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                                if (before(Calendar.getInstance())) {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                }
                            }
                            val ok = CalendarManager.createEvent(context, title, cal.timeInMillis)
                            result.put("success", ok)
                            result.put("message", if (ok) "Calendar event '$title' created." else "Could not open calendar.")
                        }
                    }
                }

                "manage_task" -> {
                    val action = args.optString("action", "add").lowercase()
                    val title = args.optString("title", "Task")
                    val priority = args.optString("priority", "MEDIUM")

                    when (action) {
                        "add", "create" -> {
                            val task = taskManager.addTask(title, priority = priority)
                            result.put("success", true)
                            result.put("task_id", task.id)
                            result.put("message", "Task '${task.title}' added to your list.")
                        }
                        "list", "get_all" -> {
                            val tasks = taskManager.getPendingTasks()
                            result.put("success", true)
                            result.put("count", tasks.size)
                            val summary = tasks.joinToString(", ") { it.title }
                            result.put("message", if (tasks.isNotEmpty()) "Pending tasks: $summary" else "No pending tasks!")
                        }
                        "complete", "mark_done" -> {
                            val ok = taskManager.completeTask(title)
                            result.put("success", ok)
                            result.put("message", if (ok) "Marked '$title' as completed." else "Task '$title' not found.")
                        }
                    }
                }

                "get_daily_briefing" -> {
                    val lang = args.optString("language", "English")
                    val briefing = dailyBriefingManager.getDailyBriefing(lang)
                    result.put("success", true)
                    result.put("briefing", briefing)
                    result.put("message", briefing)
                }

                else -> {
                    result.put("success", false)
                    result.put("message", "Unsupported tool: $name")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool '$name': ${e.message}", e)
            result.put("success", false)
            result.put("message", "Error: ${e.message}")
        }
        return result
    }
}
