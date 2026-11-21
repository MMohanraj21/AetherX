package com.jarvis.assistant.security

import java.util.UUID

/**
 * Encapsulates an unconfirmed message draft strictly bound to exact recipient and content.
 */
data class PendingMessageDraft(
    val actionId: String = UUID.randomUUID().toString(),
    val appType: String, // "WHATSAPP", "NOTIFICATION_REPLY", "SMS"
    val recipient: String,
    val phoneNumber: String? = null,
    val exactContent: String,
    val authToken: String = "AUTH_" + UUID.randomUUID().toString().take(8),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 60_000L, // 60-second validity
    var isAuthorized: Boolean = false,
    val replyAction: Any? = null
)
