package com.jarvis.assistant.telecom

import android.content.Context
import com.jarvis.assistant.automation.contacts.ContactResolutionResult
import com.jarvis.assistant.automation.contacts.ContactResolver

/**
 * Resolves incoming phone numbers to friendly contact names.
 */
class CallerIdentificationManager(context: Context) {

    private val contactResolver = ContactResolver(context)

    fun resolveCallerName(rawNumber: String?): String {
        if (rawNumber.isNullOrBlank()) {
            return "Unknown Caller"
        }
        val trimmed = rawNumber.trim()
        if (trimmed == "-1" || trimmed == "-2" || trimmed.equals("private", ignoreCase = true)) {
            return "Private Caller"
        }

        return try {
            when (val res = contactResolver.resolveContact(trimmed)) {
                is ContactResolutionResult.SingleMatch -> res.contact.name
                is ContactResolutionResult.MultipleMatches -> res.candidates.firstOrNull()?.name ?: trimmed
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }
}
