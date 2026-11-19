package com.aetherx.vision

import android.graphics.Rect

data class GroundedUiElement(
    val id: String,
    val label: String,
    val bounds: Rect,
    val confidence: Float,
    val actionType: String // "click", "input", "scroll"
)

object UiGroundingEngine {
    fun parseGroundingBoxes(aiResponseText: String, screenWidth: Int, screenHeight: Int): List<GroundedUiElement> {
        val elements = mutableListOf<GroundedUiElement>()
        // Pattern: [box_2d: ymin, xmin, ymax, xmax, "label"]
        val regex = Regex("""\[(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*"([^"]+)"\]""")
        val matches = regex.findAll(aiResponseText)

        for ((index, match) in matches.withIndex()) {
            val (ymin, xmin, ymax, xmax, label) = match.destructured
            val rect = Rect(
                (xmin.toInt() * screenWidth) / 1000,
                (ymin.toInt() * screenHeight) / 1000,
                (xmax.toInt() * screenWidth) / 1000,
                (ymax.toInt() * screenHeight) / 1000
            )
            elements.add(
                GroundedUiElement(
                    id = "elem_$index",
                    label = label,
                    bounds = rect,
                    confidence = 0.9f,
                    actionType = if (label.contains("input", ignoreCase = true)) "input" else "click"
                )
            )
        }
        return elements
    }
}
