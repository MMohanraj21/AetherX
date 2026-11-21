package com.jarvis.assistant.conversation

import android.util.Log

/**
 * Resolves pronoun references and contextual pointers ("he", "him", "her", "that file", "avan", "avaru")
 * using previously resolved entities from prior conversation turns.
 */
object ContextResolver {

    private const val TAG = "ContextResolver"

    private val PERSON_PRONOUNS = setOf(
        "he", "him", "his", "himself", "she", "her", "hers", "herself",
        "avan", "avaru", "avanga", "avala", "avanukku", "avarukku", "avangalukku",
        "woh", "usko", "unka", "usey"
    )

    private val APP_PRONOUNS = setOf(
        "that app", "the app", "it", "antha app", "antha application"
    )

    private val FILE_PRONOUNS = setOf(
        "that file", "the file", "antha file", "antha document"
    )

    fun resolveQueryContext(rawQuery: String): ResolvedQuery {
        val lower = rawQuery.lowercase().trim()
        val words = lower.split(Regex("\s+"))
        var resolvedText = rawQuery
        val substitutions = mutableListOf<String>()

        // 1. Resolve Person Pronouns
        val personEntity = ConversationStateManager.getLatestEntity(ConversationStateManager.EntityType.PERSON_CONTACT)
        if (personEntity != null) {
            for (p in PERSON_PRONOUNS) {
                val regex = Regex("\b$p\b", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(resolvedText)) {
                    resolvedText = regex.replace(resolvedText, personEntity.value)
                    substitutions.add("$p -> ${personEntity.value}")
                }
            }
        }

        // 2. Resolve App Pronouns
        val appEntity = ConversationStateManager.getLatestEntity(ConversationStateManager.EntityType.APPLICATION)
        if (appEntity != null) {
            for (p in APP_PRONOUNS) {
                val regex = Regex("\b$p\b", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(resolvedText)) {
                    resolvedText = regex.replace(resolvedText, appEntity.value)
                    substitutions.add("$p -> ${appEntity.value}")
                }
            }
        }

        // 3. Resolve File Pronouns
        val fileEntity = ConversationStateManager.getLatestEntity(ConversationStateManager.EntityType.FILE)
        if (fileEntity != null) {
            for (p in FILE_PRONOUNS) {
                val regex = Regex("\b$p\b", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(resolvedText)) {
                    resolvedText = regex.replace(resolvedText, fileEntity.value)
                    substitutions.add("$p -> ${fileEntity.value}")
                }
            }
        }

        if (substitutions.isNotEmpty()) {
            Log.i(TAG, "Context resolved: '$rawQuery' -> '$resolvedText' (${substitutions.joinToString()})")
        }

        return ResolvedQuery(
            originalQuery = rawQuery,
            resolvedQuery = resolvedText,
            substitutions = substitutions,
            activeEntity = personEntity?.value ?: appEntity?.value
        )
    }

    data class ResolvedQuery(
        val originalQuery: String,
        val resolvedQuery: String,
        val substitutions: List<String>,
        val activeEntity: String?
    )
}
