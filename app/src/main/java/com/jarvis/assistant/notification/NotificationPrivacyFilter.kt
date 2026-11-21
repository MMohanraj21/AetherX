package com.jarvis.assistant.notification

import java.util.Calendar
import java.util.regex.Pattern

/**
 * Privacy and sensitivity filter for ZARA AI's notification intelligence.
 * Strictly excludes OTPs, 2FA tokens, banking transactions, passwords, and respects quiet hours.
 */
object NotificationPrivacyFilter {

    private val SENSITIVE_PATTERNS = listOf(
        Pattern.compile("\\b(\\d{4,8})\\b.*?(?:otp|one time password|verification code|secret code)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:otp|verification code|security code|passcode|one-time password)[^\\w]{1,5}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:credited|debited|acct|a/c|balance|inr|rs\\.|trans id|atm txn|upi txn)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:password|pin code|cvv|expiry date)\\b", Pattern.CASE_INSENSITIVE)
    )

    private val EXCLUDED_PACKAGES = setOf(
        "com.google.android.apps.authenticator2",
        "com.authy.authy",
        "com.duosecurity.duomobile",
        "com.microsoft.authenticator"
    )

    /**
     * Evaluates whether a notification is safe to announce aloud.
     */
    fun isSafeToAnnounce(packageName: String, title: String, text: String): Boolean {
        if (EXCLUDED_PACKAGES.contains(packageName.lowercase())) return false
        if (isQuietHours()) return false

        val fullContent = "$title $text"
        for (pattern in SENSITIVE_PATTERNS) {
            if (pattern.matcher(fullContent).find()) {
                return false
            }
        }
        return true
    }

    /**
     * Checks if current local time falls into quiet hours (default: 11 PM to 7 AM).
     */
    fun isQuietHours(startHour: Int = 23, endHour: Int = 7): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (startHour > endHour) {
            currentHour >= startHour || currentHour < endHour
        } else {
            currentHour in startHour until endHour
        }
    }
}
