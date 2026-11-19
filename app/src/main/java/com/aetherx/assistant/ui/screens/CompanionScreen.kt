package com.aetherx.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherx.assistant.ui.components.CyberOrb
import com.aetherx.assistant.ui.theme.*
import com.aetherx.security.PrivacyShield

@Composable
fun CompanionScreen() {
    val privacyState by PrivacyShield.state.collectAsState()
    var isLiveActive by remember { mutableStateOf(true) }
    var personaMode by remember { mutableStateOf("AETHER PRIME") }
    var statusText by remember { mutableStateOf("QUANTUM CORE ONLINE • LISTENING") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBgDark)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AETHER X",
                    color = CyberCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "HYBRID MULTIMODAL CORE",
                    color = CyberTextSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }

            Surface(
                color = CyberGlass,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Text(
                    text = personaMode,
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Center Animated Cyber Orb
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CyberOrb(
                energyLevel = if (privacyState.microphoneMuted) 0f else 0.4f,
                isListening = isLiveActive && !privacyState.microphoneMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (privacyState.microphoneMuted) "MIC MUTED • HARDWARE SHIELD ACTIVE" else statusText,
                color = if (privacyState.microphoneMuted) CyberMagenta else CyberCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute / Privacy Toggle
            IconButton(
                onClick = { PrivacyShield.toggleMicrophone() },
                modifier = Modifier
                    .size(56.dp)
                    .background(CyberGlass, CircleShape)
                    .border(1.dp, if (privacyState.microphoneMuted) CyberMagenta else CyberCyan, CircleShape)
            ) {
                Icon(
                    imageVector = if (privacyState.microphoneMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute Toggle",
                    tint = if (privacyState.microphoneMuted) CyberMagenta else CyberCyan
                )
            }

            // Power / Disconnect
            IconButton(
                onClick = { isLiveActive = !isLiveActive },
                modifier = Modifier
                    .size(56.dp)
                    .background(CyberGlass, CircleShape)
                    .border(1.dp, CyberViolet, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Session Toggle",
                    tint = CyberViolet
                )
            }
        }
    }
}
