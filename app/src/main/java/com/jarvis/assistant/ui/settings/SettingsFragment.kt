package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.FragmentSettingsBinding
import com.jarvis.assistant.ui.memory.MemoryActivity
import com.jarvis.assistant.ui.permission.PermissionActivity
import com.jarvis.assistant.ui.voice.VoiceEnrollmentActivity

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val preferences by lazy { JarvisApp.instance.preferences }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)

        loadUserData()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
            loadUserData()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            binding.root.visibility = View.VISIBLE
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
            loadUserData()
        } else if (hidden && _binding != null) {
            binding.root.visibility = View.GONE
        }
    }

    private fun loadUserData() {
        val userName = preferences.userName.ifBlank { "Rehaan" }
        binding.tvProfileName.text = userName
        binding.tvProfileSubtitle.text = "ZARA OPERATOR • PRIMARY USER"

        // Owner Voice Profile Status
        val hasVoiceProfile = preferences.hasOwnerVoiceProfile()
        if (hasVoiceProfile) {
            binding.tvOwnerVoiceBadge.text = "ACTIVE"
            binding.tvOwnerVoiceBadge.setTextColor(Color.parseColor("#10B981"))
            binding.tvOwnerVoiceBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge_green)
            binding.tvOwnerVoiceSubtitle.text = "Voice Profile Active • Only you can speak"

            binding.tvProfileVoiceStatus.text = "VOICE ENROLLED"
            binding.tvProfileVoiceStatus.setTextColor(Color.parseColor("#10B981"))
            binding.chipProfileVoiceStatus.setBackgroundResource(R.drawable.bg_jarvis_status_badge_green)
            binding.ivProfileVoiceBadgeIcon.setImageResource(R.drawable.ic_verified_badge)
            binding.ivProfileVoiceBadgeIcon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
        } else {
            binding.tvOwnerVoiceBadge.text = "SETUP"
            binding.tvOwnerVoiceBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_sapphire))
            binding.tvOwnerVoiceBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
            binding.tvOwnerVoiceSubtitle.text = "Not enrolled • Tap to record voice profile"

            binding.tvProfileVoiceStatus.text = "ENROLL VOICE"
            binding.tvProfileVoiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_sapphire))
            binding.chipProfileVoiceStatus.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
            binding.ivProfileVoiceBadgeIcon.setImageResource(R.drawable.ic_settings_owner_voice)
            binding.ivProfileVoiceBadgeIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.accent_sapphire)
            )
        }

        // Language
        binding.tvCurrentLanguage.text = preferences.language

        // Persona & Voice
        val currentPersona = preferences.personality
        val currentVoice = preferences.voice
        binding.tvCurrentPersona.text = "$currentPersona • Voice: $currentVoice"
        binding.tvProfilePersonaChip.text = currentPersona.uppercase()
        binding.tvVoiceStudioSubtitle.text = "Active Voice: $currentVoice • Strict Lock Active"

        // Silent Execution Mode
        binding.switchSilentMode.isChecked = preferences.isSilentExecutionMode

        // Always Ready (Wake Word)
        binding.switchAlwaysReady.isChecked = com.jarvis.assistant.audio.WakeWordManager.isEnabled(requireContext())

        // Model
        val modelDisplay = preferences.aiModel.removePrefix("models/")
        binding.tvCurrentModel.text = "$modelDisplay • Live WebSocket"

        // Theme
        binding.tvCurrentTheme.text = when (preferences.appTheme) {
            "dark" -> "Dark Theme"
            "red" -> "Red Theme"
            "sunset" -> "Sunset Theme"
            "custom" -> "Custom (${preferences.customThemeColor})"
            else -> "Light Theme"
        }

        // Origin Island
        val hasOverlay = com.jarvis.assistant.island.ZaraIslandManager.getInstance(requireContext()).hasOverlayPermission()
        val islandActive = preferences.isIslandEnabled && hasOverlay
        binding.tvOriginIslandBadge.text = if (islandActive) "ACTIVE" else if (preferences.isIslandEnabled) "GRANT PERMISSION" else "OFF"
        binding.tvOriginIslandBadge.setTextColor(if (islandActive) Color.parseColor("#10B981") else ContextCompat.getColor(requireContext(), R.color.accent_sapphire))
        binding.tvOriginIslandBadge.setBackgroundResource(if (islandActive) R.drawable.bg_jarvis_status_badge_green else R.drawable.bg_jarvis_status_badge)
    }

    private fun setupClickListeners() {
        // Profile Card -> Change Name
        binding.cardProfile.setOnClickListener {
            showEditNameDialog()
        }

        // Voice Status Chip on Profile Card -> Opens Voice Enrollment
        binding.chipProfileVoiceStatus.setOnClickListener {
            startActivity(Intent(requireContext(), VoiceEnrollmentActivity::class.java))
        }

        // Section 1: AI & Voice Intelligence
        binding.rowOwnerVoice.setOnClickListener {
            startActivity(Intent(requireContext(), VoiceEnrollmentActivity::class.java))
        }

        binding.rowLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.rowPersonaVoice.setOnClickListener {
            startActivity(Intent(requireContext(), PersonaActivity::class.java))
        }

        binding.rowVoiceStudio.setOnClickListener {
            startActivity(Intent(requireContext(), com.jarvis.assistant.ui.voice.VoiceStudioActivity::class.java))
        }

        binding.switchSilentMode.setOnCheckedChangeListener { _, isChecked ->
            preferences.isSilentExecutionMode = isChecked
            val msg = if (isChecked) "Silent Execution Mode Enabled" else "Silent Execution Mode Disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.switchAlwaysReady.setOnCheckedChangeListener { _, isChecked ->
            com.jarvis.assistant.audio.WakeWordManager.setEnabled(requireContext(), isChecked)
            val msg = if (isChecked) "Always Ready (Hey Zara) Enabled" else "Always Ready (Hey Zara) Disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.rowOemBackgroundGuide.setOnClickListener {
            showOemBackgroundDialog()
        }

        // Section 2: System & Intelligence Data
        binding.rowApiConnection.setOnClickListener {
            startActivity(Intent(requireContext(), ApiConnectionActivity::class.java))
        }

        binding.rowVoiceDiagnostics.setOnClickListener {
            startActivity(Intent(requireContext(), com.jarvis.assistant.ui.diagnostics.VoiceDiagnosticsActivity::class.java))
        }

        binding.rowMemory.setOnClickListener {
            startActivity(Intent(requireContext(), MemoryActivity::class.java))
        }

        binding.rowPermissions.setOnClickListener {
            startActivity(Intent(requireContext(), PermissionActivity::class.java))
        }

        binding.rowOverlayAvatars.setOnClickListener {
            startActivity(Intent(requireContext(), OverlayAvatarActivity::class.java))
        }

        binding.rowOriginIsland.setOnClickListener {
            showOriginIslandDialog()
        }

        binding.rowDefaultAssistant.setOnClickListener {
            openDefaultAssistantSettings()
        }
        binding.rowApplications.setOnClickListener {
            startActivity(Intent(requireContext(), HandsFreeActivity::class.java))
        }

        // Section 3: Appearance & Security
        binding.rowAppTheme.setOnClickListener {
            ThemeSelectionBottomSheet.newInstance().show(childFragmentManager, ThemeSelectionBottomSheet.TAG)
        }

        binding.rowPrivacyPolicy.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyShieldActivity::class.java))
        }

        binding.rowAppIdentity.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Zara App Identity")
                .setMessage(com.jarvis.assistant.security.AppIdentityVerifier.report(requireContext()))
                .setPositiveButton("Close", null)
                .show()
        }

        // Social Media Community Buttons
        binding.btnSocialTelegram.setOnClickListener {
            openUrl("https://t.me/Hexvora777")
        }

        binding.btnSocialInstagram.setOnClickListener {
            openUrl("https://instagram.com/Hexvora777")
        }

        binding.btnSocialYoutube.setOnClickListener {
            openUrl("https://t.me/Hexvora777")
        }

        binding.tvFooterCredit.text = "ZARA AI OS • v2.2 PRO\nArchitected & Developed exclusively by Mohanraj\nTelegram: t.me/Hexvora777 • Instagram: @Hexvora777"
        binding.tvFooterCredit.setOnClickListener {
            openUrl("https://t.me/Hexvora777")
        }
    }

    
    private fun showOriginIslandDialog() {
        val islandManager = com.jarvis.assistant.island.ZaraIslandManager.getInstance(requireContext())
        val hasOverlay = islandManager.hasOverlayPermission()

        val options = arrayOf(
            if (preferences.isIslandEnabled) "Disable Origin Island" else "Enable Origin Island",
            if (hasOverlay) "Overlay Permission: Granted ✓" else "Grant Display Over Other Apps Permission",
            "Grant Notification Listener Permission",
            "Test Mode: Activate Voice Island",
            "Test Mode: Music Marquee Preview"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Origin Island Configuration")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        preferences.isIslandEnabled = !preferences.isIslandEnabled
                        if (preferences.isIslandEnabled) {
                            islandManager.showIsland()
                        } else {
                            islandManager.hideIsland()
                        }
                        loadUserData()
                    }
                    1 -> {
                        islandManager.requestOverlayPermission(requireContext())
                    }
                    2 -> {
                        try {
                            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(requireContext(), "Could not open Notification settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> {
                        islandManager.showIsland()
                        val intent = Intent(requireContext(), com.jarvis.assistant.background.JarvisConversationService::class.java).apply {
                            action = com.jarvis.assistant.background.JarvisConversationService.ACTION_START_CONVERSATION
                        }
                        androidx.core.content.ContextCompat.startForegroundService(requireContext(), intent)
                    }
                    4 -> {
                        islandManager.showIsland()
                        islandManager.updateMusic(
                            com.jarvis.assistant.island.IslandMusicData(
                                title = "Starboy (Official)",
                                artist = "The Weeknd ft. Daft Punk",
                                isPlaying = true
                            )
                        )
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openDefaultAssistantSettings() {
        val intents = listOf(
            android.content.Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
            android.content.Intent("android.settings.VOICE_INPUT_SETTINGS"),
            android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    private fun showLanguageDialog() {
        val languages = GeminiConstants.SUPPORTED_LANGUAGES.toTypedArray()
        val currentIndex = languages.indexOfFirst { it.equals(preferences.language, ignoreCase = true) }.let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Conversation Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val chosen = languages[which]
                preferences.language = chosen
                binding.tvCurrentLanguage.text = chosen
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWakeWordDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Wake Words & Sensitivity")
            .setMessage("• Wake Words: 'Zara', 'Hey Zara'\n• Acoustic Trigger: Double Clap detection\n• Standby Mode: Low-power local Vosk offline engine\n\nWhen standby listening is active, Zara awakens instantly upon hearing your voice.")
            .setPositiveButton("Got It", null)
            .show()
    }

    private fun showAppAutomationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Autonomous App Automation")
            .setMessage("Zara is integrated with your device capabilities:\n\n• WhatsApp: Hands-free messaging & calls\n• YouTube: Search and playback automation\n• Phone Controls: Flashlight, Volume, Brightness, Bluetooth\n• Apps & Tools: Launch, search, and manage apps via voice")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showEditNameDialog() {
        val input = EditText(requireContext()).apply {
            setText(preferences.userName.ifBlank { "Rehaan" })
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Operator Name")
            .setMessage("Enter the name Zara should use to address you:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    preferences.userName = newName
                    loadUserData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOemBackgroundDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Vivo & OEM Background Setup")
            .setMessage(
                "To ensure 'Hey Zara' wakes up reliably 24/7 in the background without being restricted:\n\n" +
                "1. Autostart: Allow Zara in Autostart / Background launch.\n" +
                "2. Battery: Set Battery usage to 'Unrestricted' or 'High Background Power Consumption'.\n" +
                "3. Lock in Recents: Lock Zara in Recent Apps screen so it isn't cleared."
            )
            .setPositiveButton("Open App Settings") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    com.jarvis.assistant.util.PermissionHelper.requestBatteryOptimization(requireContext())
                }
            }
            .setNeutralButton("Battery Optimization") { _, _ ->
                com.jarvis.assistant.util.PermissionHelper.requestBatteryOptimization(requireContext())
            }
            .setNegativeButton("Done", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
