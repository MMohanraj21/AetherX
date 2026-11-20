package com.jarvis.assistant.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
import kotlin.math.sin

/**
 * Premium Android Floating Island interface inspired by Apple's Dynamic Island & Vivo Origin Island.
 * Features:
 * - Fluid spring-animated morphing around the camera punch-hole area.
 * - Hardware-accelerated glassmorphism with neon cyan & electric purple glow.
 * - Real-time animated audio waveforms, thinking orbit rings, music marquee, and call/notification cards.
 */
class ZaraIslandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // --- Dimension targets per state ---
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private val idleWidth = dp(120)
    private val idleHeight = dp(34)

    private val listeningWidth = dp(250)
    private val listeningHeight = dp(52)

    private val thinkingWidth = dp(230)
    private val thinkingHeight = dp(48)

    private val speakingWidth = dp(270)
    private val speakingHeight = dp(56)

    private val notificationWidth = dp(300)
    private val notificationHeight = dp(64)

    private val musicWidth = dp(290)
    private val musicHeight = dp(62)

    private val callWidth = dp(280)
    private val callHeight = dp(58)

    // Current animated dimensions
    var currentWidth = idleWidth.toFloat()
    var currentHeight = idleHeight.toFloat()
    var currentCornerRadius = idleHeight / 2f

    // Current State
    var currentMode: IslandMode = IslandMode.IDLE
        private set

    // Animation & Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(238, 9, 11, 17) // Ultra-deep obsidian glass
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1).toFloat().coerceAtLeast(1.5f)
    }

    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rectBounds = RectF()
    private val springInterpolator = PathInterpolator(0.28f, 1.25f, 0.45f, 1.0f)
    private var sizeAnimator: ValueAnimator? = null

    // Waveform & Visualizer State
    private var targetAudioLevel = 0f
    private var animatedAudioLevel = 0f
    private val barHeights = FloatArray(5) { 0.2f }
    private var thinkingRotation = 0f
    private var idlePulsePhase = 0f

    // Subviews
    private val contentLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(10), 0)
    }

    private val ivLogo = ImageView(context).apply {
        setImageResource(R.mipmap.ic_launcher_round)
        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
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
        setTextColor(Color.argb(190, 180, 200, 230))
        textSize = 10f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.MARQUEE
        isSelected = true
        visibility = View.GONE
    }

    // Speaking Action Controls
    private val controlsLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
    }

    private val btnPause = ImageView(context).apply {
        setImageResource(android.R.drawable.ic_media_pause)
        setColorFilter(Color.rgb(0, 229, 255))
        layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            marginEnd = dp(6)
        }
    }

    private val btnStop = ImageView(context).apply {
        setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        setColorFilter(Color.rgb(255, 64, 129))
        layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
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
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
            marginEnd = dp(6)
        }
    }

    private val btnMusicNext = ImageView(context).apply {
        setImageResource(android.R.drawable.ic_media_next)
        setColorFilter(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
    }

    // Listeners
    var onIslandClickListener: (() -> Unit)? = null
    var onIslandLongClickListener: (() -> Unit)? = null
    var onStopClickListener: (() -> Unit)? = null
    var onPauseClickListener: (() -> Unit)? = null
    var onMusicPlayPauseListener: (() -> Unit)? = null
    var onMusicNextListener: (() -> Unit)? = null
    var onNotificationClickListener: ((notificationId: String) -> Unit)? = null
    var onNotificationDismissListener: ((notificationId: String) -> Unit)? = null

    private var activeNotificationId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAnimating = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            updateVisualizer()
            invalidate()
            if (isAnimating || currentMode != IslandMode.IDLE || idlePulsePhase > 0) {
                mainHandler.postDelayed(this, 16) // ~60fps
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

        contentLayout.addView(ivLogo)
        contentLayout.addView(textLayout)
        contentLayout.addView(controlsLayout)
        contentLayout.addView(musicControlsLayout)

        val flParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }
        addView(contentLayout, flParams)

        setupListeners()
        setMode(IslandMode.IDLE, animate = false)
        startFrameTicker()
    }

    private fun setupListeners() {
        btnStop.setOnClickListener {
            onStopClickListener?.invoke()
        }
        btnPause.setOnClickListener {
            onPauseClickListener?.invoke()
        }
        btnMusicPlayPause.setOnClickListener {
            onMusicPlayPauseListener?.invoke()
        }
        btnMusicNext.setOnClickListener {
            onMusicNextListener?.invoke()
        }

        setOnLongClickListener {
            onIslandLongClickListener?.invoke()
            true
        }

        setOnClickListener {
            when (currentMode) {
                IslandMode.IDLE -> onIslandClickListener?.invoke()
                IslandMode.NOTIFICATION -> activeNotificationId?.let { id -> onNotificationClickListener?.invoke(id) }
                else -> onIslandClickListener?.invoke()
            }
        }
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
            IslandMode.NOTIFICATION -> Pair(notificationWidth, notificationHeight)
            IslandMode.MUSIC -> Pair(musicWidth, musicHeight)
            IslandMode.CALL -> Pair(callWidth, callHeight)
        }

        updateContentForMode(mode)

        if (animate) {
            animateToSize(targetW.toFloat(), targetH.toFloat())
        } else {
            currentWidth = targetW.toFloat()
            currentHeight = targetH.toFloat()
            currentCornerRadius = currentHeight / 2f
            requestLayout()
            invalidate()
        }
    }

    private fun updateContentForMode(mode: IslandMode) {
        controlsLayout.visibility = View.GONE
        musicControlsLayout.visibility = View.GONE
        tvSubtitle.visibility = View.GONE

        when (mode) {
            IslandMode.IDLE -> {
                tvTitle.text = "ZARA"
                tvTitle.textSize = 11f
                tvSubtitle.visibility = View.GONE
                ivLogo.visibility = View.VISIBLE
                contentLayout.setPadding(dp(10), 0, dp(10), 0)
            }
            IslandMode.LISTENING -> {
                tvTitle.text = "ZARA is Listening…"
                tvTitle.textSize = 12f
                tvSubtitle.text = "Speak your request"
                tvSubtitle.visibility = View.VISIBLE
                ivLogo.visibility = View.VISIBLE
                contentLayout.setPadding(dp(12), 0, dp(14), 0)
            }
            IslandMode.THINKING -> {
                tvTitle.text = "ZARA is Thinking…"
                tvTitle.textSize = 12f
                tvSubtitle.text = "Processing request"
                tvSubtitle.visibility = View.VISIBLE
                ivLogo.visibility = View.VISIBLE
                contentLayout.setPadding(dp(12), 0, dp(14), 0)
            }
            IslandMode.SPEAKING -> {
                tvTitle.text = "ZARA is Speaking…"
                tvTitle.textSize = 12f
                tvSubtitle.text = "Replying to you"
                tvSubtitle.visibility = View.VISIBLE
                controlsLayout.visibility = View.VISIBLE
                ivLogo.visibility = View.VISIBLE
                contentLayout.setPadding(dp(12), 0, dp(12), 0)
            }
            IslandMode.NOTIFICATION -> {
                ivLogo.visibility = View.VISIBLE
                tvSubtitle.visibility = View.VISIBLE
                contentLayout.setPadding(dp(14), 0, dp(14), 0)
            }
            IslandMode.MUSIC -> {
                ivLogo.visibility = View.VISIBLE
                tvSubtitle.visibility = View.VISIBLE
                musicControlsLayout.visibility = View.VISIBLE
                contentLayout.setPadding(dp(12), 0, dp(12), 0)
            }
            IslandMode.CALL -> {
                ivLogo.visibility = View.VISIBLE
                tvSubtitle.visibility = View.VISIBLE
                contentLayout.setPadding(dp(14), 0, dp(14), 0)
            }
        }
    }

    fun setLiveTranscript(transcript: String) {
        if (currentMode == IslandMode.LISTENING && transcript.isNotBlank()) {
            tvSubtitle.text = transcript
            tvSubtitle.visibility = View.VISIBLE
        }
    }

    fun setNotification(notification: IslandNotificationData) {
        activeNotificationId = notification.id
        tvTitle.text = notification.title.ifBlank { notification.appName }
        tvSubtitle.text = notification.text
        tvSubtitle.visibility = View.VISIBLE
        if (notification.icon != null) {
            ivLogo.setImageDrawable(notification.icon)
        } else {
            ivLogo.setImageResource(R.mipmap.ic_launcher_round)
        }
        setMode(IslandMode.NOTIFICATION, animate = true)
    }

    fun setMusic(music: IslandMusicData) {
        tvTitle.text = music.title
        tvSubtitle.text = music.artist
        tvSubtitle.visibility = View.VISIBLE
        if (music.albumArt != null) {
            ivLogo.setImageBitmap(music.albumArt)
        } else {
            ivLogo.setImageResource(android.R.drawable.ic_media_play)
        }
        btnMusicPlayPause.setImageResource(
            if (music.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        setMode(IslandMode.MUSIC, animate = true)
    }

    fun setAudioLevel(level: Float, frequency: AudioFrequencyData? = null) {
        targetAudioLevel = level.coerceIn(0f, 1f)
    }

    private fun updateVisualizer() {
        // Smoothly interpolate audio amplitude
        animatedAudioLevel = (animatedAudioLevel * 0.7f + targetAudioLevel * 0.3f).coerceIn(0f, 1f)
        targetAudioLevel *= 0.88f // Decay

        // Animate frequency bars
        val baseFreq = animatedAudioLevel
        for (i in barHeights.indices) {
            val variance = (sin(System.currentTimeMillis() * 0.015f + i * 1.2f) * 0.5f + 0.5f).toFloat()
            val target = (baseFreq * 0.75f + variance * 0.25f).coerceIn(0.15f, 1.0f)
            barHeights[i] = barHeights[i] * 0.65f + target * 0.35f
        }

        // Orbit rotation for Thinking state
        if (currentMode == IslandMode.THINKING) {
            thinkingRotation = (thinkingRotation + 5f) % 360f
        }

        // Idle pulse
        if (currentMode == IslandMode.IDLE) {
            idlePulsePhase = (idlePulsePhase + 0.05f) % (2f * Math.PI.toFloat())
        }
    }

    private fun animateToSize(targetW: Float, targetH: Float) {
        sizeAnimator?.cancel()
        val startW = currentWidth
        val startH = currentHeight
        val startR = currentCornerRadius
        val targetR = targetH / 2f

        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
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
        val w = width.toFloat()
        val h = height.toFloat()
        rectBounds.set(0f, 0f, w, h)

        // 1. Draw Glassmorphism Obsidian Pill Background
        canvas.drawRoundRect(rectBounds, currentCornerRadius, currentCornerRadius, backgroundPaint)

        // 2. Draw Dual-Color Neon Gradient Border (Cyan #00E5FF to Purple #B388FF)
        val gradient = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.argb(220, 0, 229, 255),  // Neon Cyan
                Color.argb(160, 123, 31, 162), // Electric Purple
                Color.argb(220, 179, 136, 255) // Cyber Violet
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        borderPaint.shader = gradient
        val inset = borderPaint.strokeWidth / 2f
        rectBounds.inset(inset, inset)
        canvas.drawRoundRect(rectBounds, currentCornerRadius, currentCornerRadius, borderPaint)

        // 3. Mode-Specific Drawing Elements
        when (currentMode) {
            IslandMode.LISTENING -> drawWaveform(canvas, w, h, isListening = true)
            IslandMode.SPEAKING -> drawWaveform(canvas, w, h, isListening = false)
            IslandMode.THINKING -> drawThinkingOrbit(canvas, w, h)
            IslandMode.IDLE -> drawIdlePulse(canvas, w, h)
            else -> {}
        }

        super.onDraw(canvas)
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
                Color.argb(240, 0, 229, 255) // Neon Cyan for listening
            } else {
                Color.argb(240, 179, 136, 255) // Electric Purple for speaking
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
    }

    private fun drawIdlePulse(canvas: Canvas, w: Float, h: Float) {
        val pulseAlpha = ((sin(idlePulsePhase) * 0.35f + 0.65f) * 255).toInt()
        val cx = w - dp(14)
        val cy = h / 2f
        val r = dp(3).toFloat()

        glowPaint.color = Color.argb(pulseAlpha, 0, 229, 255)
        canvas.drawCircle(cx, cy, r, glowPaint)
    }
}
