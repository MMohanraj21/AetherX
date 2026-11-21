package com.jarvis.assistant.security.guardian

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.jarvis.assistant.util.PermissionHelper

/**
 * Audits runtime security permissions and device authorizations.
 */
class PermissionAuditManager(private val context: Context) {

    data class SecurityAuditReport(
        val hasMicrophone: Boolean,
        val hasNotifications: Boolean,
        val hasPhoneAccess: Boolean,
        val hasContacts: Boolean,
        val hasAccessibility: Boolean,
        val overallStatus: String
    )

    fun audit(): SecurityAuditReport {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasNotif = PermissionHelper.hasNotificationPermission(context)
        val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasA11y = PermissionHelper.isAccessibilityServiceEnabled(context)

        val status = if (hasMic && hasNotif && hasA11y) "PROTECTED" else "ACTION_REQUIRED"

        return SecurityAuditReport(
            hasMicrophone = hasMic,
            hasNotifications = hasNotif,
            hasPhoneAccess = hasPhone,
            hasContacts = hasContacts,
            hasAccessibility = hasA11y,
            overallStatus = status
        )
    }
}
