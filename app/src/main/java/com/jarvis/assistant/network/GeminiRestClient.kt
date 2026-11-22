package com.jarvis.assistant.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * High-reliability REST client for Gemini generateContent API.
 * Used as an ultra-fast fallback whenever Gemini Live WebSocket encounters timeouts,
 * connection drops, or model errors, guaranteeing Zara always generates a response.
 */
object GeminiRestClient {
    private const val TAG = "GeminiRestClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun generateContent(
        apiKey: String,
        prompt: String,
        systemInstruction: String? = null,
        model: String = "gemini-2.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API key is missing"))
        }
        if (prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt is empty"))
        }

        try {
            val cleanModel = if (model.startsWith("models/")) model.removePrefix("models/") else model
            val targetModel = when (cleanModel) {
                "gemini-3.8-live", "gemini-3.1-flash-live-preview" -> "gemini-2.5-flash"
                else -> cleanModel.ifBlank { "gemini-2.5-flash" }
            }
            val url = "$BASE_URL/$targetModel:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)

                if (!systemInstruction.isNullOrBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemInstruction)
                            })
                        })
                    })
                }

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 500)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Gemini REST error HTTP ${resp.code}: $body")
                    return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                }

                val jsonResponse = JSONObject(body)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "").trim()
                        if (text.isNotBlank()) {
                            return@withContext Result.success(text)
                        }
                    }
                }
                Result.failure(IOException("Empty response from Gemini REST API"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateContent exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
