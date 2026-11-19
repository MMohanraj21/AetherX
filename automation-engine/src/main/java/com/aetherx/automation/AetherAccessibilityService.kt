package com.aetherx.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AetherAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "AetherAccessibility"
        @Volatile
        var instance: AetherAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AETHER X Accessibility Service connected safely")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event monitoring for grounded UI execution
    }

    override fun onInterrupt() {
        Log.w(TAG, "AETHER X Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun clickCoordinates(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture at ($x, $y) cancelled")
            }
        }, null)
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }
}
