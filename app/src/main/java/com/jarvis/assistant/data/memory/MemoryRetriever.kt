package com.jarvis.assistant.data.memory

import android.content.Context
import android.util.Log

/**
 * Executes multi-tier memory retrieval across:
 * 1. Long-Term Categorized Memories (MemoryEntity)
 * 2. Searchable Conversation Archive (MessageEntity & ConversationSummaryEntity)
 * 3. Active Task Contexts (TaskContextEntity)
 *
 * Builds high-density contextual prompts so ZARA can answer questions about
 * prior user discussions, preferences, decisions, and projects accurately.
 */
class MemoryRetriever(
    private val memoryDao: MemoryDao,
    private val conversationDao: ConversationDao
) {
    companion object {
        private const val TAG = "MemoryRetriever"
    }

    /**
     * Resolves relevant context for a user utterance by querying Room SQLite.
     * Returns a formatted context prompt to be injected into LLM session instructions or turn prefix.
     */
    suspend fun retrieveRelevantContext(userUtterance: String): String {
        val clean = userUtterance.trim()
        if (clean.isBlank()) return ""

        val sb = StringBuilder()

        try {
            // 1. Search Long-Term Categorized Memories
            val keywords = extractKeywords(clean)
            val matchedMemories = mutableSetOf<MemoryEntity>()

            for (kw in keywords) {
                if (kw.length >= 3) {
                    val found = memoryDao.searchMemories(kw)
                    matchedMemories.addAll(found.take(5))
                }
            }

            // Also check for category-specific questions
            val matchedCategory = detectCategoryIntent(clean)
            if (matchedCategory != null) {
                val categoryMems = memoryDao.getAllMemories().filter { it.category.equals(matchedCategory, ignoreCase = true) }
                matchedMemories.addAll(categoryMems.take(5))
            }

            if (matchedMemories.isNotEmpty()) {
                sb.append("\n[PERSISTENT MEMORY CONTEXT]\n")
                matchedMemories.sortedByDescending { it.importance }.take(6).forEach { mem ->
                    sb.append("• [${mem.category}] ${mem.key}: ${mem.value}\n")
                }
            }

            // 2. Search Historical Conversation Archive
            val matchedMessages = mutableSetOf<MessageEntity>()
            for (kw in keywords) {
                if (kw.length >= 4) {
                    val messages = conversationDao.searchMessages(kw, limit = 5)
                    matchedMessages.addAll(messages)
                }
            }

            if (matchedMessages.isNotEmpty()) {
                sb.append("\n[RELEVANT PREVIOUS CONVERSATIONS]\n")
                matchedMessages.sortedByDescending { it.timestamp }.take(4).forEach { msg ->
                    sb.append("• ${msg.sender.uppercase()}: ${msg.content} (${msg.formattedTime})\n")
                }
            }

            // 3. Search Active Task Contexts
            val activeTasks = conversationDao.getActiveTasks()
            if (activeTasks.isNotEmpty()) {
                sb.append("\n[ACTIVE WORKFLOW CONTEXT]\n")
                activeTasks.take(3).forEach { task ->
                    sb.append("• Task: ${task.title} - Goal: ${task.goal} [Status: ${task.status}]\n")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve memory context: ${e.message}")
        }

        return sb.toString().trim()
    }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
            "the", "is", "are", "was", "were", "a", "an", "and", "or", "but", "in",
            "on", "at", "to", "for", "with", "about", "did", "do", "does", "have",
            "has", "had", "tell", "me", "my", "your", "i", "you", "we", "they",
            "earlier", "yesterday", "before", "previous", "previously", "zara", "hey"
        )
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in stopWords }
    }

    private fun detectCategoryIntent(query: String): String? {
        val lower = query.lowercase()
        return when {
            lower.contains("project") || lower.contains("work on") || lower.contains("build") -> MemoryEntity.CATEGORY_PROJECTS
            lower.contains("career") || lower.contains("job") || lower.contains("company") || lower.contains("role") -> MemoryEntity.CATEGORY_CAREER
            lower.contains("education") || lower.contains("college") || lower.contains("degree") || lower.contains("school") -> MemoryEntity.CATEGORY_EDUCATION
            lower.contains("goal") || lower.contains("aim") || lower.contains("target") -> MemoryEntity.CATEGORY_GOALS
            lower.contains("tech") || lower.contains("language") || lower.contains("framework") || lower.contains("stack") -> MemoryEntity.CATEGORY_TECH_PREFERENCES
            lower.contains("preference") || lower.contains("like") || lower.contains("favorite") -> MemoryEntity.CATEGORY_PREFERENCES
            lower.contains("friend") || lower.contains("relationship") || lower.contains("contact") || lower.contains("family") -> MemoryEntity.CATEGORY_RELATIONSHIPS
            lower.contains("plan") || lower.contains("trip") || lower.contains("travel") -> MemoryEntity.CATEGORY_PLANS
            lower.contains("routine") || lower.contains("habit") || lower.contains("schedule") -> MemoryEntity.CATEGORY_RECURRING_TASKS
            else -> null
        }
    }
}
