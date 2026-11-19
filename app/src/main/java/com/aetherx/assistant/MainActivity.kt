package com.aetherx.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aetherx.assistant.ui.components.SafetyApprovalDialog
import com.aetherx.assistant.ui.screens.*
import com.aetherx.assistant.ui.theme.*
import com.aetherx.automation.ActionGatekeeper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AetherXTheme {
                MainAppHost()
            }
        }
    }
}

@Composable
fun MainAppHost() {
    var currentTab by remember { mutableStateOf(0) }
    var pendingApprovalAction by remember { mutableStateOf<ActionGatekeeper.PendingAction?>(null) }

    LaunchedEffect(Unit) {
        ActionGatekeeper.pendingApprovals.collect { action ->
            pendingApprovalAction = action
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = CyberSurface,
                contentColor = CyberCyan
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Grain, contentDescription = "Live Voice") },
                    label = { Text("Core") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        unselectedIconColor = CyberTextSecondary,
                        unselectedTextColor = CyberTextSecondary,
                        indicatorColor = CyberGlass
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Neural") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        unselectedIconColor = CyberTextSecondary,
                        unselectedTextColor = CyberTextSecondary,
                        indicatorColor = CyberGlass
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = "Vision") },
                    label = { Text("Vision") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        unselectedIconColor = CyberTextSecondary,
                        unselectedTextColor = CyberTextSecondary,
                        indicatorColor = CyberGlass
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("System") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        unselectedIconColor = CyberTextSecondary,
                        unselectedTextColor = CyberTextSecondary,
                        indicatorColor = CyberGlass
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CyberBgDark)
        ) {
            when (currentTab) {
                0 -> CompanionScreen()
                1 -> ChatScreen()
                2 -> VisionScreen()
                3 -> SettingsScreen()
            }

            // Global Safety Authorization Dialog
            pendingApprovalAction?.let { action ->
                SafetyApprovalDialog(
                    action = action,
                    onDismiss = { pendingApprovalAction = null }
                )
            }
        }
    }
}
