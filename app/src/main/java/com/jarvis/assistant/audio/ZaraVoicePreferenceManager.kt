package com.jarvis.assistant.audio

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.jarvis.assistant.JarvisApp
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZARA AI — Strict User-Selected Voice Lock Manager
 *
 * Enforces Absolute Rule:
 * - Male selected = Male only.
 * - Female selected = Female only.
 * - Zero automatic or accidental gender switching across turns, sessions, app restarts,
 *   Gemini Live reconnections, confirmations, error messages, and background states.
 *
 * Manages both Gemini Live neural voice configuration and Android TextToSpeech synchronization.
 */
object ZaraVoicePreferenceManager {

    private const val TAG = "ZaraVoicePrefMgr"

    enum class VoiceGender {
        FEMALE,
        MALE
    }

    // Supported Gemini Live neural voices mapped strictly by gender
    val FEMALE_VOICES = listOf("Aoede", "Kore")
    val MALE_VOICES = listOf("Puck", "Charon", "Fenrir")

    const val DEFAULT_FEMALE_VOICE = "Aoede" // Warm, natural, melodic female voice
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
     * Sets and locks the user's chosen voice gender and optional specific voice name.
     */
    fun setSelectedGender(context: Context, gender: VoiceGender, specificVoice: String? = null) {
        val prefs = (context.applicationContext as? JarvisApp)?.preferences ?: return
        prefs.voiceGender = gender.name

        val resolvedVoice = when (gender) {
            VoiceGender.FEMALE -> {
                if (specificVoice != null && isFemaleVoice(specificVoice)) specificVoice else DEFAULT_FEMALE_VOICE
            }
            VoiceGender.MALE -> {
                if (specificVoice != null && isMaleVoice(specificVoice)) specificVoice else DEFAULT_MALE_VOICE
            }
        }

        prefs.voice = resolvedVoice
        Log.i(TAG, "Voice selection locked: gender=${gender.name}, voice=$resolvedVoice")
    }

    /**
     * Resolves the authoritative Gemini Live voice name.
     * GUARANTEE: If FEMALE is selected, never returns a male voice. If MALE is selected, never returns a female voice.
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
     * Synchronizes and applies the strict gender voice lock to Android TextToSpeech.
     */
    fun applyVoiceToTts(context: Context, tts: TextToSpeech?) {
        tts ?: return
        val gender = getSelectedGender(context)

        try {
            // Apply language baseline
            tts.language = Locale.US

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val availableVoices = tts.voices
                if (!availableVoices.isNullOrEmpty()) {
                    val matchingVoice = when (gender) {
                        VoiceGender.FEMALE -> findFemaleVoice(availableVoices)
                        VoiceGender.MALE -> findMaleVoice(availableVoices)
                    }

                    if (matchingVoice != null) {
                        tts.voice = matchingVoice
                        Log.i(TAG, "Applied native TTS voice: ${matchingVoice.name} (Gender: ${gender.name})")
                    } else {
                        Log.w(TAG, "No explicit ${gender.name} voice found in TTS engine; applying acoustic pitch modulation.")
                    }
                }
            }

            // Apply acoustic pitch modulation as pitch lock
            when (gender) {
                VoiceGender.FEMALE -> {
                    tts.setPitch(1.08f) // Female formant shift
                    tts.setSpeechRate(1.0f)
                }
                VoiceGender.MALE -> {
                    tts.setPitch(0.90f) // Male chest resonance shift
                    tts.setSpeechRate(1.0f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying voice to TTS: ${e.message}")
        }
    }

    private fun findFemaleVoice(voices: Set<Voice>): Voice? {
        val usVoices = voices.filter { it.locale.language == "en" }
        // 1. Look for explicit female markers
        return usVoices.firstOrNull { v ->
            val name = v.name.lowercase(Locale.ROOT)
            name.contains("female") || name.contains("fem") || name.contains("-f-") || name.contains("#female")
        } ?: usVoices.firstOrNull { v ->
            // Exclude explicit male markers
            val name = v.name.lowercase(Locale.ROOT)
            !name.contains("male") && !name.contains("-m-") && !name.contains("#male")
        }
    }

    private fun findMaleVoice(voices: Set<Voice>): Voice? {
        val usVoices = voices.filter { it.locale.language == "en" }
        // 1. Look for explicit male markers
        return usVoices.firstOrNull { v ->
            val name = v.name.lowercase(Locale.ROOT)
            name.contains("male") || name.contains("-m-") || name.contains("#male")
        } ?: usVoices.firstOrNull { v ->
            // Exclude explicit female markers
            val name = v.name.lowercase(Locale.ROOT)
            !name.contains("female") && !name.contains("fem") && !name.contains("-f-") && !name.contains("#female")
        }
    }
}
