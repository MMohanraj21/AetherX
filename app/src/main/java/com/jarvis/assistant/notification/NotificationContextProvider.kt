package com.jarvis.assistant.notification

import android.content.Context
import com.jarvis.assistant.island.ZaraIslandNotificationService
import com.jarvis.assistant.util.PermissionHelper

/**
 * Generates accurate spoken responses for user questions about incoming messages and notifications.
 */
object NotificationContextProvider {

    fun formatVoiceResponse(context: Context, query: String): String {
        val lower = query.lowercase().trim()

        // 1. Check if Notification Listener is enabled
        val isServiceActive = ZaraIslandNotificationService.isConnected
        val hasPermission = PermissionHelper.hasNotificationPermission(context)

        if (!isServiceActive && !NotificationRepository.hasNotifications()) {
            return "Sir, I currently don't have access to your notification data. Please check whether ZARA's Notification Access is enabled in System Authorizations."
        }

        val all = NotificationRepository.getActiveList()
        if (all.isEmpty()) {
            return if (lower.contains("whatsapp") || lower.contains("வாட்ஸ்அப்")) {
                "Sir, you have no new WhatsApp notifications right now."
            } else {
                "Sir, you have no unread notifications right now."
            }
        }

        // 2. Query for specific app (e.g. WhatsApp, Gmail, Telegram)
        if (lower.contains("whatsapp") || lower.contains("வாட்ஸ்அப்")) {
            val waNotifs = NotificationRepository.getByPackage("whatsapp")
            return if (waNotifs.isNotEmpty()) {
                val latest = waNotifs.first()
                "Sir, you have ${waNotifs.size} WhatsApp message${if (waNotifs.size > 1) "s" else ""}. Latest from ${latest.sender}: '${latest.text}'."
            } else {
                "Sir, you have no new WhatsApp messages right now."
            }
        }

        // 3. Query for specific person (e.g. "Did Arun send anything?", "Arun message pannara?")
        val words = lower.split(" ", "?", ".", ",", "!")
            .filter { it !in listOf("did", "send", "anything", "message", "pannara", "from", "any", "new", "zara", "sir", "who", "what") }
        for (w in words) {
            if (w.length > 2) {
                val senderMatches = NotificationRepository.getBySender(w)
                if (senderMatches.isNotEmpty()) {
                    val m = senderMatches.first()
                    return "Sir, ${m.sender} sent you a message on ${m.appName}: '${m.text}' at ${m.formattedTime}."
                }
            }
        }

        // 4. Query for "Who messaged me?" / "Enakku yaar message pannirukkanga?"
        if (lower.contains("who messaged") || lower.contains("who sent") || lower.contains("yaar message") || lower.contains("yaar pannanga")) {
            val senders = all.map { it.sender }.distinct()
            return "Sir, you received messages from: ${senders.joinToString(", ")}."
        }

        // 5. Query for "Read my latest notification"
        if (lower.contains("latest") || lower.contains("last") || lower.contains("kadaisi")) {
            val latest = NotificationRepository.getLatest() ?: return "No new notifications found."
            return "Latest notification from ${latest.sender} on ${latest.appName}: '${latest.text}'."
        }

        // 6. General summary ("Any new messages?", "What notifications did I receive?")
        val count = all.size
        val top = all.take(3)
        val sb = StringBuilder("Sir, you have $count active notification${if (count > 1) "s" else ""}. ")
        for ((idx, item) in top.withIndex()) {
            sb.append("${item.sender} on ${item.appName} ('${item.text.take(45)}')")
            if (idx < top.size - 1) sb.append(", ")
        }
        sb.append(".")
        return sb.toString()
    }
}
