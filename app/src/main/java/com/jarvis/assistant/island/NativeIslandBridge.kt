package com.jarvis.assistant.island

import android.content.Context
import com.jarvis.assistant.data.model.ConversationState

/**
 * Bridge for genuine native system Island / Live Activity integration (Vivo Origin Island, Aqua Dynamics, etc.).
 *
 * All state updates route through [OriginIslandController] and [com.jarvis.assistant.notification.ZaraNotificationManager]
 * ensuring exactly one notification (ID 41) and one silent channel across the entire system.
 */
class NativeIslandBridge private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NativeIslandBridge"

        @Volatile
        private var instance: NativeIslandBridge? = null

        fun getInstance(context: Context): NativeIslandBridge {
            return instance ?: synchronized(this) {
                instance ?: NativeIslandBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Updates the native system Island with ZARA AI's current conversation state or task progress.
     */
    fun updateState(
        state: ConversationState,
        detailText: String? = null,
        progressPercent: Int? = null
    ) {
        when (state) {
            ConversationState.LISTENING -> OriginIslandController.startListening(context)
            ConversationState.THINKING -> OriginIslandController.startThinking(context)
            ConversationState.SPEAKING -> OriginIslandController.startSpeaking(context)
            ConversationState.IDLE -> OriginIslandController.dismiss(context)
        }
    }

    /**
     * Clears the native Island state.
     */
    fun dismiss() {
        OriginIslandController.dismiss(context)
    }
}
