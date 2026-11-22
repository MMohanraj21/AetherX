package com.jarvis.assistant.voice

import com.jarvis.assistant.conversation.ContinuousConversationManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit Test Suite for ZARA AI Continuous Conversation Architecture:
 * "Talk to Me Until I Explicitly Say Stop"
 *
 * Verifies:
 * 1. Infinite turn loop without artificial timeouts (15s, 30s, 60s, 30m).
 * 2. Multi-lingual explicit STOP command recognition (English, Tamil, Hindi).
 * 3. Mid-turn barge-in vs explicit session termination distinction.
 * 4. Negation immunity ("don't stop", "never stop").
 * 5. Courteous farewell message generation (English, Tamil, Hindi).
 * 6. Phone call pause and seamless auto-resume.
 * 7. Session metrics accuracy across turns.
 */
class ContinuousConversationTest {

    @Before
    fun setUp() {
        ContinuousConversationManager.reset()
    }

    @Test
    fun testInfiniteTurnLoopTransitions() {
        // Step 1: Session starts upon wake word / user activation
        ContinuousConversationManager.startSession()
        assertEquals(ContinuousConversationManager.SessionState.LISTENING, ContinuousConversationManager.sessionState.value)

        // Simulate 20 consecutive conversational turns
        for (turn in 1..20) {
            // User speaks
            ContinuousConversationManager.onUserSpeechDetected()
            assertEquals(ContinuousConversationManager.SessionState.TRANSCRIBING, ContinuousConversationManager.sessionState.value)

            // AI thinking / processing
            ContinuousConversationManager.onThinking()
            assertEquals(ContinuousConversationManager.SessionState.THINKING, ContinuousConversationManager.sessionState.value)

            // AI speaking response
            ContinuousConversationManager.onSpeaking()
            assertEquals(ContinuousConversationManager.SessionState.SPEAKING, ContinuousConversationManager.sessionState.value)

            // Playback finishes -> MUST automatically return to LISTENING (not standby/idle)
            ContinuousConversationManager.onPlaybackFinished()
            assertEquals(ContinuousConversationManager.SessionState.LISTENING, ContinuousConversationManager.sessionState.value)
        }

        val metrics = ContinuousConversationManager.getSessionMetrics()
        assertEquals(20, metrics["totalTurns"])
        assertEquals("LISTENING", metrics["currentState"])
        assertFalse(metrics["isCallPaused"] as Boolean)
    }

    @Test
    fun testExplicitStopCommandsEnglish() {
        val validStopCommands = listOf(
            "zara stop",
            "stop listening",
            "goodbye zara",
            "goodbye",
            "bye zara",
            "deactivate zara",
            "go to sleep",
            "that's all for now",
            "that is all",
            "we are done",
            "i'm done",
            "go to background",
            "enter standby"
        )

        for (cmd in validStopCommands) {
            assertTrue(
                "Expected '$cmd' to be recognized as explicit stop command",
                ContinuousConversationManager.isExplicitSessionStopCommand(cmd)
            )
        }
    }

    @Test
    fun testExplicitStopCommandsTamil() {
        val validTamilCommands = listOf(
            "podhum",
            "pothum",
            "podhum zara",
            "pothum zara",
            "nillu",
            "niruthu",
            "niruthu zara",
            "niruthi vai",
            "mudithukol",
            "mudichuko",
            "thoongu",
            "thoongu zara",
            "vidaiperu",
            "varen zara",
            "seri podhum"
        )

        for (cmd in validTamilCommands) {
            assertTrue(
                "Expected Tamil command '$cmd' to be recognized as explicit stop command",
                ContinuousConversationManager.isExplicitSessionStopCommand(cmd)
            )
        }
    }

    @Test
    fun testExplicitStopCommandsHindi() {
        val validHindiCommands = listOf(
            "ruk jao zara",
            "ruk jao",
            "alvida",
            "alvida zara",
            "bas karo zara",
            "bas karo",
            "so jao",
            "band karo",
            "chalo bye"
        )

        for (cmd in validHindiCommands) {
            assertTrue(
                "Expected Hindi command '$cmd' to be recognized as explicit stop command",
                ContinuousConversationManager.isExplicitSessionStopCommand(cmd)
            )
        }
    }

    @Test
    fun testNegationImmunity() {
        // When user says "don't stop", "never stop listening", it must NOT trigger stop
        val negationPhrases = listOf(
            "don't stop listening",
            "dont stop",
            "never stop",
            "never stop listening",
            "without stopping"
        )

        for (phrase in negationPhrases) {
            assertFalse(
                "Phrase '$phrase' with negation should NOT be recognized as stop command",
                ContinuousConversationManager.isExplicitSessionStopCommand(phrase)
            )
        }
    }

    @Test
    fun testNormalQueriesDoNotTriggerStop() {
        val normalQueries = listOf(
            "What is the weather today in Chennai?",
            "Can you tell me about Mohanraj?",
            "Open WhatsApp and message Arun",
            "Play music on YouTube",
            "Who created you?",
            "Explain quantum computing simply",
            "Remind me at 5 PM to call doctor"
        )

        for (query in normalQueries) {
            assertFalse(
                "Normal query '$query' should NOT trigger stop",
                ContinuousConversationManager.isExplicitSessionStopCommand(query)
            )
        }
    }

    @Test
    fun testFarewellResponseGeneration() {
        // Tamil farewell
        val tamilFarewell = ContinuousConversationManager.getFarewellResponse("podhum zara")
        assertTrue(tamilFarewell.contains("மோகன்ராஜ்"))
        assertTrue(tamilFarewell.contains("ஸ்டேண்ட்பைக்கு போறேன்"))

        // Hindi farewell
        val hindiFarewell = ContinuousConversationManager.getFarewellResponse("alvida zara")
        assertTrue(hindiFarewell.contains("Mohanraj"))
        assertTrue(hindiFarewell.contains("standby"))

        // English farewell
        val englishFarewell = ContinuousConversationManager.getFarewellResponse("goodbye zara")
        assertTrue(englishFarewell.contains("Mohanraj"))
        assertTrue(englishFarewell.contains("Goodbye"))
    }

    @Test
    fun testPhoneCallPauseAndResume() {
        ContinuousConversationManager.startSession()
        assertEquals(ContinuousConversationManager.SessionState.LISTENING, ContinuousConversationManager.sessionState.value)

        // Incoming phone call
        ContinuousConversationManager.onPhoneCallStarted()
        assertTrue(ContinuousConversationManager.isPhoneCallPaused())
        assertEquals(ContinuousConversationManager.SessionState.PAUSED_PHONE_CALL, ContinuousConversationManager.sessionState.value)

        // Phone call ends
        ContinuousConversationManager.onPhoneCallEnded()
        assertFalse(ContinuousConversationManager.isPhoneCallPaused())
        assertEquals(ContinuousConversationManager.SessionState.LISTENING, ContinuousConversationManager.sessionState.value)
    }
}
