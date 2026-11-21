package com.jarvis.assistant.orchestrator

import com.jarvis.assistant.offline.OfflineCapabilityRegistry
import com.jarvis.assistant.offline.OfflineIntentClassifier

/**
 * High-speed intent router classifying user queries into destination domains:
 * TELECOM, NOTIFICATION_REPLY, NOTIFICATION_QUERY, OFFLINE_DEVICE, MEDIA, AUTOMATION_MODE, EMERGENCY, or GENERATIVE_AI.
 */
object IntentRouter {

    enum class TargetDomain {
        TELECOM_CALL,
        NOTIFICATION_REPLY,
        NOTIFICATION_QUERY,
        OFFLINE_DEVICE,
        MEDIA_CONTROL,
        AUTOMATION_ROUTINE,
        EMERGENCY_SOS,
        GENERATIVE_AI
    }

    data class RoutedIntent(
        val domain: TargetDomain,
        val originalQuery: String,
        val parameter: String? = null
    )

    fun route(query: String, hasActiveCall: Boolean, hasActiveNotifSession: Boolean): RoutedIntent {
        val lower = query.lowercase().trim()

        // 1. Emergency
        if (lower == "sos" || lower.contains("help me") || lower.contains("emergency") || lower.contains("aabathu")) {
            return RoutedIntent(TargetDomain.EMERGENCY_SOS, query)
        }

        // 2. Incoming Call priority
        if (hasActiveCall) {
            val isCallCommand = lower.contains("attend") || lower.contains("answer") ||
                    lower.contains("reject") || lower.contains("decline") || lower.contains("edunga") ||
                    lower.contains("cut") || lower.contains("pick up")
            if (isCallCommand) {
                return RoutedIntent(TargetDomain.TELECOM_CALL, query)
            }
        }

        // 3. Notification Reply priority
        if (hasActiveNotifSession) {
            val isReplyCommand = lower.contains("yes") || lower.contains("send") ||
                    lower.contains("reply") || lower.contains("no") || lower.contains("cancel") ||
                    lower.contains("aama") || lower.contains("sari") || lower.contains("vendaam")
            if (isReplyCommand) {
                return RoutedIntent(TargetDomain.NOTIFICATION_REPLY, query)
            }
        }

        // 4. Notification Query queries ("What notifications did I receive?", "Any new messages?", "Who messaged me?", etc.)
        if (isNotificationQueryIntent(lower)) {
            return RoutedIntent(TargetDomain.NOTIFICATION_QUERY, query)
        }

        // 5. Media commands
        if (lower.contains("pause song") || lower.contains("pause music") ||
            lower.contains("play song") || lower.contains("next song") ||
            lower.contains("previous song") || lower.contains("current song")) {
            return RoutedIntent(TargetDomain.MEDIA_CONTROL, query)
        }

        // 6. Automation Routines
        if (lower.contains("morning mode") || lower.contains("night mode") ||
            lower.contains("work mode") || lower.contains("study mode") || lower.contains("driving mode")) {
            return RoutedIntent(TargetDomain.AUTOMATION_ROUTINE, query)
        }

        // 7. Offline device actions
        val offline = OfflineIntentClassifier.classify(query)
        if (offline != null) {
            return RoutedIntent(TargetDomain.OFFLINE_DEVICE, query, offline.target)
        }

        // 8. General multi-turn generative conversation & tool execution
        return RoutedIntent(TargetDomain.GENERATIVE_AI, query)
    }

    private fun isNotificationQueryIntent(lower: String): Boolean {
        // Direct notification queries
        if (lower.contains("notification") || lower.contains("நோட்டிபிகேஷன்")) {
            return true
        }
        if (lower.contains("any message") || lower.contains("any new message") ||
            lower.contains("new message") || lower.contains("unread message") ||
            lower.contains("who messaged") || lower.contains("who sent") ||
            lower.contains("read my message") || lower.contains("check message") ||
            lower.contains("what did they send") || lower.contains("what message")) {
            return true
        }
        // Tamil / Tanglish notification queries
        if (lower.contains("message vandhurukka") || lower.contains("message vandhucha") ||
            lower.contains("yaar message") || lower.contains("yaar pannanga") ||
            lower.contains("enakku yaar") || lower.contains("whatsapp la message")) {
            return true
        }
        return false
    }
}
