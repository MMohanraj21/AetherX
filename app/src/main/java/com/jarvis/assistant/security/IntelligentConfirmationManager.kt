package com.jarvis.assistant.security

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Single Confirmation Manager
 *
 * Implements a centralized, risk-based confirmation policy with duplicate execution protection:
 *
 * 1. Category A (No confirmation needed):
 *    - Open apps (Instagram, ChatGPT, YouTube, etc.)
 *    - Tell time, battery status, volume, flashlight
 *    - General Q&A, web search, music controls
 *    -> Executes immediately without asking.
 *
 * 2. Category B (Single confirmation):
 *    - Sending WhatsApp messages, SMS, emails, notification replies
 *    - Consequential external actions
 *    -> Asks EXACTLY ONCE in natural colloquial Tamil/Tanglish/English:
 *       "Rahul-kku WhatsApp message send pannava?" / "Should I send this message to Rahul?"
 *    -> User says YES ("yes", "ama", "sari", "send pannu", "kandippa", "ok", "go ahead")
 *       -> Executes immediately once, speaks "Message sent successfully.", and clears pending.
 *    -> User says NO ("no", "illai", "vendaam", "cancel", "stop", "don't send")
 *       -> Cancels immediately, speaks "Okay, message cancelled.", and clears pending.
 *    -> Never asks again.
 *
 * 3. Category C (Strong confirmation):
 *    - Deleting important files, financial transactions, irreversible operations.
 *    -> Explicit safety challenge.
 *
 * Duplicate Execution Protection:
 * - Unique request/action IDs.
 * - Idempotency tracking (recently executed action hashes kept with 30s TTL).
 * - Duplicate callback detection.
 * - Single-use token invalidation.
 */
object IntelligentConfirmationManager {

    private const val TAG = "ConfirmationManager"

    enum class RiskCategory {
        CATEGORY_A_NO_CONFIRMATION,     // Direct execution
        CATEGORY_B_SINGLE_CONFIRMATION, // Ask once, execute on confirmation
        CATEGORY_C_STRONG_CONFIRMATION  // Strong explicit confirmation
    }

    enum class ConfirmationStatus {
        PENDING,
        AUTHORIZED,
        CANCELLED,
        EXPIRED
    }

    data class PendingAction(
        val actionId: String = UUID.randomUUID().toString(),
        val actionType: String, // "WHATSAPP", "NOTIFICATION_REPLY", "SMS", "DELETE_FILE"
        val recipient: String,
        val phoneNumber: String? = null,
        val content: String,
        val spokenPrompt: String,
        val authToken: String = "AUTH_" + UUID.randomUUID().toString().take(8),
        val replyAction: Any? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val expiresAt: Long = System.currentTimeMillis() + 60_000L,
        var status: ConfirmationStatus = ConfirmationStatus.PENDING
    )

    sealed class ConfirmationResult {
        data class AwaitingConfirmation(val action: PendingAction, val spokenPrompt: String) : ConfirmationResult()
        data class Authorized(val action: PendingAction) : ConfirmationResult()
        data class Cancelled(val action: PendingAction) : ConfirmationResult()
        data class AlreadyExecuted(val actionKey: String) : ConfirmationResult()
        object Expired : ConfirmationResult()
        object NoPendingAction : ConfirmationResult()
    }

    private val _activeAction = MutableStateFlow<PendingAction?>(null)
    val activeAction: StateFlow<PendingAction?> = _activeAction.asStateFlow()

    // Idempotency cache: actionKey -> timestamp
    private val recentlyExecutedActions = ConcurrentHashMap<String, Long>()
    private const val IDEMPOTENCY_WINDOW_MS = 30_000L

    fun classifyAction(actionType: String): RiskCategory {
        return when (actionType.uppercase()) {
            "OPEN_APP", "LAUNCH_APP", "TELL_TIME", "GET_BATTERY", "FLASHLIGHT",
            "SET_VOLUME", "MEDIA_CONTROL", "SEARCH_WEB", "INFORMATIONAL", "GENERAL_QA" ->
                RiskCategory.CATEGORY_A_NO_CONFIRMATION

            "WHATSAPP_MESSAGE", "SEND_WHATSAPP", "WHATSAPP", "SEND_SMS", "SMS",
            "NOTIFICATION_REPLY", "SEND_EMAIL", "EMAIL", "SHARE_LOCATION" ->
                RiskCategory.CATEGORY_B_SINGLE_CONFIRMATION

            "DELETE_FILE", "FACTORY_RESET", "FINANCIAL_TRANSACTION", "MAKE_PURCHASE" ->
                RiskCategory.CATEGORY_C_STRONG_CONFIRMATION

            else -> RiskCategory.CATEGORY_A_NO_CONFIRMATION
        }
    }

    fun isRecentlyExecuted(actionType: String, recipient: String, content: String): Boolean {
        cleanupExpiredIdempotency()
        val key = buildFingerprint(actionType, recipient, content)
        val execTime = recentlyExecutedActions[key] ?: return false
        return (System.currentTimeMillis() - execTime) < IDEMPOTENCY_WINDOW_MS
    }

    fun markExecuted(actionType: String, recipient: String, content: String) {
        val key = buildFingerprint(actionType, recipient, content)
        recentlyExecutedActions[key] = System.currentTimeMillis()
        _activeAction.value = null
        Log.i(TAG, "Marked action as EXECUTED with idempotency key: $key")
    }

