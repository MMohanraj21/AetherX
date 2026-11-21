package com.jarvis.assistant.orchestrator

import com.jarvis.assistant.offline.OfflineCapabilityRegistry
import com.jarvis.assistant.offline.OfflineIntentClassifier

/**
 * High-speed intent router classifying user queries into destination domains:
 * TELECOM, NOTIFICATION, OFFLINE_DEVICE, MEDIA, AUTOMATION_MODE, EMERGENCY, or GENERATIVE_AI.
 */
object IntentRouter {

    enum class TargetDomain {
        TELECOM_CALL,
        NOTIFICATION_REPLY,
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
                    lower.contains("reject") || lower.contains("decline") || lower.contains("edunga")
            if (isCallCommand) {
                return RoutedIntent(TargetDomain.TELECOM_CALL, query)
            }
        }

        // 3. Notification Reply priority
        if (hasActiveNotifSession) {
            val isReplyCommand = lower.contains("yes") || lower.contains("send") ||
                    lower.contains("reply") || lower.contains("no") || lower.contains("cancel")
            if (isReplyCommand) {
                return RoutedIntent(TargetDomain.NOTIFICATION_REPLY, query)
            }
        }

        // 4. Media commands
        if (lower.contains("pause song") || lower.contains("pause music") ||
            lower.contains("play song") || lower.contains("next song") ||
            lower.contains("previous song") || lower.contains("current song")) {
            return RoutedIntent(TargetDomain.MEDIA_CONTROL, query)
        }

        // 5. Automation Routines
        if (lower.contains("morning mode") || lower.contains("night mode") ||
            lower.contains("work mode") || lower.contains("study mode") || lower.contains("driving mode")) {
            return RoutedIntent(TargetDomain.AUTOMATION_ROUTINE, query)
        }

        // 6. Offline device actions
        val offline = OfflineIntentClassifier.classify(query)
        if (offline != null) {
            return RoutedIntent(TargetDomain.OFFLINE_DEVICE, query, offline.target)
        }

        // 7. General multi-turn generative conversation & tool execution
        return RoutedIntent(TargetDomain.GENERATIVE_AI, query)
    }
}
