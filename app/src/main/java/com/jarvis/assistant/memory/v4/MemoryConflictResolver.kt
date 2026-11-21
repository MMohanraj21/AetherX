package com.jarvis.assistant.memory.v4

import com.jarvis.assistant.data.memory.MemoryEntity

/**
 * Resolves contradictory memories by detecting conflicting keys/attributes and superseding old values.
 */
object MemoryConflictResolver {

    /**
     * Resolves incoming memory against existing category records.
     * If a direct contradiction or update is identified, returns the older memory marked as outdated.
     */
    fun resolve(incoming: MemoryEntity, existing: List<MemoryEntity>): Pair<MemoryEntity, List<MemoryEntity>> {
        val outdated = mutableListOf<MemoryEntity>()
        val incomingTokens = incoming.key.lowercase().split(" ").filter { it.isNotBlank() }

        for (item in existing) {
            if (item.id == incoming.id) continue
            if (item.category == incoming.category) {
                val itemTokens = item.key.lowercase().split(" ").filter { it.isNotBlank() }
                val common = incomingTokens.intersect(itemTokens.toSet())
                // If keys strongly overlap in the same category, update the old memory
                if (common.size >= (incomingTokens.size / 2).coerceAtLeast(1)) {
                    outdated.add(item.copy(isOutdated = true, lastUpdated = System.currentTimeMillis()))
                }
            }
        }

        return incoming to outdated
    }
}
