package com.jarvis.assistant.automation.maps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jarvis.assistant.util.ActivityLauncherHelper

/**
 * Dedicated Google Maps Manager for ZARA AI.
 * Handles turn-by-turn navigation, nearby searches, and route directions
 * with native Maps package intent and universal browser fallback.
 */
class GoogleMapsManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleMapsManager"
        private const val MAPS_PACKAGE = "com.google.android.apps.maps"
    }

    /**
     * Start turn-by-turn GPS navigation to [destination].
     * (e.g. "take me to Chennai", "Chennai ku vazhi kaatu").
     */
    fun navigateTo(destination: String): Boolean {
        val cleanDest = destination.trim()
        if (cleanDest.isBlank()) return false
        Log.i(TAG, "Navigating to: $cleanDest")

        val encoded = Uri.encode(cleanDest)
        // 1. Direct navigation intent targeted to Google Maps
        val navUri = Uri.parse("google.navigation:q=$encoded")
        val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage(MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (ActivityLauncherHelper.startActivitySafely(context, navIntent)) {
            Log.i(TAG, "Navigation launched via Google Maps native intent for '$cleanDest'")
            return true
        }

        // 2. Generic Maps web navigation fallback
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val launched = ActivityLauncherHelper.startActivitySafely(context, webIntent)
        Log.i(TAG, "Navigation launched via web fallback ($launched) for '$cleanDest'")
        return launched
    }

    /**
     * Search nearby places on Google Maps (e.g. "petrol bunk", "hospitals", "restaurants").
     */
    fun searchNearby(query: String): Boolean {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return false
        Log.i(TAG, "Searching nearby: $cleanQuery")

        val encoded = Uri.encode(cleanQuery)
        // 1. Geo query intent targeted to Google Maps
        val geoUri = Uri.parse("geo:0,0?q=$encoded")
        val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage(MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (ActivityLauncherHelper.startActivitySafely(context, geoIntent)) {
            Log.i(TAG, "Nearby search launched via Google Maps native intent for '$cleanQuery'")
            return true
        }

        // 2. Web search fallback
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val launched = ActivityLauncherHelper.startActivitySafely(context, webIntent)
        Log.i(TAG, "Nearby search launched via web fallback ($launched) for '$cleanQuery'")
        return launched
    }

    /**
     * Get route / directions to [destination].
     */
    fun getDirections(destination: String): Boolean {
        return navigateTo(destination)
    }
}
