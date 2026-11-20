package com.jarvis.assistant.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.jarvis.assistant.ui.home.MainActivity

class ZaraVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onCreate() {
        super.onCreate()
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_ASSIST
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_VOICE_TRIGGER", true)
            }
            startAssistantActivity(intent)
        } catch (e: Exception) {
            hide()
        }
    }
}
