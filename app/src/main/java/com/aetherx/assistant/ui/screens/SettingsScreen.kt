package com.aetherx.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherx.assistant.ui.theme.*
import com.aetherx.security.PrivacyShield

@Composable
fun SettingsScreen() {
    val privacyState by PrivacyShield.state.collectAsState()
    var selectedProvider by remember { mutableStateOf("Google Gemini") }
    var selectedVoice by remember { mutableStateOf("Puck") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBgDark)
            .padding(16.dp)
    ) {
        Text(
            text = "SYSTEM CONFIGURATION",
            color = CyberCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Security Status Card
        Surface(
            color = CyberGlass,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "HARDWARE KEYSTORE SECURITY",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AES-256-GCM Hardware-backed Android Keystore Active\nAll API keys & memory facts encrypted at rest",
                    color = CyberTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Provider Section
        Text(
            text = "AI ORCHESTRATION PROVIDER",
            color = CyberTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Google Gemini", "OpenAI", "Auto Hybrid").forEach { provider ->
                val isSelected = provider == selectedProvider
                Button(
                    onClick = { selectedProvider = provider },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) CyberCyan else CyberGlass,
                        contentColor = if (isSelected) Color.Black else CyberTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = provider, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy Shield Controls
        Text(
            text = "PRIVACY SHIELD & SENSORS",
            color = CyberTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberGlass, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Local-Only Operation", color = CyberTextPrimary, fontSize = 14.sp)
                Text(text = "Prohibit outbound telemetry", color = CyberTextSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = privacyState.localOnlyMode,
                onCheckedChange = { PrivacyShield.setLocalOnlyMode(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberViolet)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberGlass, RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Action Confirmation Gating", color = CyberTextPrimary, fontSize = 14.sp)
                Text(text = "Require approval for all automation calls", color = CyberTextSecondary, fontSize = 11.sp)
            }
            Switch(
                checked = true,
                onCheckedChange = { },
                enabled = false, // Always enforced for safety!
                colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
            )
        }
    }
}
