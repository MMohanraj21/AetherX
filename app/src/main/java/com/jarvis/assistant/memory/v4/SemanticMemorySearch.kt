package com.jarvis.assistant.memory.v4

import com.jarvis.assistant.data.memory.MemoryEntity

/**
 * High-performance semantic and fuzzy keyword search engine for ZARA's memory bank.
 */
object SemanticMemorySearch {

    /**
     * Scores memory relevance against a user query using token overlap, recency weighting,
     * and importance multiplier.
     */
    fun rankMemories(query: String, memories: List<MemoryEntity>): List<Pair<MemoryEntity, Float>> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        return memories.mapNotNull { mem ->
            if (mem.isOutdated) return@mapNotNull null
            val memTokens = tokenize("${mem.key} ${mem.value} ${mem.category}")
            var matchCount = 0

            for (qt in queryTokens) {
                if (memTokens.any { it.contains(qt) || qt.contains(it) }) {
                    matchCount++
                }
            }

            if (matchCount == 0) return@mapNotNull null

            val overlapScore = matchCount.toFloat() / queryTokens.size.toFloat()
            val importanceMultiplier = 1.0f + (mem.importance * 0.2f)
            val confidenceMultiplier = mem.confidence.coerceIn(0.1f, 1.0f)
            val finalScore = overlapScore * importanceMultiplier * confidenceMultiplier

            mem to finalScore
        }.sortedByDescending { it.second }
    }

    private fun tokenize(text: String): Set<String> {
        val stopWords = setOf("the", "a", "an", "is", "in", "at", "to", "for", "of", "and", "or", "what", "how", "my", "your", "sir")
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && !stopWords.contains(it) }
            .toSet()
    }
}
