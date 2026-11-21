package com.jarvis.assistant.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.jarvis.assistant.island.ZaraIslandNotificationService

/**
 * Universal media playback and session controller for Spotify, YouTube Music, and Android players.
 */
class UniversalMediaController(private val context: Context) {

    companion object {
        private const val TAG = "UniversalMediaCtrl"
    }

    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private fun getActiveController(): MediaController? {
        return try {
            val component = ComponentName(context, ZaraIslandNotificationService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            controllers?.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot access active media sessions: ${e.message}")
            null
        }
    }

    fun play(): Boolean {
        val ctrl = getActiveController() ?: return false
        ctrl.transportControls.play()
        return true
    }

    fun pause(): Boolean {
        val ctrl = getActiveController() ?: return false
        ctrl.transportControls.pause()
        return true
    }

    fun togglePlayPause(): Boolean {
        val ctrl = getActiveController() ?: return false
        val state = ctrl.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            ctrl.transportControls.pause()
        } else {
            ctrl.transportControls.play()
        }
        return true
    }

    fun next(): Boolean {
        val ctrl = getActiveController() ?: return false
        ctrl.transportControls.skipToNext()
        return true
    }

    fun previous(): Boolean {
        val ctrl = getActiveController() ?: return false
        ctrl.transportControls.skipToPrevious()
        return true
    }

    fun getCurrentTrackInfo(): String {
        val ctrl = getActiveController() ?: return "No active music playing."
        val meta = ctrl.metadata
        val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        val artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
        return if (!title.isNullOrBlank()) {
            "Currently playing: $title by ${artist ?: "Unknown artist"}"
        } else {
            "A media session is connected, but track title is unavailable."
        }
    }
}
