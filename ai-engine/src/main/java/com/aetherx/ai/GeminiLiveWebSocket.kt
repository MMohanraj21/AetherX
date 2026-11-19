package com.aetherx.ai

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveWebSocket(
    private val apiKey: String,
    private val model: String = GeminiConstants.DEFAULT_MODEL,
    private val voiceName: String = GeminiConstants.DEFAULT_VOICE,
    private val systemPrompt: String = GeminiConstants.SYSTEM_PROMPT_AETHER_X,
    private val listener: Listener
) {
    interface Listener {
        fun onConnectionStateChanged(status: String)
        fun onAudioDataReceived(pcmData: ByteArray)
        fun onAssistantTextReceived(textChunk: String)
        fun onInterrupted()
        fun onTurnCompleted()
        fun onError(message: String, isFatal: Boolean = false)
        fun onToolCall(callId: String, name: String, args: Map<String, Any>?)
    }

    companion object {
        private const val TAG = "AetherGeminiLive"
        private val sharedClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isSetupComplete = AtomicBoolean(false)
    private var coroutineScope: CoroutineScope? = null
    private var sessionRenewalJob: Job? = null

    fun connect(scope: CoroutineScope) {
        coroutineScope = scope
        if (apiKey.isBlank()) {
            listener.onError("Missing Gemini API Key. Configure it in Settings.", isFatal = true)
            return
        }

        listener.onConnectionStateChanged("CONNECTING")
        val url = "${GeminiConstants.WS_BASE_URL}?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = sharedClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Gemini Live WebSocket open. Sending setup frame...")
                isConnected.set(true)
                sendSetup(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncoming(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed")
                isConnected.set(false)
                isSetupComplete.set(false)
                sessionRenewalJob?.cancel()
                listener.onConnectionStateChanged("DISCONNECTED")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected.set(false)
                isSetupComplete.set(false)
                sessionRenewalJob?.cancel()
                listener.onError("Connection error: ${t.message}")
                listener.onConnectionStateChanged("ERROR")
            }
        })
    }

    private fun sendSetup(ws: WebSocket) {
        val setup = GeminiBidiSetup(
            setup = SetupPayload(
                model = model,
                generationConfig = GenerationConfigPayload(
                    responseModalities = listOf("AUDIO"),
                    speechConfig = SpeechConfigPayload(
                        voiceConfig = VoiceConfigPayload(
                            prebuiltVoiceConfig = PrebuiltVoiceConfigPayload(voiceName = voiceName)
                        )
                    )
                ),
                systemInstruction = ContentPayload(
                    parts = listOf(PartTextPayload(text = systemPrompt))
                )
            )
        )
        val json = gson.toJson(setup)
        ws.send(json)
        isSetupComplete.set(true)
        listener.onConnectionStateChanged("ONLINE")
        scheduleSessionRenewal()
    }

    private fun scheduleSessionRenewal() {
        sessionRenewalJob?.cancel()
        sessionRenewalJob = coroutineScope?.launch {
            delay(GeminiConstants.SESSION_RENEWAL_MS)
            if (isConnected.get()) {
                Log.i(TAG, "Proactive 9-minute renewal timer triggered. Reconnecting cleanly...")
                disconnect()
                connect(this)
            }
        }
    }

    fun sendAudioChunk(pcmData: ByteArray) {
        if (!isConnected.get() || !isSetupComplete.get()) return
        val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val payload = GeminiRealtimeInput(
            realtimeInput = RealtimeAudioInput(
                audio = RealtimeAudioPayload(data = base64)
            )
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun signalEndOfSpeech() {
        if (!isConnected.get()) return
        val payload = GeminiRealtimeInput(
            realtimeInput = RealtimeAudioInput(audioStreamEnd = true)
        )
        webSocket?.send(gson.toJson(payload))
    }

    private fun handleIncoming(json: String) {
        try {
            val response = gson.fromJson(json, GeminiBidiServerResponse::class.java)
            val serverContent = response.serverContent

            if (serverContent?.interrupted == true) {
                listener.onInterrupted()
            }

            val parts = serverContent?.modelTurn?.parts
            if (parts != null) {
                for (part in parts) {
                    part.inlineData?.data?.let { b64 ->
                        val bytes = Base64.decode(b64, Base64.NO_WRAP)
                        listener.onAudioDataReceived(bytes)
                    }
                    part.text?.let { txt ->
                        listener.onAssistantTextReceived(txt)
                    }
                }
            }

            if (serverContent?.turnComplete == true) {
                listener.onTurnCompleted()
            }

            // Tool Call Handling
            response.toolCall?.functionCalls?.forEach { call ->
                listener.onToolCall(call.id, call.name, call.args)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}")
        }
    }

    fun disconnect() {
        sessionRenewalJob?.cancel()
        sessionRenewalJob = null
        isConnected.set(false)
        isSetupComplete.set(false)
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }
}
