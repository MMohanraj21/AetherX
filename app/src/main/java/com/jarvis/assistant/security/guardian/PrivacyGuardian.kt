package com.jarvis.assistant.security.guardian

import android.content.Context

/**
 * Validates URLs and monitors sensitive data transmissions.
 */
class PrivacyGuardian(private val context: Context) {

    private val auditManager = PermissionAuditManager(context)

    fun getSecuritySummary(): String {
        val rep = auditManager.audit()
        return "ZARA Security Guardian: ${rep.overallStatus}. Mic: ${rep.hasMicrophone}, Notifications: ${rep.hasNotifications}, Accessibility: ${rep.hasAccessibility}."
    }

    fun isUrlSuspicious(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("bit.ly/") || lower.contains(".xyz") || lower.contains("phishing")
    }
}
