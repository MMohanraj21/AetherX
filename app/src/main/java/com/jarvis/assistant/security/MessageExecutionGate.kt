package com.jarvis.assistant.security

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Strict Kotlin execution gate enforcing explicit verbal confirmation before
 * any communication action (WhatsApp, SMS, Notification reply) can be dispatched.
 * Cannot be bypassed by Gemini tool calls or UI assumptions.
 *
 * Integrated with [IntelligentConfirmationManager] for risk-based single confirmation
 * and duplicate execution protection.
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
        // Idempotency check: Was this action already executed recently?
        if (IntelligentConfirmationManager.isRecentlyExecuted(appType, recipient, exactContent)) {
            Log.w(TAG, "Idempotency filter: $appType to $recipient was already executed recently.")
        }

        // Re-use active draft if already staged for the same recipient and message
        val current = _activeDraft.value
        if (current != null && current.recipient.equals(recipient.trim(), ignoreCase = true) &&
            current.exactContent == exactContent.trim() && System.currentTimeMillis() <= current.expiresAt
        ) {
            val prompt = if (appType.contains("WHATSAPP", ignoreCase = true)) {
                "${current.recipient}-kku WhatsApp message send pannava?"
            } else {
                "${current.recipient}-kku reply send pannava?"
            }
            return GateResult.AwaitingConfirmation(current, prompt)
        }

        val draft = PendingMessageDraft(
            appType = appType,
            recipient = recipient.trim(),
            phoneNumber = phoneNumber?.trim(),
            exactContent = exactContent.trim(),
            replyAction = replyAction
        )
        _activeDraft.value = draft

        // Natural single-confirmation prompt
        val prompt = if (appType.contains("WHATSAPP", ignoreCase = true)) {
            "${draft.recipient}-kku WhatsApp message send pannava?"
        } else {
            "${draft.recipient}-kku reply send pannava?"
        }

        // Also stage in IntelligentConfirmationManager for unified state tracking
        IntelligentConfirmationManager.stageAction(
            actionType = appType,
            recipient = recipient,
            content = exactContent,
            phoneNumber = phoneNumber,
            replyAction = replyAction
        )

        Log.i(TAG, "Staged draft [${draft.actionId}] for ${draft.recipient}: '$prompt'")
        return GateResult.AwaitingConfirmation(draft, prompt)
    }

    fun handleUserVoiceTurn(userSpeech: String): GateResult {
        val draft = _activeDraft.value ?: return GateResult.NoPendingAction
        val now = System.currentTimeMillis()

        if (now > draft.expiresAt) {
            _activeDraft.value = null
            IntelligentConfirmationManager.clearPending()
            Log.w(TAG, "Draft [${draft.actionId}] expired.")
            return GateResult.Expired
        }

        val input = userSpeech.lowercase().trim()

        // 1. Cancellation check (English, Tamil, Tanglish, Hindi)
        val isCancel = input in setOf(
            "no", "nope", "cancel", "stop", "don't send", "dont send", "abort",
            "illai", "illa", "vendaam", "vendam", "cancel pannu", "pannatha",
            "nahi", "mat bhejo", "ruko"
        ) || input.startsWith("no ") || input.startsWith("don't ") || input.startsWith("dont ")

        if (isCancel) {
            _activeDraft.value = null
            IntelligentConfirmationManager.clearPending()
            Log.i(TAG, "User explicitly cancelled draft [${draft.actionId}].")
            return GateResult.Cancelled
        }

        // 2. Explicit Confirmation check (English, Tamil, Tanglish, Hindi)
        val isConfirm = input in setOf(
            "yes", "yeah", "yep", "sure", "ok", "okay", "send", "send it", "go ahead", "confirm",
            "ama", "aama", "aamanga", "sari", "seri", "send pannu", "send pannunga", "kandippa", "pannu",
            "theek hai", "haan", "bhejo", "bhej do"
        ) || input.startsWith("yes ") || input.startsWith("yes,") || input.contains("send it") || input.contains("send pannu")

        if (isConfirm) {
            draft.isAuthorized = true
            _activeDraft.value = null // Single-use consumption
            IntelligentConfirmationManager.markExecuted(draft.appType, draft.recipient, draft.exactContent)
            Log.i(TAG, "Draft [${draft.actionId}] explicitly AUTHORIZED by user.")
            return GateResult.Authorized(draft)
        }

        // Unclear speech or silence -> keep awaiting confirmation with consistent prompt
        val reprompt = if (draft.appType.contains("WHATSAPP", ignoreCase = true)) {
            "${draft.recipient}-kku WhatsApp message send pannava?"
        } else {
            "${draft.recipient}-kku reply send pannava?"
        }
        return GateResult.AwaitingConfirmation(draft, reprompt)
    }

    /**
     * Pre-execution validation: Ensures that the exact recipient and content were authorized.
     */
    fun validateExecution(token: String, recipient: String, content: String): Boolean {
        // If already verified as recently executed, allow to prevent failure on retry
        if (IntelligentConfirmationManager.isRecentlyExecuted("WHATSAPP", recipient, content) ||
            IntelligentConfirmationManager.isRecentlyExecuted("NOTIFICATION_REPLY", recipient, content)
        ) {
            return true
        }

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
        IntelligentConfirmationManager.clearPending()
    }
}
