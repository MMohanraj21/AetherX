package com.jarvis.assistant.audio.realtime

/**
 * Context-aware End-Of-Turn Detector for ZARA AI v5.0.
 *
 * Implements adaptive silence timeouts rather than a static single threshold.
 * Prevents premature speech cutoffs during natural multi-word Tamil/Tanglish/English
 * pauses and hesitation words ("umm", "anal", "appuram"), while accelerating completion
 * for single-word confirmations ("yes", "send it", "aama").
 */
object EndOfTurnDetector {

    // Optimized for ultra-responsive, low-latency turn completion
    private const val THRESHOLD_SHORT_CONFIRMATION_MS = 200L
    private const val THRESHOLD_DEFAULT_CONVERSATION_MS = 320L
    private const val THRESHOLD_EXTENDED_CLAUSE_MS = 600L

    // Short affirmative or negative tokens where user has clearly completed their turn
    private val SHORT_CONFIRMATION_TOKENS = setOf(
        "yes", "yeah", "yep", "sure", "ok", "okay", "no", "nope", "cancel", "stop",
        "send", "send it", "confirm", "aama", "sari", "illa", "vendaam", "podhum",
        "haan", "theek hai", "nahi"
    )

    // Conjunctions and hesitation markers indicating the user is formulating their next clause
    private val CONTINUATION_TOKENS = setOf(
        // English
        "and", "or", "because", "but", "so", "that", "if", "when", "while", "though",
        "although", "which", "who", "where", "how", "with", "for", "to", "about",
        "umm", "uh", "uhh", "er", "wait", "like",
        // Tamil / Tanglish
        "anal", "irundhalum", "appuram", "endru", "enna", "illana", "athoda", "kooda",
        "paththi", "solli", "kettu", "irukku"
    )

    /**
     * Calculates the optimal silence threshold (in milliseconds) based on transcribed text
     * and utterance duration.
     */
    fun getAdaptiveSilenceThreshold(recentTranscribedText: String, speechDurationMs: Long): Long {
        val trimmed = recentTranscribedText.trim().lowercase()
        if (trimmed.isEmpty()) {
            return THRESHOLD_DEFAULT_CONVERSATION_MS
        }

        val words = trimmed.split(Regex("""\s+"""))
        val lastWord = words.lastOrNull()?.trimEnd('.', '!', '?', ',') ?: ""

        // 1. Single or two-word affirmative/cancelation command: fast completion
        if (words.size <= 2 && (SHORT_CONFIRMATION_TOKENS.contains(trimmed) || SHORT_CONFIRMATION_TOKENS.contains(lastWord))) {
            return THRESHOLD_SHORT_CONFIRMATION_MS
        }

        // 2. Open clause or hesitation: user paused mid-sentence, grant extended thinking time
        if (CONTINUATION_TOKENS.contains(lastWord)) {
            return THRESHOLD_EXTENDED_CLAUSE_MS
        }

        // 3. Very short query (< 1 second) that is not an affirmative: give reasonable headroom
        if (speechDurationMs < 1000L && words.size < 3) {
            return 360L
        }

        // 4. Standard conversational sentence
        return THRESHOLD_DEFAULT_CONVERSATION_MS
    }

    /**
     * Determines whether the current turn should be closed based on elapsed silent duration.
     */
    fun isTurnComplete(silentDurationMs: Long, recentTranscribedText: String, speechDurationMs: Long): Boolean {
        val threshold = getAdaptiveSilenceThreshold(recentTranscribedText, speechDurationMs)
        return silentDurationMs >= threshold
    }
}
