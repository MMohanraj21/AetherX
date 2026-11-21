package com.jarvis.assistant.screen

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Ensures sensitive on-screen content (passwords, PIN fields, OTP inputs) are never harvested.
 */
object ScreenPrivacyFilter {

    fun isNodeSecure(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return false
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val combined = "$text $desc".lowercase()

        val sensitiveKeywords = listOf("password", "pin", "cvv", "otp", "secret", "card number")
        for (kw in sensitiveKeywords) {
            if (combined.contains(kw)) return false
        }
        return true
    }

    fun sanitizeText(raw: String): String {
        return raw.replace(Regex("\\b\\d{4,6}\\b"), "[HIDDEN_CODE]")
    }
}
