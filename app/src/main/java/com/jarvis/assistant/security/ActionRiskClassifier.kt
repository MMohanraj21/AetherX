package com.jarvis.assistant.security

/**
 * Risk classification for actions requested by users or emitted by tools.
 */
object ActionRiskClassifier {

    enum class RiskCategory {
        /**
         * Category A: Informational queries and harmless, reversible actions.
         * Open apps, adjust volume, toggle flashlight, read notifications, battery check.
         * Rule: Direct execution without confirmation prompts.
         */
        CATEGORY_A_HARMLESS,

        /**
         * Category B: Sensitive external communication.
         * WhatsApp message, SMS, Notification reply, Email, Social media post.
         * Rule: MANDATORY explicit verbal confirmation of EXACT recipient and content.
         */
        CATEGORY_B_EXTERNAL_COMMUNICATION,

        /**
         * Category C: Destructive or high-impact device actions.
         * Delete file, delete contact, clear app data, factory reset.
         * Rule: Strong explicit confirmation explaining target and consequence.
         */
        CATEGORY_C_DESTRUCTIVE,

        /**
         * Category D: Restricted or unsupported system actions.
         */
        CATEGORY_D_UNSUPPORTED
    }

    fun classifyAction(actionName: String): RiskCategory {
        val lower = actionName.lowercase().trim()
        return when {
            // Category B: Communication
            lower.contains("send_whatsapp") || lower.contains("whatsapp_message") ||
            lower.contains("send_sms") || lower.contains("sms") ||
            lower.contains("reply_to_notification") || lower.contains("notification_reply") ||
            lower.contains("send_email") || lower.contains("post_social") -> {
                RiskCategory.CATEGORY_B_EXTERNAL_COMMUNICATION
            }

            // Category C: Destructive
            lower.contains("delete") || lower.contains("remove") ||
            lower.contains("clear_data") || lower.contains("uninstall") ||
            lower.contains("wipe") -> {
                RiskCategory.CATEGORY_C_DESTRUCTIVE
            }

            // Category A: Harmless
            lower.contains("open_app") || lower.contains("open_website") ||
            lower.contains("flashlight") || lower.contains("torch") ||
            lower.contains("volume") || lower.contains("brightness") ||
            lower.contains("get_notifications") || lower.contains("battery") ||
            lower.contains("manage_maps") || lower.contains("play_youtube") ||
            lower.contains("search") || lower.contains("alarm") || lower.contains("reminder") -> {
                RiskCategory.CATEGORY_A_HARMLESS
            }

            else -> RiskCategory.CATEGORY_A_HARMLESS
        }
    }
}
