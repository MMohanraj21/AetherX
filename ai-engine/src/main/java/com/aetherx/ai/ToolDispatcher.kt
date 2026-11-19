package com.aetherx.ai

import android.content.Context
import android.util.Log
import com.aetherx.automation.ActionGatekeeper
import com.aetherx.automation.AppLauncher
import com.aetherx.memory.AetherMemoryRepository

class ToolDispatcher(
    private val context: Context,
    private val memoryRepo: AetherMemoryRepository,
    private val appLauncher: AppLauncher
) {
    companion object {
        private const val TAG = "ToolDispatcher"
    }

    suspend fun dispatchToolCall(
        toolName: String,
        arguments: Map<String, Any>?,
        onResult: (String) -> Unit
    ) {
        Log.i(TAG, "Dispatching tool: $toolName with args: $arguments")

        when (toolName) {
            "make_phone_call" -> {
                val number = arguments?.get("phone_number")?.toString() ?: ""
                ActionGatekeeper.requestExecution(
                    actionType = "PHONE_CALL",
                    description = "Initiate phone call to $number",
                    riskLevel = ActionGatekeeper.ActionRiskLevel.CRITICAL,
                    onApproved = {
                        appLauncher.openDialer(number)
                        onResult("Phone call initiated to $number")
                    },
                    onDenied = {
                        onResult("User denied phone call execution")
                    }
                )
            }

            "send_message" -> {
                val number = arguments?.get("phone_number")?.toString() ?: ""
                val body = arguments?.get("message_body")?.toString() ?: ""
                ActionGatekeeper.requestExecution(
                    actionType = "SEND_SMS",
                    description = "Send message to $number: \"$body\"",
                    riskLevel = ActionGatekeeper.ActionRiskLevel.CRITICAL,
                    onApproved = {
                        appLauncher.openSms(number, body)
                        onResult("Message prompt opened for $number")
                    },
                    onDenied = {
                        onResult("User denied message sending")
                    }
                )
            }

            "open_app" -> {
                val packageName = arguments?.get("package_name")?.toString() ?: ""
                ActionGatekeeper.requestExecution(
                    actionType = "OPEN_APP",
                    description = "Open application package: $packageName",
                    riskLevel = ActionGatekeeper.ActionRiskLevel.MODERATE,
                    onApproved = {
                        val ok = appLauncher.launchAppByPackage(packageName)
                        onResult(if (ok) "App opened: $packageName" else "Failed to open app")
                    },
                    onDenied = {
                        onResult("User cancelled opening app")
                    }
                )
            }

            "remember_fact" -> {
                val key = arguments?.get("key")?.toString() ?: "fact"
                val value = arguments?.get("value")?.toString() ?: ""
                val category = arguments?.get("category")?.toString() ?: "preference"
                memoryRepo.saveFact(category, key, value)
                onResult("Stored in persistent memory: $key = $value")
            }

            "recall_memory" -> {
                val query = arguments?.get("query")?.toString() ?: ""
                val facts = memoryRepo.searchMemories(query)
                val summary = facts.joinToString("; ") { "${it.memoryKey}: ${it.memoryValue}" }
                onResult(if (summary.isNotBlank()) summary else "No relevant memories found")
            }

            else -> {
                Log.w(TAG, "Unknown tool: $toolName")
                onResult("Tool $toolName is not supported on this device")
            }
        }
    }
}
