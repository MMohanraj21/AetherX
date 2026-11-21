package com.jarvis.assistant.screen

import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService

/**
 * Extracts visible on-screen text and UI structure through AccessibilityService
 * while respecting user privacy.
 */
object ScreenIntelligenceManager {

    data class ScreenSummary(
        val packageName: String,
        val visibleLabels: List<String>,
        val fullText: String
    )

    fun captureCurrentScreen(): ScreenSummary? {
        val service = JarvisAccessibilityService.instance ?: return null
        val rootNode = service.rootInActiveWindow ?: return null

        val labels = mutableListOf<String>()
        val textCollector = StringBuilder()

        traverseNode(rootNode, labels, textCollector)

        val pkg = rootNode.packageName?.toString().orEmpty()
        return ScreenSummary(
            packageName = pkg,
            visibleLabels = labels.distinct().take(30),
            fullText = textCollector.toString().trim()
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        labels: MutableList<String>,
        collector: StringBuilder
    ) {
        node ?: return
        if (!ScreenPrivacyFilter.isNodeSecure(node)) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrBlank()) {
            val clean = ScreenPrivacyFilter.sanitizeText(text)
            labels.add(clean)
            collector.append(clean).append(" • ")
        } else if (!desc.isNullOrBlank()) {
            val clean = ScreenPrivacyFilter.sanitizeText(desc)
            labels.add(clean)
            collector.append(clean).append(" • ")
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), labels, collector)
        }
    }
}
