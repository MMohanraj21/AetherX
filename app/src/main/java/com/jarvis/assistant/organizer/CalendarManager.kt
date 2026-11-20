package com.jarvis.assistant.organizer

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log

object CalendarManager {
    private const val TAG = "CalendarManager"

    fun createEvent(
        context: Context,
        title: String,
        startTimeMs: Long,
        endTimeMs: Long = startTimeMs + 3600_000L,
        description: String = "",
        location: String = ""
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMs)
                if (description.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, description)
                if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Calendar insert event triggered: '\$title'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event: \${e.message}")
            false
        }
    }
}
