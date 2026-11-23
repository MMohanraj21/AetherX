package com.jarvis.assistant.island

import android.content.Context
import android.util.Log
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.notification.ZaraNotificationManager

/**
 * Controller singleton for native Vivo Origin Island (Vivo T5 Pro / OriginOS 6 / Android 16 API 36).
 *
 * Strict Architectural Rules (v5.5):
 * - NO floating overlay windows (WindowManager.addView).
 * - NO fake island popups or duplicated bubbles.
 * - Bridges directly to the native Vivo Origin Island compositor via Android 16 Promoted
 *   Ongoing Notification through [ZaraNotificationManager] (Notification ID 41).
 * - Never calls startForeground() repeatedly on updates; delegates to notify() to avoid
 *   service recreation or audio session interrupts.
 */
object OriginIslandController {
    private const val TAG = "OriginIslandController"

    const val CHANNEL_ID = ZaraNotificationManager.CHANNEL_ID
    const val NOTIFICATION_ID = ZaraNotificationManager.NOTIFICATION_ID

    const val ACTION_PLAY_PAUSE = ZaraNotificationManager.ACTION_PLAY_PAUSE
    const val ACTION_NEXT = ZaraNotificationManager.ACTION_NEXT
    const val ACTION_PREVIOUS = ZaraNotificationManager.ACTION_PREVIOUS

    /**
     * Updates Origin Island for Voice Assistant lifecycle states.
     */
    fun updateVoiceState(context: Context, state: ConversationState, detail: String? = null) {
        ZaraNotificationManager.updateNotification(
            context = context,
            state = state,
            detail = detail,
            isStandby = false
        )
    }

    fun startListening(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateVoiceState(ctx, ConversationState.LISTENING)
    }

    fun startThinking(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateVoiceState(ctx, ConversationState.THINKING)
    }

    fun startSpeaking(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        updateVoiceState(ctx, ConversationState.SPEAKING)
    }

    /**
     * Updates Origin Island with active music metadata and playback controls.
     */
    fun updateMusicState(context: Context, trackTitle: String, artist: String?, isPlaying: Boolean) {
        ZaraNotificationManager.updateNotification(
            context = context,
            isMedia = true,
            mediaTitle = trackTitle,
            mediaArtist = artist,
            isPlaying = isPlaying
        )
    }

    /**
     * Posts a smart notification alert (task completed, connection status, error).
     */
    fun updateSmartNotification(context: Context, title: String, message: String, isError: Boolean = false) {
        ZaraNotificationManager.updateNotification(
            context = context,
            detail = message,
            isError = isError
        )
    }

    fun dismiss(context: Context? = null) {
        val ctx = context ?: JarvisConversationService.activeInstance ?: return
        ZaraNotificationManager.updateNotification(ctx, isStandby = true)
        Log.i(TAG, "Origin Island capsule transitioned to standby cleanly.")
    }
}
