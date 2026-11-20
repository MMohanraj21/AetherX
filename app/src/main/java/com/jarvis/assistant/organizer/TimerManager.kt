package com.jarvis.assistant.organizer

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log

object TimerManager {
    private const val TAG = "TimerManager"

    fun startTimer(context: Context, lengthSeconds: Int, message: String = "Zara Timer", skipUi: Boolean = true): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, lengthSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Timer started for ${lengthSeconds}s '$message'")
            true
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_SET_TIMER failed: ${e.message}")
            false
        }
    }
}
