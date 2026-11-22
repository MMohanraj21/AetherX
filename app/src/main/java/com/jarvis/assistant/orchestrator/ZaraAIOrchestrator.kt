package com.jarvis.assistant.orchestrator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jarvis.assistant.automation.command.CommandParser
import com.jarvis.assistant.automation.command.JarvisCommand
import com.jarvis.assistant.automation.modes.SmartAutomationEngine
import com.jarvis.assistant.automation.phone.PhoneControlManager
import com.jarvis.assistant.conversation.ConversationalReasoningEngine
import com.jarvis.assistant.emergency.EmergencyAssistant
import com.jarvis.assistant.media.UniversalMediaController
import com.jarvis.assistant.notification.NotificationContextProvider
import com.jarvis.assistant.notification.NotificationEventManager
import com.jarvis.assistant.offline.OfflineAIEngine
import com.jarvis.assistant.offline.OfflineIntentClassifier
import com.jarvis.assistant.security.IntelligentConfirmationManager
import com.jarvis.assistant.security.MessageExecutionGate
import com.jarvis.assistant.telecom.CallAssistantManager
import com.jarvis.assistant.util.ActivityLauncherHelper
import java.net.URLEncoder
import java.util.Locale

/**
 * Unified central intelligence orchestrator coordinating device domains, continuous conversation context,
 * notifications, telecom actions, conversational reasoning, and high-level autonomous tasks.
 *
 * Implements Risk-Based Single Confirmation Policy:
 * - Category A: Zero confirmation (Open apps, YouTube, Media, Flashlight, Time, Volume)
 * - Category B: Single confirmation (WhatsApp, Notification reply, SMS) -> Asks once, executes on YES.
 * - Category C: Strong confirmation (Sensitive / destructive operations)
 */
class ZaraAIOrchestrator(private val context: Context) {

    companion object {
        private const val TAG = "ZaraAIOrchestrator"

        @Volatile
        private var instance: ZaraAIOrchestrator? = null

        fun getInstance(context: Context): ZaraAIOrchestrator {
            return instance ?: synchronized(this) {
                instance ?: ZaraAIOrchestrator(context.applicationContext).also { instance = it }
            }
        }
    }

    private val callManager = CallAssistantManager.getInstance(context)
    private val offlineEngine = OfflineAIEngine(context)
    private val mediaController = UniversalMediaController(context)
    private val automationEngine = SmartAutomationEngine(context)
    private val emergencyAssistant = EmergencyAssistant(context)
    private val reasoningEngine = ConversationalReasoningEngine.getInstance(context)
    private val phoneControlManager = PhoneControlManager(context)

    /**
     * Attempts to resolve and execute intent through specialized local domains first.
     * Returns true if handled directly; returns false if query should stream to Gemini Live.
     */
    fun processVoiceUtterance(utterance: String, onVocalFeedback: (String) -> Unit): Boolean {
        // Priority 1: Strict Single Confirmation Gate (Category B Actions)
        if (MessageExecutionGate.hasPendingConfirmation()) {
            val gateResult = MessageExecutionGate.handleUserVoiceTurn(utterance)
            return when (gateResult) {
                is MessageExecutionGate.GateResult.Authorized -> {
                    val draft = gateResult.draft
                    when (draft.appType) {
                        "WHATSAPP" -> {
                            com.jarvis.assistant.util.WhatsAppMessenger.sendMessage(context, draft.recipient, draft.exactContent, 1, confirmed = true)
                            onVocalFeedback("Message sent successfully.")
                        }
                        "NOTIFICATION_REPLY" -> {
                            val notifAction = (draft.replyAction as? com.jarvis.assistant.notification.NotificationReplyManager.ReplyableAction)
                            if (notifAction != null) {
                                val ok = com.jarvis.assistant.notification.NotificationReplyManager.sendReply(context, notifAction, draft.exactContent)
                                onVocalFeedback(if (ok) "Message sent successfully." else "Could not submit reply.")
                            }
                        }
                    }
                    true
                }
                is MessageExecutionGate.GateResult.Cancelled -> {
                    onVocalFeedback("Okay, message cancelled.")
                    true
                }
                is MessageExecutionGate.GateResult.Expired -> {
                    onVocalFeedback("Confirmation timed out. The message was not sent.")
                    true
                }
                is MessageExecutionGate.GateResult.AwaitingConfirmation -> {
                    onVocalFeedback(gateResult.spokenPrompt)
                    true
                }
                else -> false
            }
        }

        // Priority 2: Category A Direct Execution (Zero Confirmation Needed)
        // High-speed local execution (< 50ms): Open Instagram, Open ChatGPT, Open YouTube, etc.
        val parsed = CommandParser.parse(utterance)
        if (parsed.command != null && parsed.confidence >= 0.90f) {
            when (val cmd = parsed.command) {
                is JarvisCommand.OpenApp -> {
                    val feedback = phoneControlManager.openApp(cmd.packageName)
                    onVocalFeedback(feedback)
                    return true
                }
                is JarvisCommand.PlayYouTube -> {
                    val q = cmd.query
                    val uri = Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))
                    val ytIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.youtube")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val launched = ActivityLauncherHelper.startActivitySafely(context, ytIntent)
                    if (!launched) {
                        phoneControlManager.openUrl("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))
                    }
                    onVocalFeedback("Playing $q on YouTube.")
                    return true
                }
                else -> {}
            }
        }

