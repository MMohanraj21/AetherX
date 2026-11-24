package com.jarvis.assistant.ui.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
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
    private var activeMediaPlayer: MediaPlayer? = null
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
            return
        }

        stopAuditionPlayback()
        currentlyPlayingVoiceId = profile.id
        voiceAdapter.notifyDataSetChanged()

        // 1. First priority: Play authentic pre-recorded spoken Tamil MP3 audition asset
        try {
            val assetPath = "auditions/audition_${profile.id.lowercase()}.mp3"
            val afd = assets.openFd(assetPath)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener {
                    stopAuditionPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopAuditionPlayback()
                    true
                }
                prepare()
                start()
            }
            activeMediaPlayer = player
            return
        } catch (e: Exception) {
            Log.w(TAG, "Bundled MP3 audition not found or failed for ${profile.id}: ${e.message}. Falling back to live synthesis.")
        }

        // 2. Fallback: Live synthesis via Gemini or PCM cache
        auditionJob = lifecycleScope.launch {
            playVoiceAuditionLiveFallback(profile)
        }
    }

    private suspend fun playVoiceAuditionLiveFallback(profile: VoiceProfile) {
        val apiKey = preferences.apiKey
        val cacheFile = File(cacheDir, "audition_${profile.id.lowercase()}.pcm")

        val audioBytes: ByteArray? = withContext(Dispatchers.IO) {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                try { cacheFile.readBytes() } catch (e: Exception) { null }
            } else if (apiKey.isNotBlank()) {
                val result = GeminiRestClient.synthesizeSpeech(
                    apiKey = apiKey,
                    voiceName = profile.id,
                    text = profile.sampleTamilText
                )
                if (result.isSuccess) {
                    val bytes = result.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        try { cacheFile.writeBytes(bytes) } catch (_: Exception) {}
                        bytes
                    } else null
                } else null
            } else null
        }

        if (audioBytes == null || audioBytes.isEmpty()) {
            Toast.makeText(
                this@VoiceStudioActivity,
                "Audition sample unavailable. Please try again.",
                Toast.LENGTH_SHORT
            ).show()
            stopAuditionPlayback()
            return
        }

        playRawPcm(audioBytes, profile.id)
    }

    private suspend fun playRawPcm(rawBytes: ByteArray, voiceId: String) = withContext(Dispatchers.IO) {
        try {
            val pcmBytes = if (rawBytes.size > 44 &&
                rawBytes[0] == 'R'.code.toByte() &&
                rawBytes[1] == 'I'.code.toByte() &&
                rawBytes[2] == 'F'.code.toByte() &&
                rawBytes[3] == 'F'.code.toByte()
            ) {
                rawBytes.copyOfRange(44, rawBytes.size)
            } else {
                rawBytes
            }

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

            val chunkSize = 4096
            var offset = 0
            while (offset < pcmBytes.size && currentlyPlayingVoiceId == voiceId) {
                val bytesToWrite = minOf(chunkSize, pcmBytes.size - offset)
                track.write(pcmBytes, offset, bytesToWrite)
                offset += bytesToWrite
            }

            kotlinx.coroutines.delay(300)
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing raw PCM: ${e.message}", e)
        } finally {
            activeAudioTrack = null
            withContext(Dispatchers.Main) {
                if (currentlyPlayingVoiceId == voiceId) {
                    stopAuditionPlayback()
                }
            }
        }
    }

    private fun stopAuditionPlayback() {
        auditionJob?.cancel()
        auditionJob = null
        val hadPlaying = currentlyPlayingVoiceId != null
        currentlyPlayingVoiceId = null
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (_: Exception) {}
        activeMediaPlayer = null
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (_: Exception) {}
        activeAudioTrack = null
        if (hadPlaying) {
            voiceAdapter.notifyDataSetChanged()
        }
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
