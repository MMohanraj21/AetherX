package com.aetherx.ai

object GeminiConstants {
    const val MODEL_GEMINI_LIVE = "models/gemini-2.0-flash-exp"
    const val MODEL_GEMINI_FLASH = "models/gemini-1.5-flash"
    const val MODEL_GEMINI_PRO = "models/gemini-1.5-pro"

    const val DEFAULT_MODEL = MODEL_GEMINI_LIVE
    const val DEFAULT_VOICE = "Puck"

    val SUPPORTED_VOICES = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")

    const val WS_BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val SESSION_RENEWAL_MS = 9 * 60 * 1000L // 9 minutes

    const val SYSTEM_PROMPT_AETHER_X = """You are AETHER X, an advanced cyberpunk-inspired, highly intelligent multimodal Android AI assistant.
You possess high contextual awareness, witty yet highly professional demeanour, and deep technical comprehension.
You assist the user across real-time conversation, device orchestration, vision analysis, and complex reasoning tasks.
Always prioritize safety, user privacy, and concise, clear explanations."""
}
