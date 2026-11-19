package com.aetherx.networking

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class AetherHttpClient(
    private var baseUrl: String = "http://127.0.0.1:8899",
    private val gson: Gson = Gson()
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl.trimEnd('/')
    }

    suspend fun getHealth(): NetworkResult<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext NetworkResult.Error("HTTP Error: ${response.code}", response.code)
                }
                val body = response.body?.string().orEmpty()
                val parsed = gson.fromJson(body, HealthResponse::class.java)
                NetworkResult.Success(parsed)
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network failure: ${e.localizedMessage ?: e.message}", cause = e)
        }
    }

    suspend fun sendChat(request: ChatRequest): NetworkResult<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/v1/chat")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext NetworkResult.Error("HTTP ${response.code}: ${response.message}", response.code)
                }
                val body = response.body?.string().orEmpty()
                val parsed = gson.fromJson(body, ChatResponse::class.java)
                NetworkResult.Success(parsed)
            }
        } catch (e: Exception) {
            NetworkResult.Error("Chat failed: ${e.localizedMessage ?: e.message}", cause = e)
        }
    }

    fun streamChat(request: ChatRequest): Flow<String> = callbackFlow {
        val streamRequest = request.copy(stream = true)
        val jsonBody = gson.toJson(streamRequest)
        val httpRequest = Request.Builder()
            .url("$baseUrl/api/v1/chat/stream")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        val call = client.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    close(IOException("Stream HTTP ${response.code}"))
                    return
                }
                val stream = response.body?.byteStream()
                if (stream == null) {
                    close(IOException("Empty response body"))
                    return
                }

                val reader = BufferedReader(InputStreamReader(stream))
                var line: String?
                try {
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            trySend(data)
                        }
                    }
                    close()
                } catch (e: Exception) {
                    close(e)
                } finally {
                    reader.close()
                }
            }
        })

        awaitClose { call.cancel() }
    }

    suspend fun analyzeVision(request: VisionRequest): NetworkResult<VisionResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/v1/vision/analyze")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext NetworkResult.Error("HTTP ${response.code}: ${response.message}", response.code)
                }
                val body = response.body?.string().orEmpty()
                val parsed = gson.fromJson(body, VisionResponse::class.java)
                NetworkResult.Success(parsed)
            }
        } catch (e: Exception) {
            NetworkResult.Error("Vision analysis failed: ${e.localizedMessage ?: e.message}", cause = e)
        }
    }
}
