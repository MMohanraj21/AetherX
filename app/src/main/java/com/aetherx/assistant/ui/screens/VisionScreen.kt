package com.aetherx.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherx.assistant.ui.theme.*

@Composable
fun VisionScreen() {
    val modes = listOf("FASHION & ATTIRE", "LANDMARK & TRAVEL", "DOCUMENT OCR", "OBJECT & SCENE")
    var selectedMode by remember { mutableStateOf(modes[0]) }
    var analysisResult by remember { mutableStateOf("Point camera at subject to begin neural analysis...") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBgDark)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MULTIMODAL VISION CORE",
            color = CyberCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Mode Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.take(2).forEach { mode ->
                val isSelected = mode == selectedMode
                Surface(
                    color = if (isSelected) CyberCyan else CyberGlass,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedMode = mode }
                ) {
                    Text(
                        text = mode,
                        color = if (isSelected) Color.Black else CyberTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }

        // Camera Viewfinder Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CyberSurface, RoundedCornerShape(16.dp))
                .border(2.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera viewfinder",
                    tint = CyberCyan.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "CAMERAX SENSOR ACTIVE",
                    color = CyberTextSecondary,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Analysis Result Card
        Surface(
            color = CyberGlass,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberViolet),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "NEURAL INFERENCE [$selectedMode]",
                    color = CyberViolet,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = analysisResult,
                    color = CyberTextPrimary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
