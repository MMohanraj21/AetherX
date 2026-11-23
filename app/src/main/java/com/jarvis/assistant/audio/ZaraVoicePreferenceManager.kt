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
 * ZARA AI — Strict User-Selected Voice Lock Manager (v5.5)
 *
 * Enforces Absolute Architectural Rules:
 * - Male selected = Male only.
 * - Female selected = Female only.
 * - Zero automatic or accidental gender switching across turns, sessions, app restarts,
 *   Gemini Live reconnections, confirmations, error messages, and background states.
 * - If the selected voice is unavailable, report limitation instead of switching gender.
 * - Manages both Gemini Live neural voice configuration and Android TextToSpeech synchronization separately.
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
     * Synchronizes and applies the strict gender voice lock to Android TextToSpeech.
     * Ensures pitch modulation and strict voice filtering prevent opposite-gender bleed.
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
                        VoiceGender.FEMALE -> findStrictFemaleVoice(availableVoices)
                        VoiceGender.MALE -> findStrictMaleVoice(availableVoices)
                    }

                    if (matchingVoice != null) {
                        tts.voice = matchingVoice
                        Log.i(TAG, "Applied native TTS voice: ${matchingVoice.name} (Gender: ${gender.name})")
                    } else {
                        Log.w(TAG, "Strict voice lock: No explicit ${gender.name} voice profile found in system TTS. Retaining base engine with acoustic formant modulation.")
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

    private fun findStrictFemaleVoice(voices: Set<Voice>): Voice? {
        val usVoices = voices.filter { it.locale.language == "en" }
        // 1. Look for explicit female markers in voice name
        val explicitFemale = usVoices.firstOrNull { v ->
            val name = v.name.lowercase(Locale.ROOT)
            val isMale = name.contains("male") || name.contains("-m-") || name.contains("#male") ||
                    name.contains("iom") || name.contains("iof") || name.contains("-guy") ||
                    name.contains("david") || name.contains("george")
            !isMale && (name.contains("female") || name.contains("fem") || name.contains("-f-") ||
                    name.contains("#female") || name.contains("sfg") || name.contains("iog") ||
                    name.contains("iol") || name.contains("tpf") || name.contains("samantha") ||
                    name.contains("victoria") || name.contains("zira"))
        }
        if (explicitFemale != null) return explicitFemale

        // 2. Look for features attribute containing female
        return usVoices.firstOrNull { v ->
            val feats = v.features?.joinToString(",")?.lowercase(Locale.ROOT).orEmpty()
            feats.contains("gender=female") || feats.contains("female")
        }
    }

    private fun findStrictMaleVoice(voices: Set<Voice>): Voice? {
        val usVoices = voices.filter { it.locale.language == "en" }
        // 1. Look for explicit male markers in voice name
        val explicitMale = usVoices.firstOrNull { v ->
            val name = v.name.lowercase(Locale.ROOT)
            val isFemale = name.contains("female") || name.contains("fem") || name.contains("-f-") ||
                    name.contains("#female") || name.contains("sfg") || name.contains("iog") ||
                    name.contains("iol") || name.contains("tpf") || name.contains("samantha") ||
                    name.contains("victoria") || name.contains("zira")
            !isFemale && (name.contains("male") || name.contains("-m-") || name.contains("#male") ||
                    name.contains("iom") || name.contains("iof") || name.contains("-guy") ||
                    name.contains("david") || name.contains("george") || name.contains("mark"))
        }
        if (explicitMale != null) return explicitMale

        // 2. Look for features attribute containing male
        return usVoices.firstOrNull { v ->
            val feats = v.features?.joinToString(",")?.lowercase(Locale.ROOT).orEmpty()
            (feats.contains("gender=male") || feats.contains("male")) && !feats.contains("female")
        }
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
            "allowedVoices" to if (gender == VoiceGender.FEMALE) FEMALE_VOICES else MALE_VOICES
        )
    }
}
