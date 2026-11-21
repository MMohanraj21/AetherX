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
 * Controller singleton managing ZARA AI's Island integration.
 *
 * Strict Architectural Rule:
 * - Genuine native system Island is used ONLY when [NativeIslandDetector.isNativeIslandSupported] is true.
 * - On unsupported devices, all floating overlays and fake simulated islands are strictly DISABLED.
 * - No fake Island, no floating popups, no unnecessary overlays.
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
    private val nativeBridge = NativeIslandBridge.getInstance(appContext)

    private var overlayView: ZaraIslandOverlayView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isOverlayAttached = false

    private var previousModeBeforeNotification: IslandMode = IslandMode.IDLE
    private var autoDismissNotificationRunnable: Runnable? = null

    init {
        observeConversationService()
    }

    fun isNativeSupported(): Boolean {
        return NativeIslandDetector.isNativeIslandSupported(appContext)
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

    private fun dp(v: Int): Int = (v * appContext.resources.displayMetrics.density).toInt()

    /**
     * Attaches or updates SuperX Capsule 2.0 integration.
     *
     * In accordance with requirements:
     * - Bridges to native system island (Vivo Origin Island / SuperX capsule notification).
     * - Renders interactive SuperX Capsule 2.0 overlay when overlay permission is granted.
     */
    @SuppressLint("WrongConstant")
    fun showIsland(): Boolean {
        val prefs = (appContext as? JarvisApp)?.preferences
        if (prefs != null && !prefs.isIslandEnabled) {
            hideIsland()
            return false
        }

        // 1. Bridge to native system island (Vivo Origin Island)
        if (isNativeSupported()) {
            val currentState = JarvisConversationService.conversationState.value
            nativeBridge.updateState(currentState)
        }

        // 2. Attach Interactive SuperX Capsule 2.0 overlay view when permission granted
        if (hasOverlayPermission()) {
            attachOverlayView()
        }

        return true
    }

    private fun attachOverlayView() {
        if (isOverlayAttached && overlayView != null) return
        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(12) // Positioned naturally around the camera cutout
            }

            val view = ZaraIslandOverlayView(appContext).also {
                setupOverlayCallbacks(it)
                overlayView = it
            }

            windowManager.addView(view, params)
            isOverlayAttached = true
            windowParams = params
            view.startFrameTicker()
            Log.i(TAG, "SuperX Capsule 2.0 attached successfully to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach SuperX Capsule overlay: ${e.message}", e)
        }
    }

    fun hideIsland() {
        nativeBridge.dismiss()
        if (!isOverlayAttached || overlayView == null) return
        try {
            overlayView?.cleanup()
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

        view.onNotificationClickListener = { _ ->
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
                if (sessionOn && isNativeSupported()) {
                    showIsland()
                } else if (!sessionOn && isNativeSupported()) {
                    nativeBridge.updateState(ConversationState.IDLE)
                }
            }
        }
    }

    private fun handleConversationStateChanged(state: ConversationState) {
        if (isNativeSupported()) {
            nativeBridge.updateState(state)
        }
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
