package com.aetherx.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherx.assistant.ui.theme.CyberCyan
import com.aetherx.assistant.ui.theme.CyberGlass
import com.aetherx.assistant.ui.theme.CyberMagenta
import com.aetherx.assistant.ui.theme.CyberTextPrimary
import com.aetherx.automation.ActionGatekeeper

@Composable
fun SafetyApprovalDialog(
    action: ActionGatekeeper.PendingAction,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            ActionGatekeeper.denyAction(action.id)
            onDismiss()
        },
        containerColor = CyberGlass,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .border(1.dp, CyberCyan, RoundedCornerShape(16.dp))
            .padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚠️ SAFETY AUTHORIZATION",
                    color = CyberMagenta,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AETHER X is requesting permission to execute an automated device action:",
                    color = CyberTextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = action.description,
                    color = CyberCyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Risk Level: ${action.riskLevel.name}. This action requires explicit approval.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ActionGatekeeper.approveAction(action.id)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Approve & Execute", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    ActionGatekeeper.denyAction(action.id)
                    onDismiss()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberMagenta)
            ) {
                Text("Deny Action")
            }
        }
    )
}
