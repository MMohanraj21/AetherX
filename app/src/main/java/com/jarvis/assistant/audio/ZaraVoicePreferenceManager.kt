package com.jarvis.assistant.audio

import android.content.Context
import android.util.Log
import com.jarvis.assistant.JarvisApp
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZARA AI — Strict User-Selected Voice Lock Manager
 *
 * Exclusively powered by Gemini Live Native Neural Audio Generation.
 * All legacy Android TextToSpeech and 10-voice custom libraries have been removed.
 *
 * Supported Official Gemini Live Neural Voices:
 * - Female: Aoede (Default), Kore
 * - Male: Puck (Default), Charon, Fenrir
 *
 * Guarantees:
 * - Strict Gender Lock: Female selected = Female only (Aoede/Kore). Male selected = Male only (Puck/Charon/Fenrir).
 * - Zero automatic or accidental voice/gender switching.
 * - Persistent across sessions, reboots, and Gemini reconnects.
 */
object ZaraVoicePreferenceManager {

    private const val TAG = "ZaraVoicePrefMgr"

    enum class VoiceGender {
        FEMALE,
        MALE
    }

    // Official Gemini Live neural voices strictly partitioned by gender
    val FEMALE_VOICES = listOf("Aoede", "Kore")
    val MALE_VOICES = listOf("Puck", "Charon", "Fenrir")

    const val DEFAULT_FEMALE_VOICE = "Aoede" // Warm, natural, melodious female voice
    const val DEFAULT_MALE_VOICE = "Puck"   // Engaging, crisp, natural male voice

    private val isLocked = AtomicBoolean(true)

    fun isFemaleVoice(voiceName: String): Boolean {
        return FEMALE_VOICES.any { it.equals(voiceName.trim(), ignoreCase = true) }
    }

    fun isMaleVoice(voiceName: String): Boolean {
        return MALE_VOICES.any { it.equals(voiceName.trim(), ignoreCase = true) }
    }

    /**
     * Retrieves the strictly persisted voice gender preference.
     * Defaults to FEMALE if unconfigured.
     */
    fun getSelectedGender(context: Context): VoiceGender {
        val prefs = (context.applicationContext as? JarvisApp)?.preferences
        val raw = prefs?.voiceGender ?: "FEMALE"
        return if (raw.equals("MALE", ignoreCase = true)) VoiceGender.MALE else VoiceGender.FEMALE
    }

    /**
     * Sets and locks the user's chosen voice gender and specific voice name.
     */
    fun setSelectedGender(context: Context, gender: VoiceGender, specificVoice: String? = null) {
        val prefs = (context.applicationContext as? JarvisApp)?.preferences ?: return
        val targetVoice = when (gender) {
            VoiceGender.FEMALE -> {
                if (specificVoice != null && isFemaleVoice(specificVoice)) specificVoice else DEFAULT_FEMALE_VOICE
            }
            VoiceGender.MALE -> {
                if (specificVoice != null && isMaleVoice(specificVoice)) specificVoice else DEFAULT_MALE_VOICE
            }
        }
        prefs.voice = targetVoice
        prefs.voiceGender = gender.name
        Log.i(TAG, "Voice preference locked: gender=${gender.name}, voice=$targetVoice")
    }

    /**
     * Resolves the authoritative Gemini Live voice name.
     * GUARANTEE: If FEMALE is selected, NEVER returns a male voice. If MALE is selected, NEVER returns a female voice.
     */
    fun getGeminiVoiceName(context: Context): String {
        val prefs = (context.applicationContext as? JarvisApp)?.preferences
        val gender = getSelectedGender(context)
        val currentVoice = prefs?.voice ?: when (gender) {
            VoiceGender.FEMALE -> DEFAULT_FEMALE_VOICE
            VoiceGender.MALE -> DEFAULT_MALE_VOICE
        }

        return when (gender) {
            VoiceGender.FEMALE -> {
                if (isFemaleVoice(currentVoice)) currentVoice else DEFAULT_FEMALE_VOICE
            }
            VoiceGender.MALE -> {
                if (isMaleVoice(currentVoice)) currentVoice else DEFAULT_MALE_VOICE
            }
        }
    }

    /**
     * Maps voice identifier to Gemini Live prebuilt voice name.
     */
    fun getGeminiLiveVoiceName(voiceId: String): String {
        return when (voiceId.trim().lowercase()) {
            "aoede" -> "Aoede"
            "kore" -> "Kore"
            "puck" -> "Puck"
            "charon" -> "Charon"
            "fenrir" -> "Fenrir"
            else -> if (isFemaleVoice(voiceId)) DEFAULT_FEMALE_VOICE else DEFAULT_MALE_VOICE
        }
    }

    /**
     * Resolves the verified Gemini Live WebSocket API voice identifier for the active session.
     */
    fun getLiveApiVoiceName(context: Context): String {
        return getGeminiVoiceName(context)
    }

    /**
     * Inspects and returns the complete status of the voice selection lock for diagnostics.
     */
    fun getVoiceLockDiagnostics(context: Context): Map<String, Any> {
        val gender = getSelectedGender(context)
        val geminiVoice = getGeminiVoiceName(context)
        val isCompliant = when (gender) {
            VoiceGender.FEMALE -> isFemaleVoice(geminiVoice)
            VoiceGender.MALE -> isMaleVoice(geminiVoice)
        }
        return mapOf(
            "selectedGender" to gender.name,
            "geminiVoice" to geminiVoice,
            "isLockCompliant" to isCompliant,
            "allowedVoices" to if (gender == VoiceGender.FEMALE) FEMALE_VOICES else MALE_VOICES,
            "voiceEngine" to "Gemini Live Native Audio (24kHz PCM)"
        )
    }
}
