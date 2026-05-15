package com.onyx.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.onyx.launcher.game.AccountsManager
import com.onyx.launcher.game.VersionManager
import com.onyx.launcher.game.multirt.RuntimesManager
import com.onyx.launcher.ui.screens.*
import com.onyx.launcher.ui.theme.OnyxTheme

enum class Screen { HOME, VERSIONS, MODS, ACCOUNTS, SERVERS, SETTINGS }

@Composable
fun App() {
    LaunchedEffect(Unit) {
        AccountsManager.loadAccounts()
        VersionManager.loadInstalledVersions()
        VersionManager.loadVersionManifest()
        RuntimesManager.initialize()
    }
    
    OnyxTheme {
        var currentScreen by remember { mutableStateOf(Screen.HOME) }
        
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationSidebar(currentScreen) { currentScreen = it }
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    when (currentScreen) {
                        Screen.HOME -> HomeScreen()
                        Screen.VERSIONS -> VersionsScreen()
                        Screen.MODS -> ModsScreen()
                        Screen.ACCOUNTS -> AccountsScreen()
                        Screen.SERVERS -> ServersScreen()
                        Screen.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }
    }
}
