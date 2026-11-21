package com.jarvis.assistant.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Strict Kotlin execution gate enforcing explicit verbal confirmation before
 * any communication action (WhatsApp, SMS, Notification reply) can be dispatched.
 * Cannot be bypassed by Gemini tool calls or UI assumptions.
 */
object MessageExecutionGate {

    private const val TAG = "MessageExecutionGate"

    sealed class GateResult {
        data class AwaitingConfirmation(val draft: PendingMessageDraft, val spokenPrompt: String) : GateResult()
        data class Authorized(val draft: PendingMessageDraft) : GateResult()
        object Cancelled : GateResult()
        object Expired : GateResult()
        object NoPendingAction : GateResult()
    }

    private val _activeDraft = MutableStateFlow<PendingMessageDraft?>(null)
    val activeDraft: StateFlow<PendingMessageDraft?> = _activeDraft.asStateFlow()

    fun stageDraft(
        appType: String,
        recipient: String,
        phoneNumber: String? = null,
        exactContent: String,
        replyAction: Any? = null
    ): GateResult.AwaitingConfirmation {
        val draft = PendingMessageDraft(
            appType = appType,
            recipient = recipient.trim(),
            phoneNumber = phoneNumber?.trim(),
            exactContent = exactContent.trim(),
            replyAction = replyAction
        )
        _activeDraft.value = draft
        val prompt = "I'll send ${draft.recipient}: '${draft.exactContent}'. Should I send it?"
        Log.i(TAG, "Staged draft [${draft.actionId}] for ${draft.recipient}. Awaiting approval.")
        return GateResult.AwaitingConfirmation(draft, prompt)
    }

    fun handleUserVoiceTurn(userSpeech: String): GateResult {
        val draft = _activeDraft.value ?: return GateResult.NoPendingAction
        val now = System.currentTimeMillis()

        if (now > draft.expiresAt) {
            _activeDraft.value = null
            Log.w(TAG, "Draft [${draft.actionId}] expired.")
            return GateResult.Expired
        }

        val input = userSpeech.lowercase().trim()

        // 1. Cancellation check
        val isCancel = input in setOf("no", "cancel", "don't send", "abort", "vendaam", "cancel pannu", "nahi", "mat bhejo", "stop") ||
                input.startsWith("no ") || input.contains("don't") || input.contains("cancel")
        if (isCancel) {
            _activeDraft.value = null
            Log.i(TAG, "User explicitly cancelled draft [${draft.actionId}].")
            return GateResult.Cancelled
        }

        // 2. Explicit Confirmation check
        val isConfirm = input in setOf("yes", "yes send it", "send it", "confirm", "send", "go ahead", "sure", "okay send", "aama", "send pannunga", "haan", "theek hai") ||
                input.startsWith("yes,") || input.startsWith("yes ") || input.contains("send it") || input.contains("send pannu")
        if (isConfirm) {
            draft.isAuthorized = true
            _activeDraft.value = null // Single-use consumption
            Log.i(TAG, "Draft [${draft.actionId}] explicitly AUTHORIZED by user.")
            return GateResult.Authorized(draft)
        }

        // Unclear speech or silence -> keep awaiting confirmation
        val reprompt = "Should I send: '${draft.exactContent}' to ${draft.recipient}?"
        return GateResult.AwaitingConfirmation(draft, reprompt)
    }

    /**
     * Pre-execution validation: Ensures that the exact recipient and content were authorized.
     */
    fun validateExecution(token: String, recipient: String, content: String): Boolean {
        val draft = _activeDraft.value
        if (draft == null || !draft.isAuthorized) {
            Log.e(TAG, "Execution rejected: No authorized draft found.")
            return false
        }

        val tokenMatch = draft.authToken == token
        val recipientMatch = draft.recipient.equals(recipient.trim(), ignoreCase = true)
        val contentMatch = draft.exactContent == content.trim()

        if (!tokenMatch || !recipientMatch || !contentMatch) {
            Log.e(TAG, "Execution rejected: Token or payload mismatch! (token=$tokenMatch, recipient=$recipientMatch, content=$contentMatch)")
            _activeDraft.value = null // Invalidate on tampering
            return false
        }

        _activeDraft.value = null // Invalidate on use
        return true
    }

    fun hasPendingConfirmation(): Boolean {
        val d = _activeDraft.value ?: return false
        if (System.currentTimeMillis() > d.expiresAt) {
            _activeDraft.value = null
            return false
        }
        return true
    }

    fun cancelActiveDraft() {
        _activeDraft.value = null
    }
}
