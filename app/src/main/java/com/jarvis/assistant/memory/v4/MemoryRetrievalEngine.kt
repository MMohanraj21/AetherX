package com.jarvis.assistant.memory.v4

import android.content.Context
import com.jarvis.assistant.data.memory.MemoryDatabase
import com.jarvis.assistant.data.memory.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates memory querying, ranking, and contextual formatting for AI reasoning.
 */
class MemoryRetrievalEngine(context: Context) {

    private val db = MemoryDatabase.getInstance(context)
    private val memoryDao = db.memoryDao()

    suspend fun getRelevantContext(query: String, maxItems: Int = 5): String = withContext(Dispatchers.IO) {
        val all = memoryDao.getAllMemories()
        if (all.isEmpty()) return@withContext ""

        val ranked = SemanticMemorySearch.rankMemories(query, all)
        if (ranked.isEmpty()) return@withContext ""

        val top = ranked.take(maxItems)
        val sb = StringBuilder("[RELEVANT PERSONAL MEMORIES]:\n")
        for ((mem, _) in top) {
            sb.append("- [${mem.category}] ${mem.key}: ${mem.value}\n")
        }
        sb.toString().trim()
    }

    suspend fun saveMemory(category: String, key: String, value: String, importance: Int = 1) = withContext(Dispatchers.IO) {
        val cleanValue = MemoryPrivacyManager.sanitize(value)
        val incoming = MemoryEntity(
            category = category,
            key = key.trim(),
            value = cleanValue.trim(),
            rawText = "$key: $cleanValue",
            importance = importance
        )
        val existing = memoryDao.getAllMemories().filter { it.category == category }
        val (resolved, outdated) = MemoryConflictResolver.resolve(incoming, existing)

        for (old in outdated) {
            memoryDao.updateMemory(old)
        }
        memoryDao.insertMemory(resolved)
    }
}
