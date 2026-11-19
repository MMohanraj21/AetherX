package com.aetherx.ai

import com.aetherx.networking.AetherHttpClient
import com.aetherx.networking.ChatRequest
import com.aetherx.networking.ChatResponse
import com.aetherx.networking.NetworkResult
import com.aetherx.security.KeyStoreEncryption
import kotlinx.coroutines.flow.Flow

class AiProviderManager(
    private val httpClient: AetherHttpClient
) {
    enum class Provider(val value: String) {
        AUTO("auto"),
        GEMINI("gemini"),
        OPENAI("openai"),
        LOCAL_BACKEND("local")
    }

    private var activeProvider = Provider.AUTO
    private var customApiKey: String? = null

    fun setProvider(provider: Provider) {
        activeProvider = provider
    }

    fun setApiKey(key: String) {
        customApiKey = KeyStoreEncryption.encrypt(key)
    }

    fun getDecryptedApiKey(): String {
        val encrypted = customApiKey ?: return ""
        return KeyStoreEncryption.decrypt(encrypted)
    }

    suspend fun processChat(
        message: String,
        conversationId: String? = null,
        systemPrompt: String? = null
    ): NetworkResult<ChatResponse> {
        val request = ChatRequest(
            message = message,
            conversation_id = conversationId,
            provider = activeProvider.value,
            system_prompt = systemPrompt
        )
        return httpClient.sendChat(request)
    }

    fun streamChat(
        message: String,
        conversationId: String? = null,
        systemPrompt: String? = null
    ): Flow<String> {
        val request = ChatRequest(
            message = message,
            conversation_id = conversationId,
            provider = activeProvider.value,
            system_prompt = systemPrompt,
            stream = true
        )
        return httpClient.streamChat(request)
    }
}