        // Priority 3: Category B Staging for Explicit WhatsApp Messages
        // Example: "Send a WhatsApp message to Rahul saying I will come tomorrow"
        if (tryParseAndStageWhatsAppMessage(utterance, onVocalFeedback)) {
            return true
        }

        // Priority 4: Incoming Call & Notification priority
        val hasCall = callManager.hasActiveRingingCall()
        val hasNotif = NotificationEventManager.hasPendingReply()

        val routed = IntentRouter.route(utterance, hasCall, hasNotif)
        Log.i(TAG, "Query '$utterance' routed to domain: ${routed.domain}")

        when (routed.domain) {
            IntentRouter.TargetDomain.TELECOM_CALL -> {
                return callManager.handleCallVoiceCommand(utterance, onVocalFeedback)
            }
            IntentRouter.TargetDomain.NOTIFICATION_REPLY -> {
                return NotificationEventManager.handleReplyTurn(context, utterance, onVocalFeedback)
            }
            IntentRouter.TargetDomain.NOTIFICATION_QUERY -> {
                val voiceResponse = NotificationContextProvider.formatVoiceResponse(context, utterance)
                onVocalFeedback(voiceResponse)
                return true
            }
            IntentRouter.TargetDomain.MEDIA_CONTROL -> {
                return handleMediaVoiceCommand(utterance, onVocalFeedback)
            }
            IntentRouter.TargetDomain.AUTOMATION_ROUTINE -> {
                return handleAutomationVoiceCommand(utterance, onVocalFeedback)
            }
            IntentRouter.TargetDomain.EMERGENCY_SOS -> {
                emergencyAssistant.handleSosTrigger(
                    onCountdownTick = { sec -> onVocalFeedback("SOS triggered. $sec seconds to cancel.") },
                    onConfirmed = { _ -> onVocalFeedback("SOS Alert Sent! Location shared.") },
                    onCancelled = { onVocalFeedback("SOS Cancelled.") }
                )
                return true
            }
            IntentRouter.TargetDomain.OFFLINE_DEVICE -> {
                val classified = OfflineIntentClassifier.classify(utterance)
                if (classified != null) {
                    return offlineEngine.executeLocally(classified, onVocalFeedback)
                }
            }
            IntentRouter.TargetDomain.GENERATIVE_AI -> {}
        }

