package com.jarvis.assistant.security

import android.content.Context

/**
 * Enforces confirmation policies based on action risk categories.
 */
object ConfirmationPolicyManager {

    fun requiresConfirmation(actionName: String): Boolean {
        return when (ActionRiskClassifier.classifyAction(actionName)) {
            ActionRiskClassifier.RiskCategory.CATEGORY_A_HARMLESS -> false
            ActionRiskClassifier.RiskCategory.CATEGORY_B_EXTERNAL_COMMUNICATION -> true
            ActionRiskClassifier.RiskCategory.CATEGORY_C_DESTRUCTIVE -> true
            ActionRiskClassifier.RiskCategory.CATEGORY_D_UNSUPPORTED -> false
        }
    }

    fun isExternalCommunication(actionName: String): Boolean {
        return ActionRiskClassifier.classifyAction(actionName) == ActionRiskClassifier.RiskCategory.CATEGORY_B_EXTERNAL_COMMUNICATION
    }
}
