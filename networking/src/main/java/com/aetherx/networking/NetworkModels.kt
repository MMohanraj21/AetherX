package com.aetherx.networking

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val message: String,
    val conversation_id: String? = null,
    val provider: String? = "auto",
    val model: String? = null,
    val system_prompt: String? = null,
    val stream: Boolean = false
)

data class ChatResponse(
    val message: String,
    val conversation_id: String,
    val provider: String,
    val model: String,
    val tokens_used: Int = 0,
    val latency_ms: Double = 0.0,
    val audio_base64: String? = null
)

data class VisionRequest(
    val image_base64: String,
    val prompt: String,
    val task_type: String = "general", // fashion, landmark, ocr, general
    val provider: String? = "gemini"
)

data class VisionResponse(
    val result: String,
    val tags: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val confidence: Double = 0.95
)

data class HealthResponse(
    val status: String,
    val version: String,
    val active_provider: String,
    val supported_providers: List<String>
)
