package com.jarvis.assistant.voice

import com.jarvis.assistant.audio.AudioFrequencyData
import com.jarvis.assistant.audio.AudioUtils
import com.jarvis.assistant.audio.realtime.EndOfTurnDetector
import com.jarvis.assistant.audio.realtime.VoiceActivityDetector
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Test Suite for:
 * 1. Human Voice Isolation vs Animal Sounds / Ambient Noise Filtering
 * 2. Super-Fast Response Latency (Reduced Adaptive Silence Thresholds)
 */
class HumanVoiceAndLatencyTest {

    @Test
    fun testHumanVoiceIsolationAcceptsHumanVoices() {
        // Adult male voice (125 Hz fundamental pitch, mid-vocal formants)
        val male = AudioFrequencyData(amplitude = 0.15f, pitchHz = 125f, bass = 0.20f, mid = 0.22f, high = 0.08f)
        assertTrue("Adult male voice must be classified as human voice", AudioUtils.isHumanVoice(male))

        // Adult female voice (210 Hz fundamental pitch, prominent vowel body)
        val female = AudioFrequencyData(amplitude = 0.18f, pitchHz = 210f, bass = 0.15f, mid = 0.25f, high = 0.10f)
        assertTrue("Adult female voice must be classified as human voice", AudioUtils.isHumanVoice(female))

        // Quiet human speech (160 Hz fundamental pitch)
        val quiet = AudioFrequencyData(amplitude = 0.05f, pitchHz = 160f, bass = 0.08f, mid = 0.09f, high = 0.04f)
        assertTrue("Quiet human speech must be classified as human voice", AudioUtils.isHumanVoice(quiet))
    }

    @Test
    fun testHumanVoiceIsolationRejectsAnimalSoundsAndNoise() {
        // Dog barking: High frequency harmonic peaks (> 1400 Hz), high diff energy
        val dogBark = AudioFrequencyData(amplitude = 0.35f, pitchHz = 1450f, bass = 0.10f, mid = 0.12f, high = 0.55f)
        assertFalse("Dog barking must be REJECTED as non-human noise", AudioUtils.isHumanVoice(dogBark))

        // Bird chirping: Extremely high frequency whistle (> 2800 Hz)
        val birdChirp = AudioFrequencyData(amplitude = 0.20f, pitchHz = 2800f, bass = 0.02f, mid = 0.05f, high = 0.60f)
        assertFalse("Bird chirps must be REJECTED as non-human noise", AudioUtils.isHumanVoice(birdChirp))

        // Fan / AC drone: 60Hz hum, flat bass without vocal formants
        val fanDrone = AudioFrequencyData(amplitude = 0.06f, pitchHz = 60f, bass = 0.55f, mid = 0.02f, high = 0.03f)
        assertFalse("Fan drone must be REJECTED as non-human noise", AudioUtils.isHumanVoice(fanDrone))

        // Door slam / Table thump: Low rumble thump (< 55Hz)
        val doorSlam = AudioFrequencyData(amplitude = 0.40f, pitchHz = 50f, bass = 0.70f, mid = 0.03f, high = 0.15f)
        assertFalse("Door slam / thump must be REJECTED as non-human noise", AudioUtils.isHumanVoice(doorSlam))

        // Keyboard clicks / metal clatter: High frequency transient spikes
        val keyClick = AudioFrequencyData(amplitude = 0.12f, pitchHz = 2200f, bass = 0.01f, mid = 0.04f, high = 0.48f)
        assertFalse("Keyboard clicks must be REJECTED as non-human noise", AudioUtils.isHumanVoice(keyClick))
    }

    @Test
    fun testFastResponseSilenceThresholds() {
        // 1. Single-word confirmation ("yes", "aama", "send it"): fast completion <= 200ms
        val shortThreshold = EndOfTurnDetector.getAdaptiveSilenceThreshold("yes", 400L)
        assertEquals(200L, shortThreshold)

        val aamaThreshold = EndOfTurnDetector.getAdaptiveSilenceThreshold("aama", 400L)
        assertEquals(200L, aamaThreshold)

        // 2. Standard sentence: fast completion <= 320ms (reduced from 460ms)
        val standardThreshold = EndOfTurnDetector.getAdaptiveSilenceThreshold("what is the weather today in chennai", 2500L)
        assertEquals(320L, standardThreshold)

        // 3. Short command (< 1s, < 3 words): snappy <= 360ms
        val briefThreshold = EndOfTurnDetector.getAdaptiveSilenceThreshold("open camera", 700L)
        assertEquals(360L, briefThreshold)

        // 4. Open clause with continuation marker ("anal", "appuram", "and"): 600ms headroom
        val continuationThreshold = EndOfTurnDetector.getAdaptiveSilenceThreshold("naan varuven anal", 1800L)
        assertEquals(600L, continuationThreshold)
    }

    @Test
    fun testVoiceActivityDetectorWithNonHumanNoise() {
        val vad = VoiceActivityDetector()

        // Synthetic non-human noise chunk: high frequency / flat
        val noiseChunk = ByteArray(640) { (it % 50).toByte() }
        val analysis = vad.processFrame(noiseChunk, isSpeakerActive = false, hasHeadset = false)

        // Even with audio bytes present, non-human noise must not trigger speech start
        assertFalse("Noise chunk must not trigger speech start", analysis.isSpeechStart)
    }
}