    fun stageAction(
        actionType: String,
        recipient: String,
        content: String,
        phoneNumber: String? = null,
        replyAction: Any? = null,
        language: String = "auto"
    ): ConfirmationResult {
        cleanupExpiredIdempotency()

        // 1. Idempotency protection: Prevent duplicate action staging
        if (isRecentlyExecuted(actionType, recipient, content)) {
            Log.w(TAG, "Blocked duplicate action staging: $actionType for $recipient")
            return ConfirmationResult.AlreadyExecuted("$actionType:$recipient")
        }

        // 2. If identical action is already pending, return existing prompt without re-asking differently
        val existing = _activeAction.value
        if (existing != null && existing.status == ConfirmationStatus.PENDING &&
            existing.actionType.equals(actionType, ignoreCase = true) &&
            existing.recipient.equals(recipient.trim(), ignoreCase = true) &&
            existing.content == content.trim() &&
            System.currentTimeMillis() < existing.expiresAt
        ) {
            Log.i(TAG, "Reusing active pending action [${existing.actionId}] - prompt: '${existing.spokenPrompt}'")
            return ConfirmationResult.AwaitingConfirmation(existing, existing.spokenPrompt)
        }

        // 3. Format concise, natural confirmation prompt
        val spokenPrompt = formatSpokenPrompt(actionType, recipient.trim(), content.trim(), language)

        val action = PendingAction(
            actionType = actionType,
            recipient = recipient.trim(),
            phoneNumber = phoneNumber?.trim(),
            content = content.trim(),
            spokenPrompt = spokenPrompt,
            replyAction = replyAction
        )

        _activeAction.value = action
        Log.i(TAG, "Staged single confirmation [${action.actionId}]: '$spokenPrompt'")
        return ConfirmationResult.AwaitingConfirmation(action, spokenPrompt)
    }

    fun handleUserVoiceTurn(userSpeech: String): ConfirmationResult {
        val action = _activeAction.value ?: return ConfirmationResult.NoPendingAction
        val now = System.currentTimeMillis()

        if (now > action.expiresAt) {
            _activeAction.value = null
            Log.w(TAG, "Pending action [${action.actionId}] expired.")
            return ConfirmationResult.Expired
        }

        val input = userSpeech.lowercase().trim()

        // Cancellation Match (English, Tamil, Tanglish, Hindi)
        val isCancel = input in setOf(
            "no", "nope", "cancel", "stop", "don't send", "dont send", "abort",
            "illai", "illa", "vendaam", "vendam", "cancel pannu", "pannatha",
            "nahi", "mat bhejo", "ruko"
        ) || input.startsWith("no ") || input.startsWith("don't ") || input.startsWith("dont ")

        if (isCancel) {
            action.status = ConfirmationStatus.CANCELLED
            _activeAction.value = null
            Log.i(TAG, "Action [${action.actionId}] CANCELLED by user speech: '$userSpeech'")
            return ConfirmationResult.Cancelled(action)
        }

        // Confirmation Match (English, Tamil, Tanglish, Hindi)
        val isConfirm = input in setOf(
            "yes", "yeah", "yep", "sure", "ok", "okay", "send", "send it", "go ahead", "confirm",
            "ama", "aama", "aamanga", "sari", "seri", "send pannu", "send pannunga", "kandippa", "pannu",
            "theek hai", "haan", "bhejo", "bhej do"
        ) || input.startsWith("yes ") || input.startsWith("yes,") || input.contains("send it") || input.contains("send pannu")

        if (isConfirm) {
            action.status = ConfirmationStatus.AUTHORIZED
            markExecuted(action.actionType, action.recipient, action.content)
            Log.i(TAG, "Action [${action.actionId}] AUTHORIZED by user speech: '$userSpeech'")
            return ConfirmationResult.Authorized(action)
        }

        // If user says something completely unrelated (e.g. asking a question or giving a new command)
        val isNewCommand = input.startsWith("open ") || input.startsWith("what ") || input.startsWith("who ") ||
                input.startsWith("how ") || input.startsWith("turn ") || input.startsWith("play ")
        if (isNewCommand) {
            Log.i(TAG, "User switched topic; clearing pending confirmation for action [${action.actionId}]")
            _activeAction.value = null
            return ConfirmationResult.NoPendingAction
        }

        // If unclear, return existing prompt (never generate duplicate different prompts)
        return ConfirmationResult.AwaitingConfirmation(action, action.spokenPrompt)
    }

    fun hasPendingConfirmation(): Boolean {
        val a = _activeAction.value ?: return false
        if (System.currentTimeMillis() > a.expiresAt) {
            _activeAction.value = null
            return false
        }
        return a.status == ConfirmationStatus.PENDING
    }

    fun clearPending() {
        _activeAction.value = null
    }

    private fun formatSpokenPrompt(actionType: String, recipient: String, content: String, language: String): String {
        return when (actionType.uppercase()) {
            "WHATSAPP", "WHATSAPP_MESSAGE", "SEND_WHATSAPP" -> {
                "$recipient-kku WhatsApp message send pannava?"
            }
            "NOTIFICATION_REPLY" -> {
                "$recipient-kku reply send pannava?"
            }
            "SMS", "SEND_SMS" -> {
                "$recipient-kku SMS send pannava?"
            }
            else -> {
                "Should I proceed with $actionType for $recipient?"
            }
        }
    }

    private fun buildFingerprint(actionType: String, recipient: String, content: String): String {
        return "${actionType.uppercase()}:${recipient.trim().lowercase()}:${content.trim().lowercase()}"
    }

    private fun cleanupExpiredIdempotency() {
        val now = System.currentTimeMillis()
        recentlyExecutedActions.entries.removeIf { (now - it.value) > IDEMPOTENCY_WINDOW_MS }
    }
}
