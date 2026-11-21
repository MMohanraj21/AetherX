package com.jarvis.assistant.notification

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rich model representing an intercepted notification in ZARA AI's unified notification system.
 */
data class ZaraNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val conversationId: String? = null,
    val replyAction: NotificationReplyManager.ReplyableAction? = null,
    val canReply: Boolean = replyAction != null,
    var isRead: Boolean = false,
    var isSuppressed: Boolean = false,
    val isSensitive: Boolean = false
) {
    val formattedTime: String
        get() = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

    val summaryForSpeech: String
        get() {
            return if (sender.isNotBlank() && sender != appName) {
                "$sender sent a message on $appName: '$text'"
            } else if (title.isNotBlank() && title != appName) {
                "$title on $appName: '$text'"
            } else {
                "Notification from $appName: '$text'"
            }
        }
}
