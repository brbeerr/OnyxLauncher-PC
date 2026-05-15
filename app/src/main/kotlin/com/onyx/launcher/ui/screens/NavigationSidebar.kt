package com.onyx.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.launcher.ui.Screen

@Composable
fun NavigationSidebar(currentScreen: Screen, onScreenChange: (Screen) -> Unit) {
    Column(modifier = Modifier.fillMaxHeight().width(220.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Text("Onyx Launcher", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 32.dp, top = 8.dp))
        NavItem(Icons.Default.Home, "Home", currentScreen == Screen.HOME) { onScreenChange(Screen.HOME) }
        NavItem(Icons.Default.Inventory, "Versions", currentScreen == Screen.VERSIONS) { onScreenChange(Screen.VERSIONS) }
        NavItem(Icons.Default.Extension, "Mods", currentScreen == Screen.MODS) { onScreenChange(Screen.MODS) }
        NavItem(Icons.Default.Person, "Accounts", currentScreen == Screen.ACCOUNTS) { onScreenChange(Screen.ACCOUNTS) }
        Spacer(modifier = Modifier.weight(1f))
        NavItem(Icons.Default.Settings, "Settings", currentScreen == Screen.SETTINGS) { onScreenChange(Screen.SETTINGS) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.background
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = color, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
    Spacer(modifier = Modifier.height(4.dp))
}
