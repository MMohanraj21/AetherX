package com.jarvis.assistant.island

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.AudioFrequencyData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * SuperX Capsule 2.0 — Premium Futuristic & Cute Dynamic Island Interface.
 *
 * Architecture & Features:
 * - 60 FPS hardware-accelerated Canvas with spring-physics morphing.
 * - Cute Animated AI Companion: Blinking eyes, breathing, thinking neural orbit,
 *   audio-reactive waveform mouth, happy celebration (^ ^), and gentle error shake.
 * - Interactive Dynamic Expansion: Tap to expand into a rich SuperX Dashboard with:
 *   * Live conversation transcripts & AI responses.
 *   * Music playback controls (Track info, play/pause, next).
 *   * Device status: Battery % + charging indicator, current time.
 *   * Quick actions: Voice trigger, settings, collapse.
 * - Gesture support: Tap to expand, swipe up to collapse.
 */
class ZaraIslandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity

    // --- State Dimensions ---
    private val idleWidth = dp(124)
    private val idleHeight = dp(36)

    private val listeningWidth = dp(250)
    private val listeningHeight = dp(52)

    private val thinkingWidth = dp(230)
    private val thinkingHeight = dp(48)

    private val speakingWidth = dp(270)
    private val speakingHeight = dp(56)

    private val successWidth = dp(240)
    private val successHeight = dp(48)

    private val errorWidth = dp(250)
    private val errorHeight = dp(50)

    private val notificationWidth = dp(300)
    private val notificationHeight = dp(64)

    private val musicWidth = dp(290)
    private val musicHeight = dp(62)

    private val callWidth = dp(280)
    private val callHeight = dp(58)

    private val expandedWidth = dp(330)
    private val expandedHeight = dp(190)

    // Current Animated Bounds
    var currentWidth = idleWidth.toFloat()
    var currentHeight = idleHeight.toFloat()
    var currentCornerRadius = idleHeight / 2f

    // Current Mode
    var currentMode: IslandMode = IslandMode.IDLE
        private set

    private var previousModeBeforeExpand: IslandMode = IslandMode.IDLE

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(244, 9, 11, 18) // Ultra-deep obsidian glass
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1).toFloat().coerceAtLeast(1.5f)
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(0, 229, 255) // Neon Cyan
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 255, 255)
    }

    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(12f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val rectBounds = RectF()
    private val springInterpolator = PathInterpolator(0.25f, 1.25f, 0.45f, 1.0f)
    private var sizeAnimator: ValueAnimator? = null

    // Waveform & Audio Levels
    private var targetAudioLevel = 0f
    private var animatedAudioLevel = 0f
    private val barHeights = FloatArray(5) { 0.2f }

    // Cute Companion Animation States
    private var breathPhase = 0f
    private var blinkTimer = 0f
    private var blinkProgress = 0f // 0 = open, 1 = fully closed
    private var isBlinking = false

    private var thinkingRotation = 0f
    private var idlePulsePhase = 0f

    // Error Shake Animation
    private var shakePhase = 0f
    private var shakeOffset = 0f

    // Floating Sparkle Particles
    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var alpha: Int,
        var color: Int
    )
    private val particles = mutableListOf<Particle>()
    private val random = Random()

    // Telemetry: Battery & Time
    private var batteryPercent: Int = 100
    private var isCharging: Boolean = false
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    // Subviews & Layouts
    private val contentLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(10), 0)
    }

    private val textLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            marginStart = dp(8)
            marginEnd = dp(6)
        }
    }

    private val tvTitle = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        text = "ZARA"
    }

    private val tvSubtitle = TextView(context).apply {
        setTextColor(Color.argb(195, 185, 205, 235))
        textSize = 10f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.MARQUEE
        isSelected = true
        visibility = View.GONE
    }

    // Action Controls (Speaking Mode)
    private val controlsLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
    }

    private val btnPause = ImageView(context).apply {
        setImageResource(android.R.drawable.ic_media_pause)
        setColorFilter(Color.rgb(0, 229, 255))
        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
            marginEnd = dp(6)
        }
    }

    private val btnStop = ImageView(context).apply {
        setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        setColorFilter(Color.rgb(255, 64, 129))
        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
    }

    // Music Action Controls
    private val musicControlsLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
    }

    private val btnMusicPlayPause = ImageView(context).apply {
        setImageResource(android.R.drawable.ic_media_pause)
        setColorFilter(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            marginEnd = dp(6)
        }
    }

    private val btnMusicNext = ImageView(context).apply {
        setImageResource(android.R.drawable.ic_media_next)
        setColorFilter(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
    }

    // --- EXPANDED DASHBOARD VIEW ---
    private val expandedLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(dp(16), dp(14), dp(16), dp(12))
    }

    private val tvExpandedHeader = TextView(context).apply {
        setTextColor(Color.rgb(0, 229, 255))
        textSize = 11f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        text = "SUPERX CAPSULE 2.0"
    }

    private val tvExpandedTranscript = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = android.graphics.Typeface.DEFAULT
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        text = "Say \"Hey Zara\" or tap to speak."
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(6)
            bottomMargin = dp(8)
        }
    }

    private val expandedControlsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(4)
        }
    }

    private val tvBatteryStatus = TextView(context).apply {
        setTextColor(Color.argb(200, 180, 200, 230))
        textSize = 10f
        text = "⚡ 100%"
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
    }

    private val btnCollapse = TextView(context).apply {
        setTextColor(Color.rgb(0, 229, 255))
        textSize = 11f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        text = "▲ COLLAPSE"
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    // Listeners
    var onIslandClickListener: (() -> Unit)? = null
    var onIslandLongClickListener: (() -> Unit)? = null
    var onStopClickListener: (() -> Unit)? = null
    var onPauseClickListener: (() -> Unit)? = null
    var onMusicPlayPauseListener: (() -> Unit)? = null
    var onMusicNextListener: (() -> Unit)? = null
    var onNotificationClickListener: ((notificationId: String) -> Unit)? = null

    private var activeNotificationId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAnimating = false

    // Touch gesture tracking for swipe up/down
    private var touchStartY = 0f

    // Battery Broadcast Receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryPercent = (level * 100) / scale
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            updateBatteryText()
        }
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            updateVisualizer()
            updateCompanionAnimations()
            invalidate()
            if (isAnimating) {
                mainHandler.postDelayed(this, 16) // 60 FPS
            }
        }
    }

    init {
        setWillNotDraw(false)
        clipToPadding = false
        clipChildren = false

        textLayout.addView(tvTitle)
        textLayout.addView(tvSubtitle)

        controlsLayout.addView(btnPause)
        controlsLayout.addView(btnStop)

        musicControlsLayout.addView(btnMusicPlayPause)
        musicControlsLayout.addView(btnMusicNext)

        contentLayout.addView(textLayout)
        contentLayout.addView(controlsLayout)
        contentLayout.addView(musicControlsLayout)

        val flParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }
        addView(contentLayout, flParams)

        // Expanded Layout
        expandedControlsRow.addView(tvBatteryStatus)
        expandedControlsRow.addView(btnCollapse)

        expandedLayout.addView(tvExpandedHeader)
        expandedLayout.addView(tvExpandedTranscript)
        expandedLayout.addView(expandedControlsRow)

        val expParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        addView(expandedLayout, expParams)

        setupListeners()
        initParticles()
        registerBatteryReceiver()
        setMode(IslandMode.IDLE, animate = false)
        startFrameTicker()
    }

    private fun registerBatteryReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
        } catch (_: Exception) {}
    }

    fun cleanup() {
        stopFrameTicker()
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }

    private fun updateBatteryText() {
        val chargeIcon = if (isCharging) "⚡ " else "🔋 "
        val timeStr = timeFormat.format(Date())
        tvBatteryStatus.text = "$chargeIcon$batteryPercent% • $timeStr"
    }

    private fun initParticles() {
        particles.clear()
        for (i in 0 until 12) {
            particles.add(
                Particle(
                    x = random.nextFloat() * idleWidth,
                    y = random.nextFloat() * idleHeight,
                    vx = (random.nextFloat() - 0.5f) * 0.8f,
                    vy = -0.3f - random.nextFloat() * 0.5f,
                    radius = dp(1) + random.nextFloat() * dp(2),
                    alpha = random.nextInt(150) + 50,
                    color = if (random.nextBoolean()) Color.rgb(0, 229, 255) else Color.rgb(179, 136, 255)
                )
            )
        }
    }

    private fun setupListeners() {
        btnStop.setOnClickListener { onStopClickListener?.invoke() }
        btnPause.setOnClickListener { onPauseClickListener?.invoke() }
        btnMusicPlayPause.setOnClickListener { onMusicPlayPauseListener?.invoke() }
        btnMusicNext.setOnClickListener { onMusicNextListener?.invoke() }
        btnCollapse.setOnClickListener { collapse() }

        setOnLongClickListener {
            onIslandLongClickListener?.invoke()
            true
        }

        setOnClickListener {
            if (currentMode == IslandMode.EXPANDED) {
                // Clicking inside expanded
                onIslandClickListener?.invoke()
            } else if (currentMode == IslandMode.NOTIFICATION) {
                activeNotificationId?.let { id -> onNotificationClickListener?.invoke(id) }
            } else {
                // Tap to Expand!
                expand()
                onIslandClickListener?.invoke()
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> touchStartY = ev.rawY
            MotionEvent.ACTION_MOVE -> {
                val deltaY = touchStartY - ev.rawY
                if (currentMode == IslandMode.EXPANDED && deltaY > dp(25)) {
                    // Swipe up detected on expanded card
                    collapse()
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    fun expand() {
        if (currentMode == IslandMode.EXPANDED) return
        previousModeBeforeExpand = currentMode
        setMode(IslandMode.EXPANDED, animate = true)
    }

    fun collapse() {
        if (currentMode != IslandMode.EXPANDED) return
        setMode(previousModeBeforeExpand, animate = true)
    }

    fun startFrameTicker() {
        isAnimating = true
        mainHandler.removeCallbacks(frameRunnable)
        mainHandler.post(frameRunnable)
    }

    fun stopFrameTicker() {
        isAnimating = false
        mainHandler.removeCallbacks(frameRunnable)
    }

    fun setMode(mode: IslandMode, animate: Boolean = true) {
        if (currentMode == mode && !animate) return
        currentMode = mode

        val (targetW, targetH) = when (mode) {
            IslandMode.IDLE -> Pair(idleWidth, idleHeight)
            IslandMode.LISTENING -> Pair(listeningWidth, listeningHeight)
            IslandMode.THINKING -> Pair(thinkingWidth, thinkingHeight)
            IslandMode.SPEAKING -> Pair(speakingWidth, speakingHeight)
            IslandMode.SUCCESS -> Pair(successWidth, successHeight)
            IslandMode.ERROR -> {
                startErrorShake()
                Pair(errorWidth, errorHeight)
            }
            IslandMode.NOTIFICATION -> Pair(notificationWidth, notificationHeight)
            IslandMode.MUSIC -> Pair(musicWidth, musicHeight)
            IslandMode.CALL -> Pair(callWidth, callHeight)
            IslandMode.EXPANDED -> Pair(expandedWidth, expandedHeight)
        }

        updateContentForMode(mode)

        if (animate) {
            animateToSize(targetW.toFloat(), targetH.toFloat(), isExpanded = (mode == IslandMode.EXPANDED))
        } else {
            currentWidth = targetW.toFloat()
            currentHeight = targetH.toFloat()
            currentCornerRadius = if (mode == IslandMode.EXPANDED) dp(28).toFloat() else currentHeight / 2f
            requestLayout()
            invalidate()
        }
    }

    private fun startErrorShake() {
        shakePhase = 0f
    }

    private fun updateContentForMode(mode: IslandMode) {
        controlsLayout.visibility = View.GONE
        musicControlsLayout.visibility = View.GONE
        tvSubtitle.visibility = View.GONE

        if (mode == IslandMode.EXPANDED) {
            contentLayout.visibility = View.GONE
            expandedLayout.visibility = View.VISIBLE
            updateBatteryText()
            return
        }

        contentLayout.visibility = View.VISIBLE
        expandedLayout.visibility = View.GONE

        when (mode) {
            IslandMode.IDLE -> {
                tvTitle.text = "ZARA"
                tvTitle.textSize = 11f
                tvSubtitle.visibility = View.GONE
                contentLayout.setPadding(dp(36), 0, dp(10), 0)
            }
            IslandMode.LISTENING -> {
                tvTitle.text = "Listening…"
                tvTitle.textSize = 12f
                tvSubtitle.text = "Speak your request"
                tvSubtitle.visibility = View.VISIBLE
                contentLayout.setPadding(dp(40), 0, dp(14), 0)
            }
            IslandMode.THINKING -> {
                tvTitle.text = "Thinking…"
                tvTitle.textSize = 12f
                tvSubtitle.text = "Processing request"
                tvSubtitle.visibility = View.VISIBLE
                contentLayout.setPadding(dp(40), 0, dp(14), 0)
            }
            IslandMode.SPEAKING -> {
                tvTitle.text = "Speaking…"
                tvTitle.textSize = 12f
                tvSubtitle.text = "Replying to you"
                tvSubtitle.visibility = View.VISIBLE
                controlsLayout.visibility = View.VISIBLE
                contentLayout.setPadding(dp(40), 0, dp(12), 0)
            }
            IslandMode.SUCCESS -> {
                tvTitle.text = "Completed!"
                tvTitle.textSize = 12f
                tvSubtitle.text = "Task executed successfully"
                tvSubtitle.visibility = View.VISIBLE
                contentLayout.setPadding(dp(40), 0, dp(12), 0)
            }
            IslandMode.ERROR -> {
                tvTitle.text = "Zara Error"
                tvTitle.textSize = 12f
                tvSubtitle.text = "Tap to retry"
                tvSubtitle.visibility = View.VISIBLE
                contentLayout.setPadding(dp(40), 0, dp(12), 0)
            }
            IslandMode.NOTIFICATION -> {
                tvSubtitle.visibility = View.VISIBLE
                contentLayout.setPadding(dp(38), 0, dp(14), 0)
            }
            IslandMode.MUSIC -> {
                tvSubtitle.visibility = View.VISIBLE
                musicControlsLayout.visibility = View.VISIBLE
                contentLayout.setPadding(dp(38), 0, dp(12), 0)
            }
            IslandMode.CALL -> {
                tvSubtitle.visibility = View.VISIBLE
                contentLayout.setPadding(dp(38), 0, dp(14), 0)
            }
            else -> {}
        }
    }

    fun setLiveTranscript(transcript: String) {
        if (transcript.isNotBlank()) {
            tvSubtitle.text = transcript
            tvSubtitle.visibility = View.VISIBLE
            tvExpandedTranscript.text = transcript
        }
    }

    fun setAudioLevel(level: Float) {
        targetAudioLevel = level.coerceIn(0f, 1f)
    }

    fun setNotification(notification: IslandNotificationData) {
        activeNotificationId = notification.id
        tvTitle.text = notification.title
        tvSubtitle.text = notification.text
        setMode(IslandMode.NOTIFICATION)
    }

    fun setMusic(music: IslandMusicData) {
        tvTitle.text = music.title
        tvSubtitle.text = music.artist
        btnMusicPlayPause.setImageResource(
            if (music.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        setMode(IslandMode.MUSIC)
    }

    private fun updateVisualizer() {
        animatedAudioLevel += (targetAudioLevel - animatedAudioLevel) * 0.35f
        for (i in barHeights.indices) {
            val offset = sin(System.currentTimeMillis() / 80.0 + i * 1.2).toFloat() * 0.25f
            barHeights[i] = (animatedAudioLevel * 0.8f + offset).coerceIn(0.15f, 1.0f)
        }
    }

    private fun updateCompanionAnimations() {
        // Breathing
        breathPhase += 0.05f
        if (breathPhase > Math.PI * 2) breathPhase = 0f

        // Natural Eye Blinking: blinks every ~3.5 seconds
        blinkTimer += 0.016f
        if (!isBlinking && blinkTimer > 3.5f) {
            isBlinking = true
            blinkTimer = 0f
            blinkProgress = 0f
        }
        if (isBlinking) {
            blinkProgress += 0.12f
            if (blinkProgress >= 1f) {
                isBlinking = false
                blinkProgress = 0f
            }
        }

        // Thinking Rotation
        thinkingRotation = (thinkingRotation + 4.5f) % 360f

        // Idle Pulse
        idlePulsePhase += 0.06f

        // Error Shake decay
        if (currentMode == IslandMode.ERROR && shakePhase < Math.PI * 4) {
            shakePhase += 0.35f
            shakeOffset = (sin(shakePhase) * dp(5) * (1f - (shakePhase / (Math.PI * 4f)))).toFloat()
        } else {
            shakeOffset = 0f
        }

        // Particle floating
        val w = currentWidth
        val h = currentHeight
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            p.alpha = (p.alpha - 2).coerceAtLeast(0)
            if (p.y < 0 || p.alpha <= 0) {
                p.x = random.nextFloat() * w
                p.y = h
                p.alpha = random.nextInt(160) + 60
                p.vy = -0.3f - random.nextFloat() * 0.5f
            }
        }
    }

    private fun animateToSize(targetW: Float, targetH: Float, isExpanded: Boolean) {
        sizeAnimator?.cancel()
        val startW = currentWidth
        val startH = currentHeight
        val startR = currentCornerRadius
        val targetR = if (isExpanded) dp(28).toFloat() else targetH / 2f

        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (isExpanded) 360L else 300L
            interpolator = springInterpolator
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                currentWidth = startW + (targetW - startW) * fraction
                currentHeight = startH + (targetH - startH) * fraction
                currentCornerRadius = startR + (targetR - startR) * fraction
                requestLayout()
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wSpec = MeasureSpec.makeMeasureSpec(currentWidth.toInt(), MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(currentHeight.toInt(), MeasureSpec.EXACTLY)
        super.onMeasure(wSpec, hSpec)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        if (shakeOffset != 0f) {
            canvas.translate(shakeOffset, 0f)
        }

        val w = width.toFloat()
        val h = height.toFloat()
        rectBounds.set(0f, 0f, w, h)

        // 1. Draw Deep Glassmorphic Obsidian Background
        canvas.drawRoundRect(rectBounds, currentCornerRadius, currentCornerRadius, backgroundPaint)

        // 2. Draw Dual-Color Neon Glow Border (Cyan #00E5FF to Purple #B388FF)
        val gradient = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.argb(230, 0, 229, 255),  // Neon Cyan
                Color.argb(170, 123, 31, 162), // Electric Purple
                Color.argb(230, 179, 136, 255) // Cyber Violet
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        borderPaint.shader = gradient
        val inset = borderPaint.strokeWidth / 2f
        rectBounds.inset(inset, inset)
        canvas.drawRoundRect(rectBounds, currentCornerRadius, currentCornerRadius, borderPaint)

        // 3. Draw Ambient Floating Sparkle Particles
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
        }

        // 4. Draw Cute Companion Face (left area)
        drawCuteCompanion(canvas, h)

        // 5. Draw Mode-Specific Right Visualizers
        when (currentMode) {
            IslandMode.LISTENING -> drawWaveform(canvas, w, h, isListening = true)
            IslandMode.SPEAKING -> drawWaveform(canvas, w, h, isListening = false)
            IslandMode.THINKING -> drawThinkingOrbit(canvas, w, h)
            IslandMode.SUCCESS -> drawSuccessCelebration(canvas, w, h)
            IslandMode.ERROR -> drawErrorIndicator(canvas, w, h)
            IslandMode.IDLE -> drawIdleGlow(canvas, w, h)
            else -> {}
        }

        canvas.restore()
        super.onDraw(canvas)
    }

    /**
     * Draws the Cute AI Companion expressions on the left pill slot:
     * - Blinking eyes, smiling curves, attentive listening eyes, thinking looking-up eyes.
     */
    private fun drawCuteCompanion(canvas: Canvas, h: Float) {
        val cx = dp(20).toFloat()
        val cy = h / 2f

        val eyeSpacing = dp(5).toFloat()
        val eyeWidth = dp(3).toFloat()
        var eyeHeight = dp(6).toFloat()

        // Natural eyelid blink closure
        if (isBlinking) {
            val blinkFraction = sin(blinkProgress * Math.PI).toFloat()
            eyeHeight = (eyeHeight * (1f - blinkFraction)).coerceAtLeast(1.5f)
        }

        eyePaint.color = when (currentMode) {
            IslandMode.ERROR -> Color.rgb(255, 64, 129)
            IslandMode.SUCCESS -> Color.rgb(0, 230, 118)
            IslandMode.THINKING -> Color.rgb(179, 136, 255)
            else -> Color.rgb(0, 229, 255)
        }

        when (currentMode) {
            IslandMode.SUCCESS -> {
                // Happy squinting eyes (^ ^)
                val strokeEye = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(0, 230, 118)
                    style = Paint.Style.STROKE
                    strokeWidth = dp(2).toFloat()
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(cx - eyeSpacing - dp(2), cy + dp(1), cx - eyeSpacing, cy - dp(2), strokeEye)
                canvas.drawLine(cx - eyeSpacing, cy - dp(2), cx - eyeSpacing + dp(2), cy + dp(1), strokeEye)

                canvas.drawLine(cx + eyeSpacing - dp(2), cy + dp(1), cx + eyeSpacing, cy - dp(2), strokeEye)
                canvas.drawLine(cx + eyeSpacing, cy - dp(2), cx + eyeSpacing + dp(2), cy + dp(1), strokeEye)
            }
            IslandMode.THINKING -> {
                // Curious looking-up eyes
                val leftEyeRect = RectF(cx - eyeSpacing - eyeWidth, cy - eyeHeight / 2f - dp(2), cx - eyeSpacing, cy + eyeHeight / 2f - dp(2))
                val rightEyeRect = RectF(cx + eyeSpacing, cy - eyeHeight / 2f - dp(2), cx + eyeSpacing + eyeWidth, cy + eyeHeight / 2f - dp(2))
                canvas.drawRoundRect(leftEyeRect, eyeWidth / 2f, eyeWidth / 2f, eyePaint)
                canvas.drawRoundRect(rightEyeRect, eyeWidth / 2f, eyeWidth / 2f, eyePaint)
            }
            IslandMode.ERROR -> {
                // Sad/apologetic eyes (• ‸ •)
                val leftEyeRect = RectF(cx - eyeSpacing - eyeWidth, cy - eyeHeight / 2f + dp(1), cx - eyeSpacing, cy + eyeHeight / 2f + dp(1))
                val rightEyeRect = RectF(cx + eyeSpacing, cy - eyeHeight / 2f + dp(1), cx + eyeSpacing + eyeWidth, cy + eyeHeight / 2f + dp(1))
                canvas.drawRoundRect(leftEyeRect, eyeWidth / 2f, eyeWidth / 2f, eyePaint)
                canvas.drawRoundRect(rightEyeRect, eyeWidth / 2f, eyeWidth / 2f, eyePaint)
            }
            else -> {
                // Normal Cute Blinking Capsule Eyes
                val leftEyeRect = RectF(cx - eyeSpacing - eyeWidth, cy - eyeHeight / 2f, cx - eyeSpacing, cy + eyeHeight / 2f)
                val rightEyeRect = RectF(cx + eyeSpacing, cy - eyeHeight / 2f, cx + eyeSpacing + eyeWidth, cy + eyeHeight / 2f)
                canvas.drawRoundRect(leftEyeRect, eyeWidth / 2f, eyeWidth / 2f, eyePaint)
                canvas.drawRoundRect(rightEyeRect, eyeWidth / 2f, eyeWidth / 2f, eyePaint)

                // White pupil sparkle
                if (!isBlinking) {
                    canvas.drawCircle(cx - eyeSpacing - eyeWidth / 2f, cy - dp(1), dp(1).toFloat(), pupilPaint)
                    canvas.drawCircle(cx + eyeSpacing + eyeWidth / 2f, cy - dp(1), dp(1).toFloat(), pupilPaint)
                }
            }
        }
    }

    private fun drawWaveform(canvas: Canvas, w: Float, h: Float, isListening: Boolean) {
        val numBars = barHeights.size
        val barWidth = dp(3).toFloat()
        val spacing = dp(2).toFloat()
        val totalVisualizerW = (numBars * barWidth) + ((numBars - 1) * spacing)
        val startX = w - totalVisualizerW - dp(14)
        val centerY = h / 2f
        val maxBarH = h * 0.55f

        for (i in 0 until numBars) {
            val barH = maxBarH * barHeights[i]
            val x = startX + i * (barWidth + spacing)
            val top = centerY - barH / 2f
            val bottom = centerY + barH / 2f

            waveformPaint.color = if (isListening) {
                Color.argb(240, 0, 229, 255) // Neon Cyan
            } else {
                Color.argb(240, 179, 136, 255) // Electric Purple
            }
            canvas.drawRoundRect(x, top, x + barWidth, bottom, barWidth / 2f, barWidth / 2f, waveformPaint)
        }
    }

    private fun drawThinkingOrbit(canvas: Canvas, w: Float, h: Float) {
        val radius = dp(9).toFloat()
        val cx = w - radius - dp(16)
        val cy = h / 2f
        val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        orbitPaint.color = Color.rgb(0, 229, 255)
        canvas.drawArc(arcRect, thinkingRotation, 100f, false, orbitPaint)

        orbitPaint.color = Color.rgb(179, 136, 255)
        canvas.drawArc(arcRect, (thinkingRotation + 180f) % 360f, 100f, false, orbitPaint)

        // Orbiting dot
        val dotRad = Math.toRadians(thinkingRotation.toDouble())
        val dotX = cx + (radius * cos(dotRad)).toFloat()
        val dotY = cy + (radius * sin(dotRad)).toFloat()
        glowPaint.color = Color.WHITE
        canvas.drawCircle(dotX, dotY, dp(2).toFloat(), glowPaint)
    }

    private fun drawSuccessCelebration(canvas: Canvas, w: Float, h: Float) {
        val cx = w - dp(18)
        val cy = h / 2f
        glowPaint.color = Color.rgb(0, 230, 118)
        canvas.drawCircle(cx, cy, dp(4).toFloat(), glowPaint)
    }

    private fun drawErrorIndicator(canvas: Canvas, w: Float, h: Float) {
        val cx = w - dp(18)
        val cy = h / 2f
        glowPaint.color = Color.rgb(255, 64, 129)
        canvas.drawCircle(cx, cy, dp(4).toFloat(), glowPaint)
    }

    private fun drawIdleGlow(canvas: Canvas, w: Float, h: Float) {
        val pulseAlpha = ((sin(idlePulsePhase) * 0.35f + 0.65f) * 255).toInt()
        val cx = w - dp(14)
        val cy = h / 2f
        val r = dp(3).toFloat()

        glowPaint.color = Color.argb(pulseAlpha, 0, 229, 255)
        canvas.drawCircle(cx, cy, r, glowPaint)
    }
}