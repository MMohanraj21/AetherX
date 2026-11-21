package com.jarvis.assistant.conversation

import android.util.Log
import java.util.Locale

/**
 * Parses user utterances to extract language, intent type, action category,
 * and key semantic entities without changing the original query's core meaning.
 */
object QueryUnderstandingEngine {

    private const val TAG = "QueryUnderstanding"

    enum class QueryType {
        INFORMATIONAL,          // "What is 5G?", "Who is the Chief Minister?", "How much battery?"
        ACTION_REQUEST,         // "Open WhatsApp", "Send a message", "Turn on flashlight"
        CLARIFICATION_ANSWER,   // Responding to a question like "Who should I send it to?" -> "Arun"
        CONFIRMATION_RESPONSE,  // "Yes, send it", "Confirm", "Cancel", "No"
        GENERAL_CONVERSATION    // "I'm feeling bored", "How are you?", "Thank you"
    }

    enum class DetectedLanguage {
        ENGLISH,
        TAMIL,
        TANGLISH,
        HINDI,
        HINGLISH
    }

    data class AnalyzedQuery(
        val rawUtterance: String,
        val normalizedQuery: String,
        val queryType: QueryType,
        val language: DetectedLanguage,
        val targetApp: String? = null,
        val targetContact: String? = null,
        val messageBody: String? = null,
        val isHardwareQuery: Boolean = false,
        val isDeviceControl: Boolean = false
    )

    fun analyze(utterance: String): AnalyzedQuery {
        val clean = normalize(utterance)
        val lang = detectLanguage(clean)
        val lower = clean.lowercase(Locale.ROOT)

        // 1. Check if user is responding to pending confirmation
        val isConfirm = isConfirmationPhrase(lower)
        val isCancel = isCancellationPhrase(lower)
        if (isConfirm || isCancel) {
            return AnalyzedQuery(
                rawUtterance = utterance,
                normalizedQuery = clean,
                queryType = QueryType.CONFIRMATION_RESPONSE,
                language = lang
            )
        }

        // 2. Check if user is responding to pending clarification
        if (ConversationStateManager.hasPendingClarification()) {
            return AnalyzedQuery(
                rawUtterance = utterance,
                normalizedQuery = clean,
                queryType = QueryType.CLARIFICATION_ANSWER,
                language = lang
            )
        }

        // 3. Hardware status queries (Battery, Network, Flashlight state)
        val isHardware = isDeviceStateQuery(lower)
        if (isHardware) {
            return AnalyzedQuery(
                rawUtterance = utterance,
                normalizedQuery = clean,
                queryType = QueryType.INFORMATIONAL,
                language = lang,
                isHardwareQuery = true
            )
        }

        // 4. Pure informational queries (What is..., Who is..., How does..., Why...)
        val isInfo = isInformationalQuery(lower)
        if (isInfo) {
            return AnalyzedQuery(
                rawUtterance = utterance,
                normalizedQuery = clean,
                queryType = QueryType.INFORMATIONAL,
                language = lang
            )
        }

        // 5. Action requests (Open, Send, Call, Set, Adjust, Delete)
        val isAction = isActionRequest(lower)
        if (isAction) {
            val contact = extractContact(lower)
            val app = extractApp(lower)
            val msg = extractMessage(lower)
            return AnalyzedQuery(
                rawUtterance = utterance,
                normalizedQuery = clean,
                queryType = QueryType.ACTION_REQUEST,
                language = lang,
                targetApp = app,
                targetContact = contact,
                messageBody = msg,
                isDeviceControl = isDeviceControlQuery(lower)
            )
        }

        // 6. General conversation ("I'm bored", "Tell me something", etc.)
        return AnalyzedQuery(
            rawUtterance = utterance,
            normalizedQuery = clean,
            queryType = QueryType.GENERAL_CONVERSATION,
            language = lang
        )
    }

    private fun normalize(raw: String): String {
        val wakeRegex = Regex("(?i)^(?:hey|hi|hello|ok|okay|wake\s+up|listen)?\s*(?:zara|sara|zahra|xara)[,\s]*")
        return raw.replace(wakeRegex, "").trim().trimEnd('.', '!', '?', ',')
    }

