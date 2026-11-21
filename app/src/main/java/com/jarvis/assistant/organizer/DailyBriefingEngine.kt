package com.jarvis.assistant.organizer

import android.content.Context
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level orchestration engine for vocal morning/evening smart daily briefings.
 */
class DailyBriefingEngine(private val context: Context) {

    private val notesManager = NotesManager(context)
    private val reminderManager = ReminderManager(context)
    private val taskManager = TaskManager(context)
    private val briefingManager = DailyBriefingManager(context, notesManager, reminderManager, taskManager)

    suspend fun deliverDailyBriefing(language: String = "English", onSpoken: (String) -> Unit) = withContext(Dispatchers.Main) {
        val briefingText = briefingManager.getDailyBriefing(language)
        val conversationService = JarvisConversationService.activeInstance

        if (conversationService != null) {
            conversationService.speakImmediateVoiceResponse(briefingText) {
                com.jarvis.assistant.island.OriginIslandController.startListening(context)
            }
        }
        onSpoken(briefingText)
    }
}
