package com.jarvis.assistant.conversation

import android.content.Context
import android.util.Log

/**
 * Central conversational reasoning engine coordinating natural language comprehension,
 * multi-turn reference resolution, missing information clarification, and action routing.
 */
class ConversationalReasoningEngine(private val context: Context) {

    companion object {
        private const val TAG = "ConvReasoningEngine"

        @Volatile
        private var instance: ConversationalReasoningEngine? = null

        fun getInstance(context: Context): ConversationalReasoningEngine {
            return instance ?: synchronized(this) {
                instance ?: ConversationalReasoningEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    sealed class ReasoningResult {
        data class SpeakClarification(val question: String) : ReasoningResult()
        data class SpeakConfirmation(val prompt: String, val actionToken: String) : ReasoningResult()
        data class ExecuteDirectly(val resolvedQuery: String) : ReasoningResult()
        data class DelegateToGemini(val resolvedQuery: String) : ReasoningResult()
        data class HandledLocally(val feedback: String) : ReasoningResult()
    }

    fun evaluate(rawUtterance: String): ReasoningResult {
        // 1. Context Resolution (Resolve pronouns like "he", "him", "that app" from prior turns)
        val contextResolution = ContextResolver.resolveQueryContext(rawUtterance)
        val workingQuery = contextResolution.resolvedQuery

        // 2. Query Understanding (Extract intent type, language, entities)
        val analysis = QueryUnderstandingEngine.analyze(workingQuery)
        Log.i(TAG, "Utterance: '$rawUtterance' -> Resolved: '$workingQuery' [${analysis.queryType}]")

        // 3. Handle Pending Clarification Answer
        if (analysis.queryType == QueryUnderstandingEngine.QueryType.CLARIFICATION_ANSWER) {
            val payload = ClarificationManager.resolveAnswer(rawUtterance)
            if (payload != null) {
                val recipient = payload["recipient"]
                val content = payload["content"]
                if (!recipient.isNullOrBlank() && !content.isNullOrBlank()) {
                    // Both fields now resolved! Generate draft & ask final confirmation
                    val prompt = "I'll send $recipient: '$content'. Should I send it?"
                    ConversationStateManager.recordEntity(ConversationStateManager.EntityType.PERSON_CONTACT, recipient, recipient)
                    return ReasoningResult.SpeakConfirmation(prompt, "AUTH_${System.currentTimeMillis()}")
                } else if (!recipient.isNullOrBlank() && content.isNullOrBlank()) {
                    // Recipient obtained, now ask for message
                    ConversationStateManager.recordEntity(ConversationStateManager.EntityType.PERSON_CONTACT, recipient, recipient)
                    val followUp = "What would you like me to tell $recipient?"
                    return ReasoningResult.SpeakClarification(followUp)
                }
            }
        }

        // 4. Handle Actions with Missing Info
        if (analysis.queryType == QueryUnderstandingEngine.QueryType.ACTION_REQUEST) {
            val completeness = ClarificationManager.evaluateActionCompleteness(
                intent = "SEND_MESSAGE",
                targetApp = analysis.targetApp,
                recipient = analysis.targetContact,
                content = analysis.messageBody
            )
            if (completeness is ClarificationManager.ClarificationResult.NeedsClarification) {
                return ReasoningResult.SpeakClarification(completeness.question)
            }
        }

        // 5. Update Entity Tracker for future turns
        analysis.targetContact?.let {
            ConversationStateManager.recordEntity(ConversationStateManager.EntityType.PERSON_CONTACT, it, it)
        }
        analysis.targetApp?.let {
            ConversationStateManager.recordEntity(ConversationStateManager.EntityType.APPLICATION, it, it)
        }

        // 6. Forward to Gemini Live with fully resolved pronouns & entities
        return ReasoningResult.DelegateToGemini(workingQuery)
    }
}
