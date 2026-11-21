package com.jarvis.assistant.audio.realtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Audio focus manager for ZARA AI v5.0.
 *
 * Requests transient exclusive audio focus during active conversational voice sessions
 * and releases it cleanly when entering standby, pausing background music and media
 * apps during voice interactions.
 */
class AudioFocusController(private val context: Context) {

    companion object {
        private const val TAG = "AudioFocusController"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                hasFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently")
                hasFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost transiently (can duck)")
                hasFocus = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasFocus = true
            }
        }
    }

    fun requestFocus(exclusive: Boolean = true): Boolean {
        if (hasFocus) return true
        val am = audioManager ?: return false

        val focusGain = if (exclusive) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
        } else {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        }

        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        val result = am.requestAudioFocus(request)
        hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        if (hasFocus) {
            audioFocusRequest = request
            Log.d(TAG, "Audio focus granted (exclusive=$exclusive)")
        } else {
            Log.w(TAG, "Audio focus request rejected: $result")
        }
        return hasFocus
    }

    fun releaseFocus() {
        if (!hasFocus) return
        val am = audioManager ?: return
        audioFocusRequest?.let {
            am.abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
        hasFocus = false
        Log.d(TAG, "Audio focus released")
    }

    fun hasActiveFocus(): Boolean = hasFocus
}
