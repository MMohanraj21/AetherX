package com.jarvis.assistant.translator

import android.content.Context
import java.util.Locale

/**
 * Handles bi-directional live translation between English, Tamil, Hindi, and Kannada.
 */
class LiveTranslatorEngine(private val context: Context) {

    enum class SupportedLanguage(val code: String, val displayName: String, val locale: Locale) {
        ENGLISH("en", "English", Locale.US),
        TAMIL("ta", "Tamil", Locale("ta", "IN")),
        HINDI("hi", "Hindi", Locale("hi", "IN")),
        KANNADA("kn", "Kannada", Locale("kn", "IN"))
    }

    private var sourceLang = SupportedLanguage.ENGLISH
    private var targetLang = SupportedLanguage.TAMIL

    fun setLanguages(source: SupportedLanguage, target: SupportedLanguage) {
        sourceLang = source
        targetLang = target
    }

    /**
     * Translates common conversational and assistant phrases.
     */
    fun translateQuickPhrase(text: String, target: SupportedLanguage): String {
        val lower = text.lowercase().trim()
        return when (target) {
            SupportedLanguage.TAMIL -> when {
                lower.contains("hello") || lower.contains("hi") -> "வணக்கம்"
                lower.contains("how are you") -> "நீங்கள் எப்படி இருக்கிறீர்கள்?"
                lower.contains("thank you") -> "நன்றி"
                lower.contains("good morning") -> "காலை வணக்கம்"
                lower.contains("yes") -> "ஆம்"
                lower.contains("no") -> "இல்லை"
                else -> text
            }
            SupportedLanguage.HINDI -> when {
                lower.contains("hello") || lower.contains("hi") -> "नमस्ते"
                lower.contains("how are you") -> "आप कैसे हैं?"
                lower.contains("thank you") -> "धन्यवाद"
                lower.contains("good morning") -> "सुप्रभात"
                lower.contains("yes") -> "हाँ"
                lower.contains("no") -> "नहीं"
                else -> text
            }
            SupportedLanguage.KANNADA -> when {
                lower.contains("hello") || lower.contains("hi") -> "ನಮಸ್ಕಾರ"
                lower.contains("how are you") -> "ನೀವು ಹೇಗಿದ್ದೀರಿ?"
                lower.contains("thank you") -> "ಧನ್ಯವಾದಗಳು"
                else -> text
            }
            SupportedLanguage.ENGLISH -> when {
                lower.contains("வணக்கம்") || lower.contains("नमस्ते") || lower.contains("ನಮಸ್ಕಾರ") -> "Hello"
                lower.contains("நன்றி") || lower.contains("धन्यवाद") -> "Thank you"
                else -> text
            }
        }
    }
}
