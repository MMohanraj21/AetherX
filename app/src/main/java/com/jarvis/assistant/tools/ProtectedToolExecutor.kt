package com.jarvis.assistant.tools

import android.content.Context
import android.util.Log
import com.jarvis.assistant.security.MessageExecutionGate
import org.json.JSONObject

/**
 * Protects tool execution by verifying security and confirmation requirements
 * before mutating device state or sending external communications.
 */
object ProtectedToolExecutor {

    private const val TAG = "ProtectedToolExecutor"

    sealed class ExecutionGateResult {
        object Allowed : ExecutionGateResult()
        data class RequiresConfirmation(val prompt: String, val draftToken: String) : ExecutionGateResult()
        data class Rejected(val reason: String) : ExecutionGateResult()
    }

    /**
     * Intercepts tool calls before execution. For Category B tools, stages a draft
     * and blocks execution until explicit user verbal confirmation is obtained.
     */
    fun evaluateToolExecution(
        toolName: String,
        args: JSONObject
    ): ExecutionGateResult {
        val toolDef = ToolRegistry.getTool(toolName)
        val requiresConfirm = toolDef?.requiresConfirmation ?: false

        if (!requiresConfirm) {
            return ExecutionGateResult.Allowed
        }

        when (toolName) {
            "send_whatsapp_message" -> {
                val recipient = args.optString("recipient_name", "").trim()
                val message = args.optString("message", "").trim()

                if (recipient.isBlank() || message.isBlank()) {
                    return ExecutionGateResult.Rejected("Recipient or message is empty.")
                }

                // Stage in MessageExecutionGate
                val staging = MessageExecutionGate.stageDraft(
                    appType = "WHATSAPP",
                    recipient = recipient,
                    exactContent = message
                )

                Log.i(TAG, "Tool '$toolName' gated: Staged draft for $recipient. Prompting user.")
                return ExecutionGateResult.RequiresConfirmation(staging.spokenPrompt, staging.draft.authToken)
            }

            "reply_to_notification" -> {
                val sender = args.optString("sender", "").trim()
                val message = args.optString("message", "").trim()

                if (message.isBlank()) {
                    return ExecutionGateResult.Rejected("Reply text cannot be empty.")
                }

                val staging = MessageExecutionGate.stageDraft(
                    appType = "NOTIFICATION_REPLY",
                    recipient = if (sender.isNotBlank()) sender else "Sender",
                    exactContent = message
                )

                Log.i(TAG, "Tool '$toolName' gated: Staged draft reply. Prompting user.")
                return ExecutionGateResult.RequiresConfirmation(staging.spokenPrompt, staging.draft.authToken)
            }
        }

        return ExecutionGateResult.Allowed
    }
}
