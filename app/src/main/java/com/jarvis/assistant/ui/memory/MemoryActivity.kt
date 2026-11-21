package com.jarvis.assistant.ui.memory

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.memory.MemoryEntity
import com.jarvis.assistant.databinding.ActivityMemoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MemoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryBinding
    private val memoryRepo by lazy { JarvisApp.instance.memoryRepository }
    private lateinit var adapter: MemoryAdapter

    private var allMemories: List<MemoryEntity> = emptyList()
    private var selectedCategory: String = "All"
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupCategoryChips()
        setupSearch()
        observeMemories()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddMemory.setOnClickListener { showAddMemoryDialog() }
        binding.btnExportMemory.setOnClickListener { exportMemoriesAsJson() }
    }

    private fun exportMemoriesAsJson() {
        if (allMemories.isEmpty()) {
            Toast.makeText(this, "No memories stored to export.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val jsonArray = org.json.JSONArray()
            for (m in allMemories) {
                val obj = org.json.JSONObject().apply {
                    put("id", m.id)
                    put("category", m.category)
                    put("key", m.key)
                    put("value", m.value)
                    put("rawText", m.rawText)
                    put("timestamp", m.timestamp)
                    put("formattedDate", m.formattedDate)
                    put("importance", m.importance)
                    put("isOutdated", m.isOutdated)
                }
                jsonArray.put(obj)
            }
            val jsonString = jsonArray.toString(2)
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, jsonString)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, "Export ZARA Memories (JSON)")
            startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export memories: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = MemoryAdapter(
            onEdit = { memory -> showEditMemoryDialog(memory) },
            onDelete = { memory -> confirmDeleteMemory(memory) }
        )
        binding.rvMemories.layoutManager = LinearLayoutManager(this)
        binding.rvMemories.adapter = adapter
    }

    private fun setupCategoryChips() {
        val categories = listOf("All") + MemoryEntity.ALL_CATEGORIES
        binding.layoutCategories.removeAllViews()

        val chips = mutableListOf<Pair<TextView, String>>()

        for (cat in categories) {
            val chip = TextView(this).apply {
                text = formatCategoryDisplayName(cat)
                textSize = 12f
                val paddingH = (14 * resources.displayMetrics.density).toInt()
                val paddingV = (6 * resources.displayMetrics.density).toInt()
                setPadding(paddingH, paddingV, paddingH, paddingV)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (chips.isNotEmpty()) {
                        marginStart = (8 * resources.displayMetrics.density).toInt()
                    }
                }
                layoutParams = params
            }
            binding.layoutCategories.addView(chip)
            chips.add(Pair(chip, cat))
        }

        updateChipStyles(chips, chips.first().first)

        for ((view, category) in chips) {
            view.setOnClickListener {
                selectedCategory = category
                updateChipStyles(chips, view)
                filterAndDisplay()
            }
        }
    }

    private fun formatCategoryDisplayName(category: String): String {
        return when (category) {
            "All" -> "All"
            MemoryEntity.CATEGORY_USER_PROFILE -> "Profile"
            MemoryEntity.CATEGORY_TECH_PREFERENCES -> "Tech"
            MemoryEntity.CATEGORY_RECURRING_TASKS -> "Routines"
            else -> category.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }
    }

    private fun updateChipStyles(chips: List<Pair<TextView, String>>, selectedView: TextView) {
        val tvAccent = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeAccent, tvAccent, true)
        val accentColor = if (tvAccent.data != 0) tvAccent.data else ContextCompat.getColor(this, R.color.primary_cyan)

        val tvSecondary = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeTextSecondary, tvSecondary, true)
        val secondaryColor = if (tvSecondary.data != 0) tvSecondary.data else ContextCompat.getColor(this, R.color.text_secondary)

        for ((view, _) in chips) {
            if (view == selectedView) {
                view.setBackgroundResource(R.drawable.bg_chip_selected)
                view.setTextColor(accentColor)
            } else {
                view.setBackgroundResource(R.drawable.bg_chip_unselected)
                view.setTextColor(secondaryColor)
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                filterAndDisplay()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeMemories() {
        lifecycleScope.launch {
            memoryRepo.getAllMemoriesFlow().collectLatest { memories ->
                allMemories = memories
                filterAndDisplay()
            }
        }
    }

    private fun filterAndDisplay() {
        val filtered = allMemories.filter { mem ->
            val matchesCategory = (selectedCategory == "All" || mem.category.equals(selectedCategory, ignoreCase = true))
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                mem.rawText.contains(searchQuery, ignoreCase = true) ||
                mem.key.contains(searchQuery, ignoreCase = true) ||
                mem.category.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }

        binding.tvMemoryCount.text = "SAVED MEMORIES (${filtered.size})"
        adapter.submitList(filtered)

        if (filtered.isEmpty()) {
            binding.tvNoMemories.visibility = View.VISIBLE
        } else {
            binding.tvNoMemories.visibility = View.GONE
        }
    }

    private fun showAddMemoryDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MemoryActivity,
                android.R.layout.simple_spinner_dropdown_item,
                MemoryEntity.ALL_CATEGORIES
            )
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(16, 16, 16, 16)
        }

        val tvPrimary = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeTextPrimary, tvPrimary, true)
        val textPrimaryColor = if (tvPrimary.data != 0) tvPrimary.data else ContextCompat.getColor(this@MemoryActivity, R.color.text_primary)

        val tvMuted = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeTextMuted, tvMuted, true)
        val textMutedColor = if (tvMuted.data != 0) tvMuted.data else ContextCompat.getColor(this@MemoryActivity, R.color.text_muted)

        val editText = EditText(this).apply {
            hint = "e.g., My favorite song is Believer"
            setTextColor(textPrimaryColor)
            setHintTextColor(textMutedColor)
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
        }

        layout.addView(spinner)
        layout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add New Memory")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                val fact = editText.text.toString().trim()
                val category = spinner.selectedItem.toString()
                if (fact.isNotBlank()) {
                    lifecycleScope.launch {
                        memoryRepo.saveMemory(
                            rawText = fact,
                            category = category,
                            key = fact.take(30).trim(),
                            value = fact
                        )
                        Toast.makeText(this@MemoryActivity, "Memory saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showEditMemoryDialog(memory: MemoryEntity) {
        val tvPrimary = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeTextPrimary, tvPrimary, true)
        val textPrimaryColor = if (tvPrimary.data != 0) tvPrimary.data else ContextCompat.getColor(this@MemoryActivity, R.color.text_primary)

        val editText = EditText(this).apply {
            setText(memory.rawText)
            setTextColor(textPrimaryColor)
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(24, 24, 24, 24)
        }

        val container = LinearLayout(this).apply {
            setPadding(48, 24, 48, 16)
            addView(editText)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Memory")
            .setView(container)
            .setPositiveButton("UPDATE") { _, _ ->
                val newFact = editText.text.toString().trim()
                if (newFact.isNotBlank()) {
                    lifecycleScope.launch {
                        memoryRepo.updateMemory(
                            memory.copy(
                                rawText = newFact,
                                value = newFact,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        Toast.makeText(this@MemoryActivity, "Memory updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun confirmDeleteMemory(memory: MemoryEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Memory")
            .setMessage("Are you sure you want ZARA to forget this fact?\n\n\"${memory.rawText}\"")
            .setPositiveButton("DELETE") { _, _ ->
                lifecycleScope.launch {
                    memoryRepo.deleteMemoryById(memory.id)
                    Toast.makeText(this@MemoryActivity, "Memory deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
