package com.jarvis.assistant.conversation

import android.content.Context
import android.util.Log

/**
 * Detects missing required parameters for user action requests and generates
 * natural, concise single-question clarifications while preserving context.
 */
object ClarificationManager {

    private const val TAG = "ClarificationManager"

    sealed class ClarificationResult {
        object Ready : ClarificationResult()
        data class NeedsClarification(val question: String, val pendingField: String) : ClarificationResult()
    }

    fun evaluateActionCompleteness(
        intent: String,
        targetApp: String?,
        recipient: String?,
        content: String?
    ): ClarificationResult {
        when (intent) {
            "SEND_MESSAGE", "WHATSAPP_MESSAGE", "SMS" -> {
                if (recipient.isNullOrBlank()) {
                    val question = "Sure, who should I send the message to?"
                    ConversationStateManager.setPendingClarification(
                        ConversationStateManager.PendingClarification(
                            intentType = intent,
                            missingField = "recipient",
                            questionAsked = question,
                            partialPayload = mapOf("content" to (content ?: ""))
                        )
                    )
                    return ClarificationResult.NeedsClarification(question, "recipient")
                }

                if (content.isNullOrBlank()) {
                    val question = "What would you like me to tell $recipient?"
                    ConversationStateManager.setPendingClarification(
                        ConversationStateManager.PendingClarification(
                            intentType = intent,
                            missingField = "content",
                            questionAsked = question,
                            partialPayload = mapOf("recipient" to recipient)
                        )
                    )
                    return ClarificationResult.NeedsClarification(question, "content")
                }
            }

            "CALL_CONTACT" -> {
                if (recipient.isNullOrBlank()) {
                    val question = "Who would you like to call?"
                    ConversationStateManager.setPendingClarification(
                        ConversationStateManager.PendingClarification(
                            intentType = intent,
                            missingField = "recipient",
                            questionAsked = question
                        )
                    )
                    return ClarificationResult.NeedsClarification(question, "recipient")
                }
            }

            "CREATE_REMINDER" -> {
                if (content.isNullOrBlank()) {
                    val question = "Sure, what should I remind you about?"
                    ConversationStateManager.setPendingClarification(
                        ConversationStateManager.PendingClarification(
                            intentType = intent,
                            missingField = "content",
                            questionAsked = question
                        )
                    )
                    return ClarificationResult.NeedsClarification(question, "content")
                }
            }
        }

        return ClarificationResult.Ready
    }

    /**
     * Resolves user's spoken answer into the pending clarification field.
     */
    fun resolveAnswer(userAnswer: String): Map<String, String>? {
        val pending = ConversationStateManager.pendingClarification.value ?: return null
        val cleanAnswer = userAnswer.trim()
        if (cleanAnswer.isBlank()) return null

        val updated = pending.partialPayload.toMutableMap()
        updated[pending.missingField] = cleanAnswer

        Log.i(TAG, "Clarification '${pending.missingField}' resolved to '$cleanAnswer'")
        ConversationStateManager.clearPendingClarification()
        return updated
    }
}
