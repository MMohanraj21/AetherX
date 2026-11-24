package com.jarvis.assistant.ui.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.ZaraVoicePreferenceManager
import com.jarvis.assistant.audio.ZaraVoicePreferenceManager.VoiceGender
import com.jarvis.assistant.audio.ZaraVoicePreferenceManager.VoiceProfile
import com.jarvis.assistant.databinding.ActivityVoiceStudioBinding
import com.jarvis.assistant.databinding.ItemVoiceCardBinding
import com.jarvis.assistant.network.GeminiRestClient
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceStudioActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceStudioActivity"
        private const val SAMPLE_RATE = 24000
    }

    private lateinit var binding: ActivityVoiceStudioBinding
    private val preferences by lazy { JarvisApp.instance.preferences }

    private var currentGenderTab = VoiceGender.FEMALE
    private var currentlyPlayingVoiceId: String? = null
    private var activeAudioTrack: AudioTrack? = null
    private var auditionJob: Job? = null
    private lateinit var voiceAdapter: VoiceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize active tab based on saved preference
        val activeGender = ZaraVoicePreferenceManager.getSelectedGender(this)
        currentGenderTab = activeGender

        setupUI()
        setupRecyclerView()
        updateTabSelectionUI()
        updateBottomActiveVoiceBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAuditionPlayback()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnDone.setOnClickListener {
            finish()
        }

        binding.tabFemale.setOnClickListener {
            if (currentGenderTab != VoiceGender.FEMALE) {
                stopAuditionPlayback()
                currentGenderTab = VoiceGender.FEMALE
                updateTabSelectionUI()
                voiceAdapter.submitList(ZaraVoicePreferenceManager.FEMALE_PROFILES)
            }
        }

        binding.tabMale.setOnClickListener {
            if (currentGenderTab != VoiceGender.MALE) {
                stopAuditionPlayback()
                currentGenderTab = VoiceGender.MALE
                updateTabSelectionUI()
                voiceAdapter.submitList(ZaraVoicePreferenceManager.MALE_PROFILES)
            }
        }
    }

    private fun updateTabSelectionUI() {
        if (currentGenderTab == VoiceGender.FEMALE) {
            binding.tabFemale.setBackgroundResource(R.drawable.bg_pill_accent)
            binding.tabFemale.setTextColor(ContextCompat.getColor(this, R.color.studio_text_primary))
            binding.tabMale.setBackgroundResource(android.R.color.transparent)
            binding.tabMale.setTextColor(ContextCompat.getColor(this, R.color.studio_text_secondary))
        } else {
            binding.tabMale.setBackgroundResource(R.drawable.bg_pill_accent)
            binding.tabMale.setTextColor(ContextCompat.getColor(this, R.color.studio_text_primary))
            binding.tabFemale.setBackgroundResource(android.R.color.transparent)
            binding.tabFemale.setTextColor(ContextCompat.getColor(this, R.color.studio_text_secondary))
        }
    }

    private fun setupRecyclerView() {
        voiceAdapter = VoiceAdapter(
            onAuditionClick = { profile ->
                handleAuditionClick(profile)
            },
            onSelectClick = { profile ->
                selectVoice(profile)
            }
        )
        binding.rvVoices.layoutManager = LinearLayoutManager(this)
        binding.rvVoices.adapter = voiceAdapter

        val initialList = if (currentGenderTab == VoiceGender.FEMALE) {
            ZaraVoicePreferenceManager.FEMALE_PROFILES
        } else {
            ZaraVoicePreferenceManager.MALE_PROFILES
        }
        voiceAdapter.submitList(initialList)
    }

    private fun selectVoice(profile: VoiceProfile) {
        stopAuditionPlayback()
        ZaraVoicePreferenceManager.setSelectedGender(this, profile.gender, profile.id)
        preferences.voice = profile.id
        preferences.voiceGender = profile.gender.name

        voiceAdapter.notifyDataSetChanged()
        updateBottomActiveVoiceBar()

        Toast.makeText(
            this,
            "Active Voice: ${profile.displayName} (${profile.gender.name})\nStrict Gender Lock Active • Zero Fallback",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateBottomActiveVoiceBar() {
        val activeVoice = preferences.voice
        val activeGender = if (ZaraVoicePreferenceManager.isFemaleVoice(activeVoice)) "Female" else "Male"
        binding.tvActiveVoiceTitle.text = "Active Voice: $activeVoice"
        binding.tvActiveVoiceSubtitle.text = "$activeGender AI Voice • Strict Lock Active • Native Neural Audio"
    }

    private fun handleAuditionClick(profile: VoiceProfile) {
        if (currentlyPlayingVoiceId == profile.id) {
            // Already playing this voice -> stop
            stopAuditionPlayback()
            voiceAdapter.notifyDataSetChanged()
            return
        }

        stopAuditionPlayback()
        currentlyPlayingVoiceId = profile.id
        voiceAdapter.notifyDataSetChanged()

        auditionJob = lifecycleScope.launch {
            playVoiceAudition(profile)
        }
    }

    private suspend fun playVoiceAudition(profile: VoiceProfile) {
        val apiKey = preferences.apiKey
        val cacheFile = File(cacheDir, "audition_${profile.id}.pcm")

        val audioBytes: ByteArray? = withContext(Dispatchers.IO) {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    cacheFile.readBytes()
                } catch (e: Exception) {
                    null
                }
            } else if (apiKey.isNotBlank()) {
                val sampleText = profile.sampleTamilText
                val result = GeminiRestClient.synthesizeSpeech(
                    apiKey = apiKey,
                    voiceName = profile.id,
                    text = sampleText
                )
                if (result.isSuccess) {
                    val bytes = result.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        try {
                            cacheFile.writeBytes(bytes)
                        } catch (_: Exception) {}
                        bytes
                    } else null
                } else {
                    Log.w(TAG, "Gemini speech synthesis failed: ${result.exceptionOrNull()?.message}")
                    null
                }
            } else {
                null
            }
        }

        if (audioBytes == null || audioBytes.isEmpty()) {
            if (apiKey.isBlank()) {
                Toast.makeText(
                    this@VoiceStudioActivity,
                    "Gemini API Key is required in Settings for live neural audio generation.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@VoiceStudioActivity,
                    "Audition sample generation failed. Check network connection.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            currentlyPlayingVoiceId = null
            voiceAdapter.notifyDataSetChanged()
            return
        }

        // Play the 24kHz 16-bit PCM bytes
        playRawPcm(audioBytes, profile.id)
    }

    private suspend fun playRawPcm(pcmBytes: ByteArray, voiceId: String) = withContext(Dispatchers.IO) {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 4, pcmBytes.size)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            activeAudioTrack = track
            track.play()

            // Write PCM in chunks
            val chunkSize = 4096
            var offset = 0
            while (offset < pcmBytes.size && currentlyPlayingVoiceId == voiceId) {
                val bytesToWrite = minOf(chunkSize, pcmBytes.size - offset)
                track.write(pcmBytes, offset, bytesToWrite)
                offset += bytesToWrite
            }

            // Small delay to let final buffer drain
            kotlinx.coroutines.delay(300)
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing raw PCM: ${e.message}", e)
        } finally {
            activeAudioTrack = null
            withContext(Dispatchers.Main) {
                if (currentlyPlayingVoiceId == voiceId) {
                    currentlyPlayingVoiceId = null
                    voiceAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun stopAuditionPlayback() {
        auditionJob?.cancel()
        auditionJob = null
        currentlyPlayingVoiceId = null
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (_: Exception) {}
        activeAudioTrack = null
    }

    private inner class VoiceAdapter(
        private val onAuditionClick: (VoiceProfile) -> Unit,
        private val onSelectClick: (VoiceProfile) -> Unit
    ) : RecyclerView.Adapter<VoiceAdapter.VoiceViewHolder>() {

        private val items = mutableListOf<VoiceProfile>()

        fun submitList(newItems: List<VoiceProfile>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceViewHolder {
            val binding = ItemVoiceCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VoiceViewHolder(binding)
        }

        override fun onBindViewHolder(holder: VoiceViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class VoiceViewHolder(private val itemBinding: ItemVoiceCardBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(profile: VoiceProfile) {
                val isSelected = profile.id.equals(preferences.voice, ignoreCase = true)
                val isPlaying = currentlyPlayingVoiceId == profile.id

                itemBinding.tvVoiceName.text = profile.displayName
                itemBinding.tvVoiceGenderTag.text = if (profile.gender == VoiceGender.FEMALE) "Female" else "Male"
                itemBinding.tvVoiceStyle.text = profile.description
                itemBinding.tvVoiceDescription.text = profile.personalityStyle

                // Card stroke highlight
                if (isSelected) {
                    itemBinding.cardVoice.strokeColor = ContextCompat.getColor(this@VoiceStudioActivity, R.color.studio_card_active_border)
                    itemBinding.tvActiveBadge.visibility = View.VISIBLE
                    itemBinding.btnSelect.text = "Selected ✓"
                    itemBinding.btnSelect.isEnabled = false
                    itemBinding.btnSelect.alpha = 0.7f
                } else {
                    itemBinding.cardVoice.strokeColor = ContextCompat.getColor(this@VoiceStudioActivity, R.color.studio_card_border)
                    itemBinding.tvActiveBadge.visibility = View.GONE
                    itemBinding.btnSelect.text = "Select Voice"
                    itemBinding.btnSelect.isEnabled = true
                    itemBinding.btnSelect.alpha = 1.0f
                }

                // Play / Stop audition button
                if (isPlaying) {
                    itemBinding.btnAudition.text = "Stop Audition"
                    itemBinding.btnAudition.setIconResource(R.drawable.ic_sound_stop)
                    itemBinding.btnAudition.setIconTintResource(R.color.studio_purple_bright)
                } else {
                    itemBinding.btnAudition.text = "Play Audition"
                    itemBinding.btnAudition.setIconResource(R.drawable.ic_sound_play)
                    itemBinding.btnAudition.setIconTintResource(R.color.studio_text_primary)
                }

                itemBinding.btnAudition.setOnClickListener {
                    onAuditionClick(profile)
                }

                itemBinding.btnSelect.setOnClickListener {
                    onSelectClick(profile)
                }

                itemBinding.cardVoice.setOnClickListener {
                    if (!isSelected) {
                        onSelectClick(profile)
                    }
                }
            }
        }
    }
}
