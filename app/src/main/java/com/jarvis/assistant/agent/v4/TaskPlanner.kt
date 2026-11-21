package com.jarvis.assistant.agent.v4

/**
 * Decomposes natural language task requests into structured sub-steps.
 */
object TaskPlanner {

    data class PlannedStep(
        val stepNumber: Int,
        val description: String,
        val targetApp: String? = null
    )

    data class TaskPlan(
        val goal: String,
        val steps: List<PlannedStep>,
        val requiresApproval: Boolean
    )

    fun createPlan(goal: String): TaskPlan {
        val lower = goal.lowercase()
        val steps = mutableListOf<PlannedStep>()

        if (lower.contains("whatsapp") || lower.contains("message")) {
            steps.add(PlannedStep(1, "Launch WhatsApp", "com.whatsapp"))
            steps.add(PlannedStep(2, "Select requested contact or conversation"))
            steps.add(PlannedStep(3, "Prepare message content"))
            steps.add(PlannedStep(4, "Obtain final confirmation and send"))
        } else if (lower.contains("youtube") || lower.contains("play")) {
            steps.add(PlannedStep(1, "Launch YouTube", "com.google.android.youtube"))
            steps.add(PlannedStep(2, "Search for requested video/song"))
            steps.add(PlannedStep(3, "Begin playback"))
        } else {
            steps.add(PlannedStep(1, "Initialize task context"))
            steps.add(PlannedStep(2, "Execute action: $goal"))
            steps.add(PlannedStep(3, "Verify visible outcome"))
        }

        return TaskPlan(
            goal = goal,
            steps = steps,
            requiresApproval = PermissionGate.requiresExplicitApproval(goal)
        )
    }
}
