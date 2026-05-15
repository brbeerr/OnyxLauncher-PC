package com.onyx.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.launcher.game.AccountsManager
import com.onyx.launcher.game.InstalledVersion
import com.onyx.launcher.game.VersionManager
import com.onyx.launcher.game.launch.GameLauncher
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    val currentAccount by AccountsManager.currentAccount.collectAsState()
    val installedVersions by VersionManager.installedVersions.collectAsState()
    var selectedVersion by remember { mutableStateOf<InstalledVersion?>(null) }
    var isLaunching by remember { mutableStateOf(false) }
    var showVersionSelector by remember { mutableStateOf(false) }
    
    LaunchedEffect(installedVersions) { if (selectedVersion == null && installedVersions.isNotEmpty()) selectedVersion = installedVersions.first() }
    
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Welcome back,", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(currentAccount?.username ?: "Guest", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text((currentAccount?.username?.firstOrNull() ?: "?").uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Ready to Play", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory, "Version", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Version", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedVersion?.id ?: "No version installed", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    if (installedVersions.isNotEmpty()) IconButton(onClick = { showVersionSelector = true }) { Icon(Icons.Default.ExpandMore, "Select") }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(onClick = {
                    selectedVersion?.let { v -> currentAccount?.let { a ->
                        scope.launch {
                            isLaunching = true
                            GameLauncher.launch(v.id, a, maxMemory = 4096, onExit = { isLaunching = false })
                        }
                    }}
                }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = selectedVersion != null && currentAccount != null && !isLaunching, shape = RoundedCornerShape(16.dp)) {
                    if (isLaunching) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(modifier = Modifier.width(12.dp)); Text("Launching...") }
                    else { Icon(Icons.Default.PlayArrow, "Play"); Spacer(modifier = Modifier.width(8.dp)); Text("PLAY", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
                
                if (currentAccount == null) { Spacer(modifier = Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, "Warning", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Please add an account first", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) } }
                if (selectedVersion == null) { Spacer(modifier = Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, "Warning", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Please install a version first", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) } }
            }
        }
    }
    
    if (showVersionSelector) {
        AlertDialog(onDismissRequest = { showVersionSelector = false }, title = { Text("Select Version") },
            text = { Column { installedVersions.forEach { v ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (selectedVersion?.id == v.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface).clickable { selectedVersion = v; showVersionSelector = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(v.id); Spacer(modifier = Modifier.weight(1f)); Text(v.type, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }}},
            confirmButton = { TextButton(onClick = { showVersionSelector = false }) { Text("Close") } }
        )
    }
}
