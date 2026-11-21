package com.jarvis.assistant.notification

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvis.assistant.background.JarvisConversationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Intelligent coordinator for notification announcements, contextual reply suggestions,
 * and user-confirmed voice dispatch.
 */
object NotificationIntelligenceManager {

    private const val TAG = "NotifIntelligenceMgr"

    data class ActiveNotificationSession(
        val key: String,
        val packageName: String,
        val sender: String,
        val messageText: String,
        val replyAction: NotificationReplyManager.ReplyableAction?,
        val suggestedReply: String,
        val timestamp: Long
    )

    private val _pendingReplySession = MutableStateFlow<ActiveNotificationSession?>(null)
    val pendingReplySession: StateFlow<ActiveNotificationSession?> = _pendingReplySession.asStateFlow()

    private val announcedNotificationHashes = LinkedHashMap<String, Long>()

    fun onNotificationReceived(context: Context, sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val pkg = sbn.packageName.orEmpty()

        if (title.isBlank() && text.isBlank()) return

        // Privacy check
        if (!NotificationPrivacyFilter.isSafeToAnnounce(pkg, title, text)) {
            Log.d(TAG, "Notification excluded by PrivacyFilter: $pkg")
            return
        }

        // Deduplication
        val notifHash = "$pkg:$title:$text"
        val now = System.currentTimeMillis()
        val lastAnnounced = announcedNotificationHashes[notifHash] ?: 0L
        if (now - lastAnnounced < 60_000L) {
            return
        }
        announcedNotificationHashes[notifHash] = now
        trimOldHashes(now)

        val replyAction = NotificationReplyManager.findReplyAction(sbn)
        val suggestedReply = generateSuggestedReply(text)

        val session = ActiveNotificationSession(
            key = sbn.key,
            packageName = pkg,
            sender = title.ifBlank { "Someone" },
            messageText = text,
            replyAction = replyAction,
            suggestedReply = suggestedReply,
            timestamp = now
        )

        val conversationService = JarvisConversationService.activeInstance
        if (conversationService != null && JarvisConversationService.isSessionOn.value) {
            _pendingReplySession.value = session
            val spokenAnnouncement = "Sir, ${session.sender} sent you a message: ${session.messageText}. Would you like to reply?"
            conversationService.speakImmediateVoiceResponse(spokenAnnouncement) {
                com.jarvis.assistant.island.OriginIslandController.startListening(context)
            }
        }
    }

    private fun generateSuggestedReply(incomingText: String): String {
        val lower = incomingText.lowercase()
        return when {
            lower.contains("coming") || lower.contains("varriya") || lower.contains("vara") -> "Yes, I will come."
            lower.contains("where are you") || lower.contains("enga iruka") -> "I am on my way."
            lower.contains("free") || lower.contains("busy") -> "I am free, tell me."
            lower.contains("call") || lower.contains("pesu") -> "I will call you in a moment."
            lower.contains("thank") || lower.contains("nandri") -> "You're welcome!"
            else -> "Okay, got it."
        }
    }

    fun handleVoiceConfirmation(context: Context, userSpokenInput: String, onComplete: (String) -> Unit): Boolean {
        val session = _pendingReplySession.value ?: return false
        val input = userSpokenInput.lowercase().trim()

        if (input.contains("yes") || input.contains("aama") || input.contains("reply") || input.contains("sari")) {
            val prompt = "Shall I reply, '${session.suggestedReply}'?"
            onComplete(prompt)
            return true
        }

        if (input.contains("send") || input.contains("send it") || input.contains("anupu") || input.contains("confirm")) {
            val action = session.replyAction
            if (action != null) {
                val success = NotificationReplyManager.sendReply(context, action, session.suggestedReply)
                _pendingReplySession.value = null
                val resultText = if (success) "Your reply has been sent." else "Could not send the reply automatically."
                onComplete(resultText)
                return true
            } else {
                _pendingReplySession.value = null
                onComplete("Direct reply is not supported for this notification. Please open the app.")
                return true
            }
        }

        if (input.contains("no") || input.contains("cancel") || input.contains("vendaam")) {
            _pendingReplySession.value = null
            onComplete("Cancelled.")
            return true
        }

        return false
    }

    fun clearPendingSession() {
        _pendingReplySession.value = null
    }

    private fun trimOldHashes(now: Long) {
        val iterator = announcedNotificationHashes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > 120_000L) {
                iterator.remove()
            }
        }
    }
}
