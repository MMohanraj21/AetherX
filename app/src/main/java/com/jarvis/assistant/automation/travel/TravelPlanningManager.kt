package com.jarvis.assistant.automation.travel

import android.content.Context
import android.util.Log
import com.jarvis.assistant.automation.maps.GoogleMapsManager
import com.jarvis.assistant.organizer.CalendarManager
import com.jarvis.assistant.organizer.ReminderManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TravelPlan(
    val destination: String,
    val departureTimeMs: Long,
    val calendarConflict: String?,
    val spokenSummary: String,
    val navigationPrepared: Boolean
)

object TravelPlanningManager {
    private const val TAG = "TravelPlanningManager"

    fun planTrip(
        context: Context,
        destination: String,
        targetDateDesc: String = "tomorrow",
        departureHour: Int = 9,
        language: String = "English"
    ): TravelPlan {
        Log.i(TAG, "Planning autonomous trip to '$destination' for $targetDateDesc at $departureHour:00")

        val cal = Calendar.getInstance().apply {
            if (targetDateDesc.contains("tomorrow", ignoreCase = true) || targetDateDesc.contains("naalaikku", ignoreCase = true)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, departureHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val departureTimeMs = cal.timeInMillis
        val returnTimeMs = departureTimeMs + (4 * 3600_000L) // 4 hours window

        // Check Calendar conflicts
        val conflicts = CalendarManager.checkConflict(context, departureTimeMs, returnTimeMs)
        val conflictText = if (conflicts.isNotEmpty()) {
            conflicts.joinToString(", ") { "${it.title} (${it.formattedTimeRange})" }
        } else null

        val isTamil = language.contains("Tamil", ignoreCase = true) || language.contains("தமிழ்")
        val dateFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(departureTimeMs))

        val summary = if (isTamil) {
            val sb = StringBuilder()
            sb.append("$destination-க்கு உங்கள் பயணத் திட்டம் தயாராக உள்ளது ($dateFmt, $departureHour:00 AM).\n")
            if (conflictText != null) {
                sb.append("⚠️ கேலெண்டர் குறிப்பு: $conflictText உடன் நேரம் மோதுகிறது.\n")
            } else {
                sb.append("✅ உங்கள் கேலெண்டரில் அந்த நேரத்தில் வேறு எந்த நிகழ்வும் இல்லை.\n")
            }
            sb.append("Google Maps வழித்தடம் தயாராக உள்ளது. நீங்கள் புறப்படும் போது 'Start navigation' என்று சொன்னால் வழிகாட்டத் தொடங்குவேன்.")
            sb.toString()
        } else {
            val sb = StringBuilder()
            sb.append("I have organized your trip to $destination for $dateFmt at $departureHour:00 AM.\n")
            if (conflictText != null) {
                sb.append("⚠️ Note: You have an existing calendar schedule: $conflictText.\n")
            } else {
                sb.append("✅ Your schedule is completely free during that time.\n")
            }
            sb.append("Route details are prepared in Google Maps. Say 'Start navigation' whenever you are ready to drive.")
            sb.toString()
        }

        return TravelPlan(
            destination = destination,
            departureTimeMs = departureTimeMs,
            calendarConflict = conflictText,
            spokenSummary = summary,
            navigationPrepared = true
        )
    }

    /**
     * Executes the finalized navigation to the destination.
     */
    fun startTripNavigation(context: Context, destination: String): Boolean {
        return GoogleMapsManager(context).navigateTo(destination)
    }
}
