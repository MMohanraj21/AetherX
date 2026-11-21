package com.jarvis.assistant.agent.v4

import android.content.Context
import android.util.Log

/**
 * High-level coordinator for multi-step task planning, execution tracking, and vocal verification.
 */
class ZaraTaskAgent(private val context: Context) {

    companion object {
        private const val TAG = "ZaraTaskAgent"
    }

    private var currentPlan: TaskPlanner.TaskPlan? = null
    private var currentStepIndex = 0

    fun initiateTask(goal: String, onPromptApproval: (String) -> Unit, onExecuteReady: (TaskPlanner.TaskPlan) -> Unit) {
        val plan = TaskPlanner.createPlan(goal)
        currentPlan = plan
        currentStepIndex = 0

        Log.i(TAG, "Task plan initialized for: '${plan.goal}' with ${plan.steps.size} steps.")

        if (plan.requiresApproval) {
            onPromptApproval("This task involves sensitive actions. Shall I proceed with: ${plan.goal}?")
        } else {
            onExecuteReady(plan)
        }
    }

    fun completeCurrentTask(): String {
        val goal = currentPlan?.goal ?: "Task"
        currentPlan = null
        currentStepIndex = 0
        return "Completed: $goal successfully."
    }
}
