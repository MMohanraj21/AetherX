package com.jarvis.assistant.offline

/**
 * Classifies spoken utterances in English, Tamil, and Tanglish into offline executable actions.
 */
object OfflineIntentClassifier {

    data class OfflineIntentResult(
        val action: OfflineCapabilityRegistry.OfflineAction,
        val target: String = "",
        val confidence: Float = 0.95f
    )

    fun classify(utterance: String): OfflineIntentResult? {
        val text = utterance.lowercase().trim()

        // Flashlight
        if (text.contains("torch on") || text.contains("flashlight on") || text.contains("light podu") || text.contains("torch podunga")) {
            return OfflineIntentResult(OfflineCapabilityRegistry.OfflineAction.FLASHLIGHT_ON)
        }
        if (text.contains("torch off") || text.contains("flashlight off") || text.contains("light anai") || text.contains("torch off pannunga")) {
            return OfflineIntentResult(OfflineCapabilityRegistry.OfflineAction.FLASHLIGHT_OFF)
        }

        // Volume
        if (text.contains("volume up") || text.contains("satham ethu") || text.contains("increase volume")) {
            return OfflineIntentResult(OfflineCapabilityRegistry.OfflineAction.VOLUME_UP)
        }
        if (text.contains("volume down") || text.contains("satham kura") || text.contains("decrease volume")) {
            return OfflineIntentResult(OfflineCapabilityRegistry.OfflineAction.VOLUME_DOWN)
        }

        // Time
        if (text.contains("time enna") || text.contains("what is the time") || text.contains("current time") || text.contains("mani enna")) {
            return OfflineIntentResult(OfflineCapabilityRegistry.OfflineAction.TELL_TIME)
        }

        // App Launch
        val openPrefixes = listOf("open ", "launch ", "thira ", "open pannu ", "open pannunga ")
        for (prefix in openPrefixes) {
            if (text.startsWith(prefix) || text.contains(prefix)) {
                val appPart = text.substringAfter(prefix).trim().split(" ").firstOrNull() ?: ""
                if (appPart.isNotBlank()) {
                    return OfflineIntentResult(OfflineCapabilityRegistry.OfflineAction.OPEN_APP, target = appPart)
                }
            }
        }

        return null
    }
}
