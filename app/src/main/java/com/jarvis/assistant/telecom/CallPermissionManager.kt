package com.jarvis.assistant.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Validates and exposes telephony, telecom, and contact permission states.
 */
object CallPermissionManager {

    fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAnswerCallsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasCallPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canMonitorCalls(context: Context): Boolean {
        return hasPhoneStatePermission(context)
    }

    fun canControlCalls(context: Context): Boolean {
        return hasAnswerCallsPermission(context)
    }
}