    private fun detectLanguage(text: String): DetectedLanguage {
        val lower = text.lowercase()
        // Tamil unicode range
        val hasTamilChars = text.any { it in '஀'..'௿' }
        if (hasTamilChars) return DetectedLanguage.TAMIL

        // Tanglish heuristics
        val tanglishWords = setOf("ku", "anuppu", "pannu", "edunga", "vandhurukka", "irukka", "enna", "sari", "aama", "vendaam", "soller", "pesu")
        val words = lower.split(Regex("\s+"))
        if (words.any { it in tanglishWords }) return DetectedLanguage.TANGLISH

        // Hindi heuristics
        val hasDevanagari = text.any { it in 'ऀ'..'ॿ' }
        if (hasDevanagari) return DetectedLanguage.HINDI

        val hinglishWords = setOf("bhai", "karo", "kardo", "kaise", "kya", "batao", "bhejo", "chalao")
        if (words.any { it in hinglishWords }) return DetectedLanguage.HINGLISH

        return DetectedLanguage.ENGLISH
    }

    fun isConfirmationPhrase(lower: String): Boolean {
        return lower in setOf("yes", "yes send it", "send it", "confirm", "send", "go ahead", "sure", "okay send", "aama", "send pannunga", "haan", "theek hai") ||
                lower.startsWith("yes,") || lower.startsWith("yes ") || lower.contains("send it") || lower.contains("send pannu")
    }

    fun isCancellationPhrase(lower: String): Boolean {
        return lower in setOf("no", "cancel", "don't send", "abort", "vendaam", "cancel pannu", "nahi", "mat bhejo", "stop") ||
                lower.startsWith("no,") || lower.startsWith("no ") || lower.contains("don't") || lower.contains("cancel")
    }

    private fun isDeviceStateQuery(lower: String): Boolean {
        return lower.contains("battery") || lower.contains("charge") ||
                lower.contains("wifi") || lower.contains("network") ||
                lower.contains("storage") || lower.contains("time")
    }

    private fun isInformationalQuery(lower: String): Boolean {
        return (lower.startsWith("what is") || lower.startsWith("who is") || lower.startsWith("how to") ||
                lower.startsWith("how does") || lower.startsWith("tell me about") || lower.startsWith("explain") ||
                lower.startsWith("where is") || lower.startsWith("when was") || lower.startsWith("why")) &&
                !lower.contains("battery") && !lower.contains("open")
    }

    private fun isActionRequest(lower: String): Boolean {
        return lower.startsWith("open ") || lower.startsWith("launch ") ||
                lower.startsWith("send ") || lower.contains("message") ||
                lower.startsWith("call ") || lower.contains("anuppu") ||
                lower.startsWith("turn on ") || lower.startsWith("turn off ") ||
                lower.startsWith("set brightness") || lower.startsWith("set volume") ||
                lower.startsWith("delete ") || lower.startsWith("remind me")
    }

    private fun isDeviceControlQuery(lower: String): Boolean {
        return lower.contains("flashlight") || lower.contains("torch") ||
                lower.contains("brightness") || lower.contains("volume") ||
                lower.contains("bluetooth") || lower.contains("wifi")
    }

    private fun extractApp(lower: String): String? {
        val apps = listOf("whatsapp", "chrome", "youtube", "camera", "settings", "instagram", "telegram", "maps", "gmail", "spotify")
        return apps.firstOrNull { lower.contains(it) }
    }

    private fun extractContact(lower: String): String? {
        val match = Regex("(?:to|call|message|for)\s+([a-zA-Z]+)").find(lower)
        return match?.groupValues?.getOrNull(1)?.takeIf { it !in listOf("me", "a", "the", "my", "someone", "him", "her") }
    }

    private fun extractMessage(lower: String): String? {
        val match = Regex("(?:saying|tell him|tell her|that)\s+(.+)").find(lower)
        return match?.groupValues?.getOrNull(1)?.trim()
    }
}
