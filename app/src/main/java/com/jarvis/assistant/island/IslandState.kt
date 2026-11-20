package com.jarvis.assistant.island

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

enum class IslandMode {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    NOTIFICATION,
    MUSIC,
    CALL
}

data class IslandNotificationData(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val icon: Drawable? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class IslandMusicData(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val albumArt: Bitmap? = null,
    val packageName: String = ""
)

data class IslandCallData(
    val callerName: String,
    val callerNumber: String,
    val isIncoming: Boolean,
    val durationSeconds: Long = 0L
)
