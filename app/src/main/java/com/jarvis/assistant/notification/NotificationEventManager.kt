package com.jarvis.assistant.notification

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.island.OriginIslandController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Event manager connecting incoming notifications to ZARA's voice engine and Origin Island.
 */
object NotificationEventManager {

    private const val TAG = "NotifEventManager"

    data class PendingReplySession(
        val item: ZaraNotificationItem,
        var state: ReplyState = ReplyState.AWAITING_WANT_REPLY,
        var proposedReplyText: String = ""
    )

    enum class ReplyState {
        AWAITING_WANT_REPLY,   // "Would you like to reply?" -> "Yes"
        AWAITING_MESSAGE_BODY, // "You can say your reply" -> "Tell him I will come"
        AWAITING_FINAL_CONFIRM // "Should I send: I will come?" -> "Yes, send it"
    }

    private val _pendingReply = MutableStateFlow<PendingReplySession?>(null)
    val pendingReply: StateFlow<PendingReplySession?> = _pendingReply.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val announcedNotificationHashes = LinkedHashMap<String, Long>()

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        val item = NotificationRepository.addOrUpdate(context, sbn, announce = true) ?: return

        if (item.isSensitive || item.isSuppressed) {
            Log.d(TAG, "Notification suppressed by PrivacyFilter: ${item.appName}")
            return
        }

        // Deduplication window (60 seconds)
        val notifHash = "${item.packageName}:${item.sender}:${item.text}"
        val now = System.currentTimeMillis()
        val lastTime = announcedNotificationHashes[notifHash] ?: 0L
        if (now - lastTime < 60_000L) {
            return
        }
        announcedNotificationHashes[notifHash] = now
        trimOldHashes(now)

        val service = JarvisConversationService.activeInstance ?: return

        // Do not interrupt active phone call
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        if (telephonyManager?.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE) {
            Log.i(TAG, "Cellular call active, queuing notification silently.")
            return
        }

        scope.launch {
            _pendingReply.value = PendingReplySession(item)

            val announcement = if (item.canReply) {
                "Sir, ${item.sender} sent you a message: '${item.text}'. Would you like to reply?"
            } else {
                "Sir, notification from ${item.appName}: ${item.sender} says '${item.text}'."
            }

            Log.i(TAG, "Announcing notification via ZARA voice: $announcement")
            service.speakImmediateVoiceResponse(announcement) {
                OriginIslandController.startListening(context)
            }
        }
    }

    fun onNotificationRemoved(key: String) {
        val removed = NotificationRepository.remove(key)
        if (_pendingReply.value?.item?.key == key) {
            _pendingReply.value = null
        }
    }

    /**
     * Interactive multi-turn voice reply state machine.
     * Returns true if handled by the notification reply workflow.
     */
    fun handleReplyTurn(context: Context, userSpokenInput: String, onFeedback: (String) -> Unit): Boolean {
        val session = _pendingReply.value ?: return false
        val input = userSpokenInput.trim().lowercase()

        // Cancellation
        if (input.contains("cancel") || input.contains("no") || input.contains("vendaam") || input.contains("don't")) {
            _pendingReply.value = null
            onFeedback("Reply cancelled.")
            return true
        }

        when (session.state) {
            ReplyState.AWAITING_WANT_REPLY -> {
                if (input.contains("yes") || input.contains("aama") || input.contains("reply") ||
                    input.contains("sari") || input.contains("sure") || input.contains("kandipa")) {
                    session.state = ReplyState.AWAITING_MESSAGE_BODY
                    onFeedback("You can say your reply.")
                    return true
                }
            }

            ReplyState.AWAITING_MESSAGE_BODY -> {
                // The user spoke the actual reply text
                var cleanReply = userSpokenInput.trim()
                val prefixRegex = Regex("^(tell him|tell her|say|reply|anupu)\\s+", RegexOption.IGNORE_CASE)
                cleanReply = cleanReply.replace(prefixRegex, "").trim()
                if (cleanReply.isNotBlank()) {
                    session.proposedReplyText = cleanReply
                    session.state = ReplyState.AWAITING_FINAL_CONFIRM
                    onFeedback("Should I send: $cleanReply?")
                    return true
                }
            }

            ReplyState.AWAITING_FINAL_CONFIRM -> {
                if (input.contains("yes") || input.contains("send") || input.contains("send it") ||
                    input.contains("anupu") || input.contains("confirm") || input.contains("sari")) {
                    val action = session.item.replyAction
                    if (action != null) {
                        val ok = NotificationReplyManager.sendReply(context, action, session.proposedReplyText)
                        _pendingReply.value = null
                        onFeedback(if (ok) "Your reply was submitted." else "Could not submit reply.")
                        return true
                    } else {
                        _pendingReply.value = null
                        onFeedback("Direct reply is not supported for this notification. Please open ${session.item.appName}.")
                        return true
                    }
                }
            }
        }

        return false
    }

    fun hasPendingReply(): Boolean = _pendingReply.value != null

    fun clearPending() {
        _pendingReply.value = null
    }

    private fun trimOldHashes(now: Long) {
        val it = announcedNotificationHashes.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > 120_000L) {
                it.remove()
            }
        }
    }
}
