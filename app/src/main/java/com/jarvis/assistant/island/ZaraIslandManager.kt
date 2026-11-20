package com.jarvis.assistant.island

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.ui.home.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Controller singleton managing ZARA AI's Origin Island overlay.
 * Handles:
 * - Dynamic punch-hole positioning across Vivo OriginOS, Funtouch OS, and standard Android.
 * - WindowManager overlay lifecycle with TYPE_APPLICATION_OVERLAY.
 * - Reactive state synchronization with ZARA AI conversation engine, media session, and notifications.
 */
class ZaraIslandManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "ZaraIslandManager"

        @Volatile
        private var instance: ZaraIslandManager? = null

        fun getInstance(context: Context): ZaraIslandManager {
            return instance ?: synchronized(this) {
                instance ?: ZaraIslandManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: ZaraIslandOverlayView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isOverlayAttached = false

    private var previousModeBeforeNotification: IslandMode = IslandMode.IDLE
    private var autoDismissNotificationRunnable: Runnable? = null

    init {
        observeConversationService()
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(appContext)
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Attaches or updates the Floating Island in the Android WindowManager.
     */
    @SuppressLint("WrongConstant")
    fun showIsland(): Boolean {
        val prefs = (appContext as? JarvisApp)?.preferences
        if (prefs != null && !prefs.isIslandEnabled) {
            hideIsland()
            return false
        }

        if (!hasOverlayPermission()) {
            Log.w(TAG, "showIsland: Overlay permission not granted; floating island cannot attach to window")
            return false
        }

        if (isOverlayAttached && overlayView != null) {
            overlayView?.visibility = View.VISIBLE
            return true
        }

        try {
            val view = ZaraIslandOverlayView(appContext).apply {
                setupOverlayCallbacks(this)
            }
            overlayView = view

            val statusBarOffset = calculateStatusBarOffset()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = statusBarOffset
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            windowParams = params

            windowManager.addView(view, params)
            isOverlayAttached = true
            Log.i(TAG, "Origin Island attached to WindowManager successfully (y-offset=$statusBarOffset)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Origin Island overlay: ${e.message}", e)
            isOverlayAttached = false
            overlayView = null
            return false
        }
    }

    fun hideIsland() {
        if (!isOverlayAttached || overlayView == null) return
        try {
            overlayView?.stopFrameTicker()
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing overlay view: ${e.message}")
        } finally {
            isOverlayAttached = false
            overlayView = null
        }
    }

    private fun setupOverlayCallbacks(view: ZaraIslandOverlayView) {
        view.onIslandClickListener = {
            val isSessionOn = JarvisConversationService.isSessionOn.value
            if (!isSessionOn) {
                // Activate voice session immediately
                val intent = Intent(appContext, JarvisConversationService::class.java).apply {
                    action = JarvisConversationService.ACTION_START_CONVERSATION
                }
                androidx.core.content.ContextCompat.startForegroundService(appContext, intent)
            } else {
                launchMainActivity()
            }
        }

        view.onIslandLongClickListener = {
            launchMainActivity()
        }

        view.onStopClickListener = {
            val service = JarvisConversationService.activeInstance
            service?.cancelAllActiveTools()
            val intent = Intent(appContext, JarvisConversationService::class.java).apply {
                action = JarvisConversationService.ACTION_ENTER_STANDBY
            }
            androidx.core.content.ContextCompat.startForegroundService(appContext, intent)
        }

        view.onPauseClickListener = {
            val service = JarvisConversationService.activeInstance
            service?.cancelAllActiveTools()
        }

        view.onMusicPlayPauseListener = {
            toggleActiveMediaPlayback()
        }

        view.onMusicNextListener = {
            skipActiveMediaNext()
        }

        view.onNotificationClickListener = { id ->
            launchMainActivity()
        }
    }

    private fun launchMainActivity() {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        appContext.startActivity(intent)
    }

    /**
     * Calculates optimal Y offset around the camera punch-hole.
     * Takes Vivo OriginOS / Funtouch OS cutout dimensions and status bar height into account.
     */
    private fun calculateStatusBarOffset(): Int {
        var result = (10 * appContext.resources.displayMetrics.density).toInt()
        val resourceId = appContext.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            val sbHeight = appContext.resources.getDimensionPixelSize(resourceId)
            // Center the island with the status bar punch-hole area
            result = maxOf((6 * appContext.resources.displayMetrics.density).toInt(), sbHeight / 3)
        }
        return result
    }

    /**
     * Subscribes to conversation state and audio flows from [JarvisConversationService].
     */
    private fun observeConversationService() {
        scope.launch {
            JarvisConversationService.conversationState.collectLatest { state ->
                handleConversationStateChanged(state)
            }
        }

        scope.launch {
            JarvisConversationService.audioLevel.collectLatest { level ->
                overlayView?.setAudioLevel(level)
            }
        }

        scope.launch {
            JarvisConversationService.liveUserTranscript.collectLatest { transcript ->
                overlayView?.setLiveTranscript(transcript)
            }
        }

        scope.launch {
            JarvisConversationService.isSessionOn.collectLatest { sessionOn ->
                if (sessionOn) {
                    showIsland()
                }
            }
        }
    }

    private fun handleConversationStateChanged(state: ConversationState) {
        val view = overlayView ?: return
        when (state) {
            ConversationState.IDLE -> view.setMode(IslandMode.IDLE)
            ConversationState.LISTENING -> view.setMode(IslandMode.LISTENING)
            ConversationState.THINKING -> view.setMode(IslandMode.THINKING)
            ConversationState.SPEAKING -> view.setMode(IslandMode.SPEAKING)
        }
    }

    // --- Notification & Media Ingestion ---

    fun postNotification(notification: IslandNotificationData) {
        val view = overlayView ?: return
        if (view.currentMode != IslandMode.NOTIFICATION) {
            previousModeBeforeNotification = view.currentMode
        }
        view.setNotification(notification)

        // Auto-dismiss back to previous state after 5 seconds
        autoDismissNotificationRunnable?.let { mainHandler.removeCallbacks(it) }
        autoDismissNotificationRunnable = Runnable {
            if (overlayView?.currentMode == IslandMode.NOTIFICATION) {
                overlayView?.setMode(previousModeBeforeNotification)
            }
        }.also {
            mainHandler.postDelayed(it, 5000L)
        }
    }

    fun updateMusic(music: IslandMusicData) {
        val view = overlayView ?: return
        if (music.isPlaying && view.currentMode == IslandMode.IDLE) {
            view.setMusic(music)
        }
    }

    private fun toggleActiveMediaPlayback() {
        try {
            val sessionManager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val controllers = sessionManager?.getActiveSessions(null)
            val controller = controllers?.firstOrNull()
            val state = controller?.playbackState?.state
            if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                controller.transportControls?.pause()
            } else {
                controller?.transportControls?.play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling media playback: ${e.message}")
        }
    }

    private fun skipActiveMediaNext() {
        try {
            val sessionManager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val controllers = sessionManager?.getActiveSessions(null)
            controllers?.firstOrNull()?.transportControls?.skipToNext()
        } catch (e: Exception) {
            Log.w(TAG, "Error skipping media: ${e.message}")
        }
    }
}
