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
                    put("maxOutputTokens", 1200)
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

    /**
     * Synthesizes speech using Gemini's native neural audio generation.
     * Returns decoded raw 24kHz 16-bit PCM bytes (stripping any WAV container headers).
     */
    suspend fun synthesizeSpeech(
        apiKey: String,
        voiceName: String,
        text: String,
        model: String = "gemini-2.0-flash"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API key is missing"))
        }
        if (text.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Text is blank"))
        }

        try {
            val cleanModel = if (model.startsWith("models/")) model.removePrefix("models/") else model
            val targetModel = when {
                cleanModel.contains("2.0-flash") -> cleanModel
                cleanModel.contains("3.8-flash-tts") -> cleanModel
                else -> "gemini-2.0-flash"
            }
            val liveVoice = com.jarvis.assistant.audio.ZaraVoicePreferenceManager.getGeminiLiveVoiceName(voiceName)
            val url = "$BASE_URL/$targetModel:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Read this text aloud in Tamil (ta-IN) with warm, natural, human conversational emotion. Output only audio:\n$text")
                            })
                        })
                    })
                }
                put("contents", contentsArray)

                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", liveVoice)
                            })
                        })
                    })
                    put("temperature", 0.6)
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
                    Log.e(TAG, "synthesizeSpeech HTTP error ${resp.code}: $body")
                    return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                }

                val jsonResponse = JSONObject(body)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val base64Data = inlineData.optString("data")
                                if (base64Data.isNotBlank()) {
                                    val decoded = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                    // If wrapped in WAV container (RIFF header), strip 44 bytes to obtain clean PCM
                                    val pcmBytes = if (decoded.size > 44 &&
                                        decoded[0] == 'R'.code.toByte() &&
                                        decoded[1] == 'I'.code.toByte() &&
                                        decoded[2] == 'F'.code.toByte() &&
                                        decoded[3] == 'F'.code.toByte()
                                    ) {
                                        decoded.copyOfRange(44, decoded.size)
                                    } else {
                                        decoded
                                    }
                                    return@withContext Result.success(pcmBytes)
                                }
                            }
                        }
                    }
                }
                Result.failure(IOException("No audio content in Gemini response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "synthesizeSpeech exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
