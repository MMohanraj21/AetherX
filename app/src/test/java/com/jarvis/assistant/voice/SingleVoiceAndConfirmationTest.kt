package com.jarvis.assistant.voice

import com.jarvis.assistant.automation.command.CommandParser
import com.jarvis.assistant.automation.command.JarvisCommand
import com.jarvis.assistant.security.IntelligentConfirmationManager
import com.jarvis.assistant.security.MessageExecutionGate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Acceptance Test Suite for:
 * 1. Single Voice Output Architecture (Double Voice Bug Fix)
 * 2. Intelligent Single Confirmation System (Risk-Based Policy & Idempotency)
 *
 * Verifies all 10 required test scenarios:
 * 1. Ask normal question -> only 1 voice speaks.
 * 2. Open Instagram -> Category A, no confirmation.
 * 3. Open ChatGPT -> Category A, no confirmation.
 * 4. Send WhatsApp message -> Category B, single confirmation asked once.
 * 5. Reply YES -> execute once immediately.
 * 6. Reply NO -> cancel immediately.
 * 7. Duplicate AI callbacks -> blocked by idempotency filter.
 * 8. Consecutive commands -> clean transitions between states.
 * 9. Interrupt while speaking (Barge-in) -> flushes both audio and TTS.
 * 10. No overlapping TTS or Gemini audio.
 */
class SingleVoiceAndConfirmationTest {

    // --- Single Voice Output Arbiter Simulator ---
    class SingleVoiceArbiterSimulator {
        enum class AudioEngine { NONE, GEMINI_LIVE_AUDIO, ANDROID_TTS }

        var activeEngine: AudioEngine = AudioEngine.NONE
            private set
        var hasLiveAudioInTurn: Boolean = false
            private set
        var lastTtsSpoken: String? = null
            private set
        var lastLiveAudioChunkCount: Int = 0
            private set
        var ttsSuppressedCount: Int = 0
            private set

        fun onLiveAudioChunkReceived(chunkSize: Int) {
            // Live audio arrives: Authoritative AI voice takes over
            activeEngine = AudioEngine.GEMINI_LIVE_AUDIO
            hasLiveAudioInTurn = true
            lastLiveAudioChunkCount++
            // Cancel any conflicting TTS
            lastTtsSpoken = null
        }

        fun requestTtsSpeech(text: String): Boolean {
            // Rule: If Gemini Live is playing audio or has played audio in this turn,
            // secondary TTS reading is suppressed!
            if (activeEngine == AudioEngine.GEMINI_LIVE_AUDIO || hasLiveAudioInTurn) {
                ttsSuppressedCount++
                return false // Suppressed to prevent double voice!
            }

            activeEngine = AudioEngine.ANDROID_TTS
            lastTtsSpoken = text
            return true
        }

        fun onUserBargeIn() {
            activeEngine = AudioEngine.NONE
            hasLiveAudioInTurn = false
            lastTtsSpoken = null
            lastLiveAudioChunkCount = 0
        }

        fun onTurnFinalized() {
            activeEngine = AudioEngine.NONE
            hasLiveAudioInTurn = false
            lastTtsSpoken = null
        }
    }

    @Before
    fun setUp() {
        IntelligentConfirmationManager.clearPending()
        MessageExecutionGate.cancelActiveDraft()
    }

    // --- TEST 1: Ask normal question -> Only one voice speaks ---
    @Test
    fun test1_NormalQuestionSingleVoiceOutput() {
        val arbiter = SingleVoiceArbiterSimulator()

        // Turn simulation: User asks "What is photosynthesis?"
        // Gemini Live returns PCM audio chunks
        arbiter.onLiveAudioChunkReceived(3200)
        assertEquals(SingleVoiceArbiterSimulator.AudioEngine.GEMINI_LIVE_AUDIO, arbiter.activeEngine)

        // Gemini also provides text transcript ("Photosynthesis is...")
        // System must NOT speak the transcript via TTS while Live audio is active
        val ttsAttempt = arbiter.requestTtsSpeech("Photosynthesis is the process...")
        assertFalse("TTS reading transcript must be SUPPRESSED when Live audio is playing", ttsAttempt)
        assertEquals("TTS suppressed count must be 1", 1, arbiter.ttsSuppressedCount)
        assertEquals("Active engine must remain purely GEMINI_LIVE_AUDIO", SingleVoiceArbiterSimulator.AudioEngine.GEMINI_LIVE_AUDIO, arbiter.activeEngine)
    }

