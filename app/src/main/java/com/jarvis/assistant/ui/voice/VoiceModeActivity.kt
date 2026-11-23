package com.jarvis.assistant.ui.voice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.ZaraVoicePreferenceManager
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.databinding.ActivityVoiceModeBinding
import kotlinx.coroutines.launch

class VoiceModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceModeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Minimize / Close to Island (Keep continuous session alive!)
        binding.btnVoiceClose.setOnClickListener {
            finish()
        }

        // End Session explicitly
        binding.btnVoiceEndSession.setOnClickListener {
            val stopIntent = Intent(this, JarvisConversationService::class.java).apply {
                action = JarvisConversationService.ACTION_STOP
            }
            startService(stopIntent)
            finish()
        }

        // Toggle Mic Mute
        binding.btnVoiceMicToggle.setOnClickListener {
            val prefs = JarvisApp.instance.preferences
            val newMuted = !prefs.isMicMuted
            prefs.isMicMuted = newMuted
            JarvisConversationService.setMicMuted(newMuted)
            updateMicState(newMuted)
        }

        // Interrupt active speech
        binding.btnVoiceInterrupt.setOnClickListener {
            JarvisConversationService.interruptPlayback()
        }

        // Display current locked voice
        val voiceName = ZaraVoicePreferenceManager.getGeminiVoiceName(this)
        val currentGender = ZaraVoicePreferenceManager.getSelectedGender(this)
        val genderShort = if (currentGender == ZaraVoicePreferenceManager.VoiceGender.FEMALE) "F" else "M"
        binding.tvSelectedVoiceLock.text = "$voiceName ($genderShort)"

        // Ensure session is started if not already running
        if (!JarvisConversationService.isSessionOn.value) {
            val startIntent = Intent(this, JarvisConversationService::class.java).apply {
                action = JarvisConversationService.ACTION_START_CONVERSATION
            }
            startService(startIntent)
        }

        updateMicState(JarvisApp.instance.preferences.isMicMuted)
        observeSessionState()
    }

    private fun updateMicState(isMuted: Boolean) {
        if (isMuted) {
            binding.ivVoiceMicStatus.setImageResource(R.drawable.ic_mic_mute_jarvis)
            binding.ivVoiceMicStatus.setColorFilter(getColor(R.color.zara_status_error))
        } else {
            binding.ivVoiceMicStatus.setImageResource(R.drawable.ic_mic_jarvis)
            binding.ivVoiceMicStatus.setColorFilter(getColor(R.color.zara_text_primary))
        }
    }

    private fun observeSessionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    JarvisConversationService.conversationState.collect { state ->
                        when (state) {
                            ConversationState.LISTENING -> {
                                binding.tvVoiceStateIndicator.text = "LISTENING (CONTINUOUS)"
                                binding.tvVoiceStateIndicator.setTextColor(getColor(R.color.zara_status_success))
                                binding.viewStateDot.setBackgroundResource(R.drawable.bg_zara_badge_success)
                                binding.viewAmbientHalo.animate().scaleX(1.15f).scaleY(1.15f).setDuration(400).start()
                            }
                            ConversationState.THINKING, ConversationState.PROCESSING -> {
                                binding.tvVoiceStateIndicator.text = "THINKING..."
                                binding.tvVoiceStateIndicator.setTextColor(getColor(R.color.zara_status_warning))
                                binding.viewStateDot.setBackgroundColor(getColor(R.color.zara_status_warning))
                                binding.viewAmbientHalo.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
                            }
                            ConversationState.SPEAKING -> {
                                binding.tvVoiceStateIndicator.text = "SPEAKING"
                                binding.tvVoiceStateIndicator.setTextColor(getColor(R.color.zara_accent_purple))
                                binding.viewStateDot.setBackgroundColor(getColor(R.color.zara_accent_purple))
                                binding.viewAmbientHalo.animate().scaleX(1.3f).scaleY(1.3f).setDuration(300).start()
                            }
                            else -> {
                                binding.tvVoiceStateIndicator.text = "READY"
                                binding.tvVoiceStateIndicator.setTextColor(getColor(R.color.zara_text_muted))
                                binding.viewAmbientHalo.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                            }
                        }
                    }
                }
            }
        }
    }
}
