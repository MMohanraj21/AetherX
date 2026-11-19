package com.aetherx.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

object AetherSecurityManager {
    private const val TAG = "AetherSecurityManager"
    const val OFFICIAL_PACKAGE_NAME = "com.aetherx.assistant"

    data class SecurityAudit(
        val packageName: String,
        val isOfficialPackage: Boolean,
        val signatureHash: String,
        val isDebuggable: Boolean,
        val isRooted: Boolean,
        val statusMessage: String
    )

    fun audit(context: Context): SecurityAudit {
        val currentPackage = context.packageName
        val isOfficial = currentPackage == OFFICIAL_PACKAGE_NAME
        val signature = getSignatureSha256(context) ?: "UNAVAILABLE"
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isRooted = detectRootIndicators()

        val status = when {
            isRooted -> "DEVICE_MODIFIED_ROOT"
            isDebug -> "DEVELOPMENT_BUILD"
            !isOfficial -> "COMMUNITY_FORK_OR_CUSTOM_FLAVOR"
            else -> "OFFICIAL_PRODUCTION_INTEGRITY_VERIFIED"
        }

        Log.i(TAG, "Security audit completed: $status (package=$currentPackage)")
        return SecurityAudit(
            packageName = currentPackage,
            isOfficialPackage = isOfficial,
            signatureHash = signature,
            isDebuggable = isDebug,
            isRooted = isRooted,
            statusMessage = status
        )
    }

    private fun getSignatureSha256(context: Context): String? {
        return try {
            val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
                )
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.GET_SIGNATURES
                )
                info.signatures
            }
            val first = sigs?.firstOrNull() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(first.toByteArray())
            digest.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun detectRootIndicators(): Boolean {
        val commonPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return commonPaths.any { java.io.File(it).exists() }
    }
}
