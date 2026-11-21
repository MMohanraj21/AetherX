package com.jarvis.assistant.tools

import com.jarvis.assistant.security.ActionRiskClassifier

/**
 * Central registry declaring all supported tools, their risk categories,
 * confirmation policies, and permission requirements.
 */
object ToolRegistry {

    data class ToolDefinition(
        val name: String,
        val description: String,
        val riskCategory: ActionRiskClassifier.RiskCategory,
        val requiresConfirmation: Boolean,
        val requiredPermissions: List<String> = emptyList()
    )

    private val tools = mapOf(
        "open_app" to ToolDefinition(
            name = "open_app",
            description = "Opens an installed application.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_A_HARMLESS,
            requiresConfirmation = false
        ),
        "open_website" to ToolDefinition(
            name = "open_website",
            description = "Opens websites/URLs in Chrome browser.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_A_HARMLESS,
            requiresConfirmation = false
        ),
        "set_flashlight" to ToolDefinition(
            name = "set_flashlight",
            description = "Turns device flashlight/torch on or off.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_A_HARMLESS,
            requiresConfirmation = false
        ),
        "set_brightness" to ToolDefinition(
            name = "set_brightness",
            description = "Adjusts screen brightness.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_A_HARMLESS,
            requiresConfirmation = false
        ),
        "set_volume" to ToolDefinition(
            name = "set_volume",
            description = "Adjusts device media volume.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_A_HARMLESS,
            requiresConfirmation = false
        ),
        "get_notifications" to ToolDefinition(
            name = "get_notifications",
            description = "Fetches active device notifications.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_A_HARMLESS,
            requiresConfirmation = false
        ),
        "manage_maps" to ToolDefinition(
            name = "manage_maps",
            description = "Navigates or searches on Google Maps.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_A_HARMLESS,
            requiresConfirmation = false
        ),
        "send_whatsapp_message" to ToolDefinition(
            name = "send_whatsapp_message",
            description = "Sends an external WhatsApp message.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_B_EXTERNAL_COMMUNICATION,
            requiresConfirmation = true,
            requiredPermissions = listOf("android.permission.READ_CONTACTS")
        ),
        "reply_to_notification" to ToolDefinition(
            name = "reply_to_notification",
            description = "Sends a reply to a notification via RemoteInput.",
            riskCategory = ActionRiskClassifier.RiskCategory.CATEGORY_B_EXTERNAL_COMMUNICATION,
            requiresConfirmation = true
        )
    )

    fun getTool(name: String): ToolDefinition? = tools[name]

    fun requiresConfirmation(name: String): Boolean = tools[name]?.requiresConfirmation ?: false
}
