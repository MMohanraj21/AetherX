package com.jarvis.assistant.organizer

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val description: String = "",
    val location: String = "",
    val isAllDay: Boolean = false
) {
    val formattedTimeRange: String
        get() {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "${sdf.format(Date(startMs))} - ${sdf.format(Date(endMs))}"
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            return sdf.format(Date(startMs))
        }
}

object CalendarManager {
    private const val TAG = "CalendarManager"

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries all calendar events occurring today (from 00:00:00 to 23:59:59).
     */
    fun getTodayEvents(context: Context): List<CalendarEvent> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        return getEventsForRange(context, startOfDay, endOfDay)
    }

    /**
     * Queries calendar events within a specific time range using CalendarContract.Instances.
     */
    fun getEventsForRange(context: Context, startMs: Long, endMs: Long): List<CalendarEvent> {
        if (!hasCalendarPermission(context)) {
            Log.w(TAG, "READ_CALENDAR permission not granted; cannot query calendar events.")
            return emptyList()
        }

        val events = mutableListOf<CalendarEvent>()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMs)
        ContentUris.appendId(builder, endMs)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = it.getColumnIndex(CalendarContract.Instances.END)
                val descIdx = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
                    val title = if (titleIdx >= 0) it.getString(titleIdx) ?: "Untitled Event" else "Untitled Event"
                    val begin = if (beginIdx >= 0) it.getLong(beginIdx) else startMs
                    val end = if (endIdx >= 0) it.getLong(endIdx) else begin + 3600000L
                    val desc = if (descIdx >= 0) it.getString(descIdx) ?: "" else ""
                    val loc = if (locIdx >= 0) it.getString(locIdx) ?: "" else ""
                    val isAllDay = if (allDayIdx >= 0) it.getInt(allDayIdx) == 1 else false

                    events.add(CalendarEvent(id, title, begin, end, desc, loc, isAllDay))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendar events: ${e.message}")
        } finally {
            cursor?.close()
        }

        return events
    }

    /**
     * Checks if there are any conflicting events during the requested start and end time.
     */
    fun checkConflict(context: Context, startMs: Long, endMs: Long): List<CalendarEvent> {
        val existing = getEventsForRange(context, startMs, endMs)
        // An event conflicts if it overlaps with [startMs, endMs]
        return existing.filter { event ->
            event.startMs < endMs && event.endMs > startMs
        }
    }

    /**
     * Inserts an event directly via ContentResolver if WRITE_CALENDAR is granted,
     * otherwise falls back to dispatching an Intent to the default Calendar application.
     */
    fun createEvent(
        context: Context,
        title: String,
        startTimeMs: Long,
        endTimeMs: Long = startTimeMs + 3600_000L,
        description: String = "",
        location: String = ""
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            val primaryCalendarId = getPrimaryCalendarId(context)
            if (primaryCalendarId != null) {
                try {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, startTimeMs)
                        put(CalendarContract.Events.DTEND, endTimeMs)
                        put(CalendarContract.Events.TITLE, title)
                        if (description.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, description)
                        if (location.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
                        put(CalendarContract.Events.CALENDAR_ID, primaryCalendarId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    }
                    val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    if (uri != null) {
                        Log.i(TAG, "Successfully inserted calendar event directly: '$title' at $uri")
                        return true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct calendar insert failed, using Intent fallback: ${e.message}")
                }
            }
        }

        // Intent Fallback
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
            Log.i(TAG, "Calendar insert event triggered via Intent: '$title'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch calendar intent: ${e.message}")
            false
        }
    }

    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.let {
                val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryIdx = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                var fallbackId: Long? = null

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val isPrimary = if (primaryIdx >= 0) it.getInt(primaryIdx) == 1 else false
                    if (isPrimary) return id
                    if (fallbackId == null) fallbackId = id
                }
                return fallbackId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query calendar ID: ${e.message}")
        } finally {
            cursor?.close()
        }
        return null
    }
}
