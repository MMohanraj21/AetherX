package com.aetherx.automation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ActionGatekeeper {
    enum class ActionRiskLevel {
        SAFE,         // Read-only actions (check weather, time, app status)
        MODERATE,     // Open an app, media play/pause
        CRITICAL      // Send message, initiate call, delete memory, system modification
    }

    data class PendingAction(
        val id: String,
        val actionType: String,
        val description: String,
        val riskLevel: ActionRiskLevel,
        val onApproved: () -> Unit,
        val onDenied: () -> Unit
    )

    private val _pendingApprovals = MutableSharedFlow<PendingAction>()
    val pendingApprovals: SharedFlow<PendingAction> = _pendingApprovals.asSharedFlow()

    private val activeRequests = mutableMapOf<String, PendingAction>()

    suspend fun requestExecution(
        actionType: String,
        description: String,
        riskLevel: ActionRiskLevel,
        onApproved: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (riskLevel == ActionRiskLevel.SAFE) {
            onApproved()
            return
        }

        val id = java.util.UUID.randomUUID().toString()
        val pending = PendingAction(id, actionType, description, riskLevel, onApproved, onDenied)
        activeRequests[id] = pending
        _pendingApprovals.emit(pending)
    }

    fun approveAction(id: String) {
        val action = activeRequests.remove(id)
        action?.onApproved?.invoke()
    }

    fun denyAction(id: String) {
        val action = activeRequests.remove(id)
        action?.onDenied?.invoke()
    }
}
