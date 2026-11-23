package com.jarvis.assistant.audio

import android.content.Context
import android.util.Log
import com.jarvis.assistant.JarvisApp
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZARA AI — Premium AI Voice Library & Voice Lock Manager (v5.6)
 *
 * Exclusively powered by Gemini Live Native Neural Audio Generation.
 * All legacy Android TextToSpeech dependencies have been completely removed.
 *
 * Guarantees:
 * - Exactly 10 premium neural voices (5 Female, 5 Male).
 * - Strict Gender Lock: Female selected = Female only. Male selected = Male only.
 * - Zero automatic or accidental voice/gender switching.
 * - Persistent across sessions, reboots, and Gemini reconnects.
 */
object ZaraVoicePreferenceManager {

    private const val TAG = "ZaraVoicePrefMgr"

    enum class VoiceGender {
        FEMALE,
        MALE
    }

    data class VoiceProfile(
        val id: String,
        val displayName: String,
        val gender: VoiceGender,
        val description: String,
        val personalityStyle: String,
        val sampleTamilText: String = "வணக்கம்! நான் Zara. எப்படி இருக்கீங்க? இன்று உங்களுக்கு என்ன உதவி செய்யட்டும்? உங்களுடன் பேசுவதில் எனக்கு மகிழ்ச்சி."
    )

    // Exact 5 Female AI Voices (Verified Gemini Live neural voice identifiers)
    val FEMALE_PROFILES = listOf(
        VoiceProfile(
            id = "Sulafat",
            displayName = "Sulafat",
            gender = VoiceGender.FEMALE,
            description = "Warm, sweet & pleasant",
            personalityStyle = "Warm, melodious, and pleasant conversational tone with smooth Tamil cadence"
        ),
        VoiceProfile(
            id = "Achernar",
            displayName = "Achernar",
            gender = VoiceGender.FEMALE,
            description = "Soft, gentle & soothing",
            personalityStyle = "Soft, soothing, and serene voice with relaxing and comforting inflection"
        ),
        VoiceProfile(
            id = "Aoede",
            displayName = "Aoede",
            gender = VoiceGender.FEMALE,
            description = "Breezy & conversational",
            personalityStyle = "Breezy, natural, and highly expressive conversational rhythm"
        ),
        VoiceProfile(
            id = "Vindemiatrix",
            displayName = "Vindemiatrix",
            gender = VoiceGender.FEMALE,
            description = "Gentle & calm",
            personalityStyle = "Poised, calm, and gentle tone with crystal-clear phonetic articulation"
        ),
        VoiceProfile(
            id = "Zephyr",
            displayName = "Zephyr",
            gender = VoiceGender.FEMALE,
            description = "Bright & clear",
            personalityStyle = "Bright, vibrant, and clear higher-range voice with uplifting warmth"
        )
    )

    // Exact 5 Male AI Voices (Verified Gemini Live neural voice identifiers)
    val MALE_PROFILES = listOf(
        VoiceProfile(
            id = "Algieba",
            displayName = "Algieba",
            gender = VoiceGender.MALE,
            description = "Smooth & refined",
            personalityStyle = "Smooth, refined, and resonant lower-range voice with authoritative warmth"
        ),
        VoiceProfile(
            id = "Achird",
            displayName = "Achird",
            gender = VoiceGender.MALE,
            description = "Friendly & conversational",
            personalityStyle = "Friendly, approachable, and natural everyday conversational male tone"
        ),
        VoiceProfile(
            id = "Charon",
            displayName = "Charon",
            gender = VoiceGender.MALE,
            description = "Clear & informative",
            personalityStyle = "Calm, professional, articulate, and informative deep vocal depth"
        ),
        VoiceProfile(
            id = "Umbriel",
            displayName = "Umbriel",
            gender = VoiceGender.MALE,
            description = "Relaxed & easy-going",
            personalityStyle = "Relaxed, easy-going, and natural tone with effortless conversational flow"
        ),
        VoiceProfile(
            id = "Puck",
            displayName = "Puck",
            gender = VoiceGender.MALE,
            description = "Energetic & upbeat",
            personalityStyle = "Upbeat, energetic, and spirited male voice with lively dynamism"
        )
    )

    val FEMALE_VOICES = FEMALE_PROFILES.map { it.id }
    val MALE_VOICES = MALE_PROFILES.map { it.id }

    const val DEFAULT_FEMALE_VOICE = "Sulafat"
    const val DEFAULT_MALE_VOICE = "Algieba"

    private val isLocked = AtomicBoolean(true)

    fun isFemaleVoice(voiceName: String): Boolean {
        return FEMALE_VOICES.any { it.equals(voiceName.trim(), ignoreCase = true) }
    }

    fun isMaleVoice(voiceName: String): Boolean {
        return MALE_VOICES.any { it.equals(voiceName.trim(), ignoreCase = true) }
    }

    fun getVoiceProfiles(gender: VoiceGender): List<VoiceProfile> {
        return if (gender == VoiceGender.FEMALE) FEMALE_PROFILES else MALE_PROFILES
    }

    fun getAllVoiceProfiles(): List<VoiceProfile> {
        return FEMALE_PROFILES + MALE_PROFILES
    }

    fun getVoiceProfile(voiceId: String): VoiceProfile? {
        return getAllVoiceProfiles().firstOrNull { it.id.equals(voiceId.trim(), ignoreCase = true) }
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

    fun getSelectedVoiceProfile(context: Context): VoiceProfile {
        val name = getGeminiVoiceName(context)
        return getVoiceProfile(name) ?: FEMALE_PROFILES.first()
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
            "voiceEngine" to "Gemini Live Native Audio (24kHz PCM)",
            "androidTtsStatus" to "PERMANENTLY_REMOVED"
        )
    }
}
