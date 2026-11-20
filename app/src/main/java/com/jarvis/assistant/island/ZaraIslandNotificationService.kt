package com.jarvis.assistant.island

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvis.assistant.JarvisApp

/**
 * Notification and Media Session listener for ZARA AI Origin Island.
 * Captures:
 * 1. Incoming priority notifications to display on the floating island.
 * 2. Active media session metadata (title, artist, playback state, album art) for Music Mode.
 */
class ZaraIslandNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "ZaraIslandNotifService"
        @Volatile
        var isConnected: Boolean = false
            private set
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeMediaController: MediaController? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaInfo()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaInfo()
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        setupActiveMediaController(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(TAG, "ZaraIslandNotificationService connected")
        initMediaSessionTracking()
    }

    override fun onListenerDisconnected() {
        isConnected = false
        cleanupMediaTracking()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val prefs = (application as? JarvisApp)?.preferences
        if (prefs != null && !prefs.isIslandNotificationsEnabled) return

        val pkg = sbn.packageName.orEmpty()
        // Ignore self-notifications from Zara
        if (pkg == packageName || pkg == "com.zara.assistant" || pkg == "com.jarvis.assistant") return

        // Filter out ongoing / non-clearable / silent system notifications
        if (sbn.isOngoing) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        if (title.isBlank() && text.isBlank()) return

        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg
        }

        val appIcon = try {
            pm.getApplicationIcon(pkg)
        } catch (_: Exception) {
            null
        }

        val islandNotif = IslandNotificationData(
            id = "${sbn.id}_${sbn.postTime}",
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            icon = appIcon,
            timestamp = sbn.postTime
        )

        ZaraIslandManager.getInstance(this).postNotification(islandNotif)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Handled via auto-dismiss in ZaraIslandManager
    }

    private fun initMediaSessionTracking() {
        try {
            val prefs = (application as? JarvisApp)?.preferences
            if (prefs != null && !prefs.isIslandMusicEnabled) return

            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, ZaraIslandNotificationService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            setupActiveMediaController(controllers)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize MediaSession tracking: ${e.message}")
        }
    }

    private fun setupActiveMediaController(controllers: List<MediaController>?) {
        activeMediaController?.unregisterCallback(mediaCallback)
        activeMediaController = controllers?.firstOrNull()
        activeMediaController?.registerCallback(mediaCallback)
        updateMediaInfo()
    }

    private fun updateMediaInfo() {
        val controller = activeMediaController ?: return
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val albumArt: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        if (title.isNotBlank()) {
            val musicData = IslandMusicData(
                title = title,
                artist = artist.ifBlank { "Playing" },
                isPlaying = isPlaying,
                albumArt = albumArt,
                packageName = controller.packageName.orEmpty()
            )
            ZaraIslandManager.getInstance(this).updateMusic(musicData)
        }
    }

    private fun cleanupMediaTracking() {
        try {
            activeMediaController?.unregisterCallback(mediaCallback)
            activeMediaController = null
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        cleanupMediaTracking()
        super.onDestroy()
    }
}