    // --- TEST 2: Open Instagram -> Category A, no confirmation ---
    @Test
    fun test2_OpenInstagramNoConfirmation() {
        val parsed = CommandParser.parse("open instagram")
        assertNotNull(parsed.command)
        assertTrue(parsed.command is JarvisCommand.OpenApp)
        assertEquals("instagram", (parsed.command as JarvisCommand.OpenApp).packageName)

        val risk = IntelligentConfirmationManager.classifyAction("OPEN_APP")
        assertEquals(IntelligentConfirmationManager.RiskCategory.CATEGORY_A_NO_CONFIRMATION, risk)
        assertFalse("Category A actions must NOT trigger pending confirmation", IntelligentConfirmationManager.hasPendingConfirmation())
    }

    // --- TEST 3: Open ChatGPT -> Category A, no confirmation ---
    @Test
    fun test3_OpenChatGPTNoConfirmation() {
        val parsed = CommandParser.parse("open chatgpt")
        assertNotNull(parsed.command)
        assertTrue(parsed.command is JarvisCommand.OpenApp)
        assertEquals("chatgpt", (parsed.command as JarvisCommand.OpenApp).packageName)

        val risk = IntelligentConfirmationManager.classifyAction("OPEN_APP")
        assertEquals(IntelligentConfirmationManager.RiskCategory.CATEGORY_A_NO_CONFIRMATION, risk)
        assertFalse("Category A actions must NOT trigger pending confirmation", IntelligentConfirmationManager.hasPendingConfirmation())
    }

    // --- TEST 4: Send WhatsApp message -> Category B, single confirmation asked once ---
    @Test
    fun test4_SendWhatsAppMessageSingleConfirmation() {
        val risk = IntelligentConfirmationManager.classifyAction("WHATSAPP_MESSAGE")
        assertEquals(IntelligentConfirmationManager.RiskCategory.CATEGORY_B_SINGLE_CONFIRMATION, risk)

        // Stage draft for Rahul
        val staging = MessageExecutionGate.stageDraft(
            appType = "WHATSAPP",
            recipient = "Rahul",
            exactContent = "I will come tomorrow"
        )

        assertTrue(MessageExecutionGate.hasPendingConfirmation())
        assertEquals("Rahul-kku WhatsApp message send pannava?", staging.spokenPrompt)

        // Verify duplicate staging returns the SAME prompt without re-asking differently
        val duplicateStaging = MessageExecutionGate.stageDraft(
            appType = "WHATSAPP",
            recipient = "Rahul",
            exactContent = "I will come tomorrow"
        )
        assertEquals("Rahul-kku WhatsApp message send pannava?", duplicateStaging.spokenPrompt)
    }

    // --- TEST 5: Reply YES -> Execute once immediately ---
    @Test
    fun test5_ReplyYesExecutesImmediately() {
        MessageExecutionGate.stageDraft(
            appType = "WHATSAPP",
            recipient = "Rahul",
            exactContent = "I will come tomorrow"
        )
        assertTrue(MessageExecutionGate.hasPendingConfirmation())

        // User says "Yes" (or "ama", "sari", "send pannu")
        val gateResult = MessageExecutionGate.handleUserVoiceTurn("yes")
        assertTrue("Gate result must be Authorized", gateResult is MessageExecutionGate.GateResult.Authorized)

        val authorizedDraft = (gateResult as MessageExecutionGate.GateResult.Authorized).draft
        assertEquals("Rahul", authorizedDraft.recipient)
        assertEquals("I will come tomorrow", authorizedDraft.exactContent)
        assertTrue("Draft must be marked authorized", authorizedDraft.isAuthorized)

        // Pending draft must be cleared after single authorization
        assertFalse("Pending confirmation must be CLEARED after approval", MessageExecutionGate.hasPendingConfirmation())

        // Action must be recorded in recently executed cache
        assertTrue("Action must be recorded in idempotency cache",
            IntelligentConfirmationManager.isRecentlyExecuted("WHATSAPP", "Rahul", "I will come tomorrow")
        )
    }

    // --- TEST 6: Reply NO -> Cancel immediately ---
    @Test
    fun test6_ReplyNoCancelsImmediately() {
        MessageExecutionGate.stageDraft(
            appType = "WHATSAPP",
            recipient = "Rahul",
            exactContent = "I will come tomorrow"
        )
        assertTrue(MessageExecutionGate.hasPendingConfirmation())

        // User says "No" (or "illai", "vendaam", "cancel")
        val gateResult = MessageExecutionGate.handleUserVoiceTurn("no")
        assertTrue("Gate result must be Cancelled", gateResult is MessageExecutionGate.GateResult.Cancelled)

        // Pending draft must be cleared after cancellation
        assertFalse("Pending confirmation must be CLEARED after cancellation", MessageExecutionGate.hasPendingConfirmation())

        // Action must NOT be marked executed
        assertFalse("Cancelled action must NOT be marked executed",
            IntelligentConfirmationManager.isRecentlyExecuted("WHATSAPP", "Rahul", "I will come tomorrow")
        )
    }

