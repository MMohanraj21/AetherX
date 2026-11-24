package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.audio.ZaraVoicePreferenceManager
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.ActivityPersonaBinding
import com.jarvis.assistant.ui.voice.VoiceStudioActivity
import com.jarvis.assistant.util.ThemeManager

class PersonaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonaBinding
    private val preferences by lazy { JarvisApp.instance.preferences }

    private var currentSelectedPersona = GeminiConstants.PERSONALITY_ASSISTANT

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPersonaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        setupUI()
        loadInitialState()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateVoiceCard()
        updatePersonaPreview()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadInitialState() {
        val current = preferences.personality
        val initialPersona = when (current) {
            GeminiConstants.PERSONALITY_GIRLFRIEND -> GeminiConstants.PERSONALITY_GIRLFRIEND
            GeminiConstants.PERSONALITY_BOYFRIEND -> GeminiConstants.PERSONALITY_BOYFRIEND
            GeminiConstants.PERSONALITY_TECHGPT_X -> GeminiConstants.PERSONALITY_TECHGPT_X
            else -> GeminiConstants.PERSONALITY_ASSISTANT
        }
        selectPersona(initialPersona, updateVoiceDefault = false)

        binding.etUserName.setText(preferences.userName.ifBlank { "Mohanraj" })

        updateVoiceCard()
        updatePersonaPreview()
        binding.scrollPersona.post { binding.scrollPersona.scrollTo(0, 0) }
    }

    private fun setupListeners() {
        binding.etUserName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updatePersonaPreview()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.cardPersonaJarvis.setOnClickListener {
            selectPersona(GeminiConstants.PERSONALITY_ASSISTANT, updateVoiceDefault = false)
        }

        binding.cardPersonaGirlfriend.setOnClickListener {
            selectPersona(GeminiConstants.PERSONALITY_GIRLFRIEND, updateVoiceDefault = true)
        }

        binding.cardPersonaBoyfriend.setOnClickListener {
            selectPersona(GeminiConstants.PERSONALITY_BOYFRIEND, updateVoiceDefault = true)
        }

        binding.cardPersonaTechGpt.setOnClickListener {
            selectPersona(GeminiConstants.PERSONALITY_TECHGPT_X, updateVoiceDefault = false)
        }

        val openVoiceStudioListener = {
            startActivity(Intent(this, VoiceStudioActivity::class.java))
        }

        binding.cardOpenVoiceStudio.setOnClickListener { openVoiceStudioListener() }
        binding.cardActiveVoiceProfile.setOnClickListener { openVoiceStudioListener() }
        binding.btnOpenVoiceStudioFull.setOnClickListener { openVoiceStudioListener() }

        binding.btnActivatePersona.setOnClickListener {
            saveAndActivate()
        }
    }

    private fun updateVoiceCard() {
        if (!::binding.isInitialized) return
        val profile = ZaraVoicePreferenceManager.getSelectedVoiceProfile(this)
        binding.tvActiveVoiceName.text = profile.displayName
        binding.tvActiveVoiceDesc.text = "${profile.description} • ${profile.gender.name} AI Voice"
        binding.tvVoiceGenderBadge.text = "${profile.gender.name} LOCKED"
    }

    private fun selectPersona(personaKey: String, updateVoiceDefault: Boolean) {
        currentSelectedPersona = personaKey

        binding.rbSelectJarvis.isChecked = (personaKey == GeminiConstants.PERSONALITY_ASSISTANT)
        binding.rbSelectGirlfriend.isChecked = (personaKey == GeminiConstants.PERSONALITY_GIRLFRIEND)
        binding.rbSelectBoyfriend.isChecked = (personaKey == GeminiConstants.PERSONALITY_BOYFRIEND)
        binding.rbSelectTechGpt.isChecked = (personaKey == GeminiConstants.PERSONALITY_TECHGPT_X)

        if (updateVoiceDefault) {
            when (personaKey) {
                GeminiConstants.PERSONALITY_GIRLFRIEND -> {
                    ZaraVoicePreferenceManager.setSelectedGender(this, ZaraVoicePreferenceManager.VoiceGender.FEMALE)
                }
                GeminiConstants.PERSONALITY_BOYFRIEND -> {
                    ZaraVoicePreferenceManager.setSelectedGender(this, ZaraVoicePreferenceManager.VoiceGender.MALE)
                }
            }
            updateVoiceCard()
        }
        if (::binding.isInitialized) updatePersonaPreview()
    }

    private fun updatePersonaPreview() {
        if (!::binding.isInitialized) return
        val persona = when (currentSelectedPersona) {
            GeminiConstants.PERSONALITY_GIRLFRIEND -> "Pari"
            GeminiConstants.PERSONALITY_BOYFRIEND -> "Kabir"
            GeminiConstants.PERSONALITY_TECHGPT_X -> "Mohanraj"
            else -> "Zara Pro"
        }
        val activeVoice = ZaraVoicePreferenceManager.getSelectedVoiceProfile(this).displayName
        val operator = binding.etUserName.text?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: "Mohanraj"
        binding.tvPersonaPreview.text = "$persona · $activeVoice"
        binding.tvPersonaGreeting.text = "Ready to help, $operator."
    }

    private fun saveAndActivate() {
        val userName = binding.etUserName.text?.toString()?.trim()
        preferences.userName = if (!userName.isNullOrBlank()) userName else "Mohanraj"

        val activeProfile = ZaraVoicePreferenceManager.getSelectedVoiceProfile(this)
        preferences.voice = activeProfile.id
        preferences.voiceGender = activeProfile.gender.name
        preferences.personality = currentSelectedPersona

        val personaDisplayName = when (currentSelectedPersona) {
            GeminiConstants.PERSONALITY_GIRLFRIEND -> "Pari (Girlfriend Mode)"
            GeminiConstants.PERSONALITY_BOYFRIEND -> "Kabir (Boyfriend Mode)"
            GeminiConstants.PERSONALITY_TECHGPT_X -> "Zara AI Neural Core"
            else -> "Zara Pro"
        }

        Toast.makeText(
            this,
            "Activated: $personaDisplayName with ${activeProfile.displayName} Voice",
            Toast.LENGTH_LONG
        ).show()

        finish()
    }
}
