package com.aetherx.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aetherx.assistant.ui.theme.CyberCyan
import com.aetherx.assistant.ui.theme.CyberMagenta
import com.aetherx.assistant.ui.theme.CyberViolet

@Composable
fun CyberOrb(
    modifier: Modifier = Modifier,
    energyLevel: Float = 0.2f,
    isListening: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbPulse")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    val dynamicScale = (breathingScale + energyLevel * 0.8f).coerceIn(0.8f, 1.8f)

    Canvas(modifier = modifier.size(240.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = (size.minDimension / 3.2f) * dynamicScale

        // Ambient Outer Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CyberCyan.copy(alpha = 0.35f + energyLevel * 0.4f),
                    CyberViolet.copy(alpha = 0.2f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 1.5f
            ),
            radius = baseRadius * 1.5f,
            center = center
        )

        // Core Quantum Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.9f),
                    CyberCyan.copy(alpha = 0.8f),
                    CyberViolet
                ),
                center = center,
                radius = baseRadius
            ),
            radius = baseRadius,
            center = center
        )

        // Holographic Orbital Rings
        drawCircle(
            color = CyberCyan.copy(alpha = 0.7f),
            radius = baseRadius * 1.25f,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        drawCircle(
            color = CyberMagenta.copy(alpha = 0.5f),
            radius = baseRadius * 1.4f,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