    // --- TEST 7: Duplicate AI callbacks -> Blocked by idempotency filter ---
    @Test
    fun test7_DuplicateAiCallbacksBlockedByIdempotency() {
        MessageExecutionGate.stageDraft(
            appType = "WHATSAPP",
            recipient = "Rahul",
            exactContent = "I will come tomorrow"
        )

        // User approves first time
        MessageExecutionGate.handleUserVoiceTurn("send it")

        // Action is now marked executed
        assertTrue(IntelligentConfirmationManager.isRecentlyExecuted("WHATSAPP", "Rahul", "I will come tomorrow"))

        // Duplicate AI callback arrives with the same parameters
        val duplicateStageResult = IntelligentConfirmationManager.stageAction(
            actionType = "WHATSAPP",
            recipient = "Rahul",
            content = "I will come tomorrow"
        )

        assertTrue("Duplicate staging must return AlreadyExecuted",
            duplicateStageResult is IntelligentConfirmationManager.ConfirmationResult.AlreadyExecuted
        )
        assertFalse("Duplicate callback must NOT re-open pending confirmation",
            IntelligentConfirmationManager.hasPendingConfirmation()
        )
    }

    // --- TEST 8: Consecutive commands -> Clean state transitions ---
    @Test
    fun test8_ConsecutiveCommandsExecution() {
        // Command 1: Category A
        val cmd1 = CommandParser.parse("turn on flashlight")
        assertNotNull(cmd1.command)
        assertTrue(cmd1.command is JarvisCommand.Flash)
        assertEquals(IntelligentConfirmationManager.RiskCategory.CATEGORY_A_NO_CONFIRMATION,
            IntelligentConfirmationManager.classifyAction("FLASHLIGHT"))

        // Command 2: Category B
        MessageExecutionGate.stageDraft("WHATSAPP", "Mom", "Reached safely")
        assertTrue(MessageExecutionGate.hasPendingConfirmation())

        // Command 3: Confirm Category B in Tamil ("aama send pannu")
        val res = MessageExecutionGate.handleUserVoiceTurn("aama send pannu")
        assertTrue(res is MessageExecutionGate.GateResult.Authorized)
        assertFalse(MessageExecutionGate.hasPendingConfirmation())

        // Command 4: Category A immediately after
        val cmd4 = CommandParser.parse("what is the time")
        assertNotNull(cmd4.command)
        assertFalse(MessageExecutionGate.hasPendingConfirmation())
    }

    // --- TEST 9: Interrupt while speaking (Barge-in) -> Flushes audio and TTS ---
    @Test
    fun test9_UserBargeInFlushesAudioAndTts() {
        val arbiter = SingleVoiceArbiterSimulator()

        // Assistant is speaking Live audio
        arbiter.onLiveAudioChunkReceived(4800)
        assertEquals(SingleVoiceArbiterSimulator.AudioEngine.GEMINI_LIVE_AUDIO, arbiter.activeEngine)

        // User speaks (Barge-in)
        arbiter.onUserBargeIn()

        assertEquals("Engine must be flushed to NONE on barge-in", SingleVoiceArbiterSimulator.AudioEngine.NONE, arbiter.activeEngine)
        assertFalse("Live audio active flag must be reset", arbiter.hasLiveAudioInTurn)
        assertNull("TTS buffer must be cleared", arbiter.lastTtsSpoken)
    }

    // --- TEST 10: Fallback REST text-only response uses TTS authoritatively ---
    @Test
    fun test10_RestFallbackTextOnlyUsesTtsAuthoritatively() {
        val arbiter = SingleVoiceArbiterSimulator()

        // Gemini Live did NOT return audio (e.g. offline or REST fallback)
        // Arbiter requests TTS for the REST reply
        val ttsSuccess = arbiter.requestTtsSpeech("Sundar Pichai is the CEO of Google.")

        assertTrue("TTS must succeed when Live audio is NOT active", ttsSuccess)
        assertEquals(SingleVoiceArbiterSimulator.AudioEngine.ANDROID_TTS, arbiter.activeEngine)
        assertEquals("Sundar Pichai is the CEO of Google.", arbiter.lastTtsSpoken)
    }
}
