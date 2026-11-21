package com.jarvis.assistant.agent.v4

/**
 * Enforces safety boundaries on autonomous tasks. Never allows irreversible actions
 * (money transfers, purchases, contact deletion) without explicit vocal confirmation.
 */
object PermissionGate {

    private val RESTRICTED_ACTIONS = setOf(
        "pay", "transfer", "send money", "buy", "purchase", "delete", "remove account", "format"
    )

    fun requiresExplicitApproval(taskGoal: String): Boolean {
        val lower = taskGoal.lowercase()
        return RESTRICTED_ACTIONS.any { lower.contains(it) }
    }
}