        // Priority 5: Conversational Reasoning Engine (Clarification & Multi-turn evaluation)
        val reasoning = reasoningEngine.evaluate(utterance)
        when (reasoning) {
            is ConversationalReasoningEngine.ReasoningResult.SpeakClarification -> {
                onVocalFeedback(reasoning.question)
                return true
            }
            is ConversationalReasoningEngine.ReasoningResult.SpeakConfirmation -> {
                onVocalFeedback(reasoning.prompt)
                return true
            }
            is ConversationalReasoningEngine.ReasoningResult.HandledLocally -> {
                onVocalFeedback(reasoning.feedback)
                return true
            }
            is ConversationalReasoningEngine.ReasoningResult.ExecuteDirectly -> {
                return false
            }
            is ConversationalReasoningEngine.ReasoningResult.DelegateToGemini -> {
                return false
            }
        }
    }

    private fun tryParseAndStageWhatsAppMessage(utterance: String, onFeedback: (String) -> Unit): Boolean {
        val clean = utterance.trim()
        val lower = clean.lowercase(Locale.ROOT)

        val isWhatsAppRequest = lower.contains("whatsapp") &&
                (lower.contains("message") || lower.contains("send") || lower.contains("anupu") || lower.contains("solli") || lower.contains("podu"))
        if (!isWhatsAppRequest) return false

        // Pattern 1: English: "Send a WhatsApp message to Rahul saying I will come tomorrow"
        val pattern1 = Regex("(?:send\\s+(?:a\\s+)?)?whatsapp(?:\\s+message)?\\s+to\\s+([a-zA-Z0-9\\s]+?)\\s+(?:saying|that)\\s+(.+)", RegexOption.IGNORE_CASE)
        val match1 = pattern1.find(clean)
        if (match1 != null) {
            val recipient = match1.groupValues[1].trim()
            val content = match1.groupValues[2].trim()
            if (recipient.isNotBlank() && content.isNotBlank()) {
                val staging = MessageExecutionGate.stageDraft(
                    appType = "WHATSAPP",
                    recipient = recipient,
                    exactContent = content
                )
                onFeedback(staging.spokenPrompt)
                return true
            }
        }

        // Pattern 2: Tanglish: "Rahul-kku WhatsApp message send pannu I will come tomorrow"
        val pattern2 = Regex("([a-zA-Z0-9]+)(?:-kku|-ku|\\s+kku|\\s+ku)\\s+whatsapp(?:\\s+message)?(?:\\s+send\\s+pannu)?\\s+(?:solli\\s+)?(.+)", RegexOption.IGNORE_CASE)
        val match2 = pattern2.find(clean)
        if (match2 != null) {
            val recipient = match2.groupValues[1].trim()
            val content = match2.groupValues[2].trim()
            if (recipient.isNotBlank() && content.isNotBlank()) {
                val staging = MessageExecutionGate.stageDraft(
                    appType = "WHATSAPP",
                    recipient = recipient,
                    exactContent = content
                )
                onFeedback(staging.spokenPrompt)
                return true
            }
        }

        return false
    }

    private fun handleMediaVoiceCommand(query: String, onFeedback: (String) -> Unit): Boolean {
        val lower = query.lowercase()
        return when {
            lower.contains("pause") -> {
                val ok = mediaController.pause()
                onFeedback(if (ok) "Music paused." else "No media player active.")
                true
            }
            lower.contains("play") || lower.contains("resume") -> {
                val ok = mediaController.play()
                onFeedback(if (ok) "Playing music." else "No media player active.")
                true
            }
            lower.contains("next") -> {
                mediaController.next()
                onFeedback("Skipping to next track.")
                true
            }
            lower.contains("previous") -> {
                mediaController.previous()
                onFeedback("Returning to previous track.")
                true
            }
            lower.contains("current") || lower.contains("track") -> {
                onFeedback(mediaController.getCurrentTrackInfo())
                true
            }
            else -> false
        }
    }

    private fun handleAutomationVoiceCommand(query: String, onFeedback: (String) -> Unit): Boolean {
        val lower = query.lowercase()
        val mode = when {
            lower.contains("morning") -> SmartAutomationEngine.Mode.MORNING
            lower.contains("night") -> SmartAutomationEngine.Mode.NIGHT
            lower.contains("work") -> SmartAutomationEngine.Mode.WORK
            lower.contains("study") -> SmartAutomationEngine.Mode.STUDY
            lower.contains("driving") -> SmartAutomationEngine.Mode.DRIVING
            lower.contains("travel") -> SmartAutomationEngine.Mode.TRAVEL
            lower.contains("meeting") -> SmartAutomationEngine.Mode.MEETING
            else -> null
        }
        return if (mode != null) {
            automationEngine.applyMode(mode, onFeedback)
            true
        } else {
            false
        }
    }
}
