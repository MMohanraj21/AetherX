package com.aetherx.voice

import kotlin.math.sqrt

object AudioEnergyMeter {
    fun calculateRms(buffer: ByteArray, readBytes: Int): Float {
        if (readBytes <= 0) return 0f
        var sum = 0.0
        val sampleCount = readBytes / 2
        for (i in 0 until sampleCount) {
            val sample = (buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)
            sum += sample.toDouble() * sample.toDouble()
        }
        val mean = sum / sampleCount
        val rms = sqrt(mean).toFloat()
        // Normalized 0.0 to 1.0 (assuming 16-bit PCM max amplitude 32768)
        return (rms / 32768f).coerceIn(0f, 1f)
    }
}
