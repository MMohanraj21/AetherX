package com.jarvis.assistant.memory.v4

import com.jarvis.assistant.data.memory.MemoryEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages memory export, deletion, and privacy policies.
 */
object MemoryPrivacyManager {

    /**
     * Serializes memory list to clean JSON for user data export.
     */
    fun exportToJson(memories: List<MemoryEntity>): String {
        val root = JSONObject()
        root.put("version", "4.0")
        root.put("exportedAt", System.currentTimeMillis())

        val array = JSONArray()
        for (m in memories) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("category", m.category)
                put("key", m.key)
                put("value", m.value)
                put("importance", m.importance)
                put("confidence", m.confidence.toDouble())
                put("timestamp", m.timestamp)
            }
            array.put(obj)
        }
        root.put("memories", array)
        return root.toString(2)
    }

    /**
     * Sanitizes sensitive text before long-term memory persistence.
     */
    fun sanitize(text: String): String {
        return text
            .replace(Regex("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"), "[REDACTED_CARD]")
            .replace(Regex("\\b(?:otp|password|pin)[^\\w]{1,5}\\w+\\b", RegexOption.IGNORE_CASE), "[REDACTED_CREDENTIAL]")
    }
}
