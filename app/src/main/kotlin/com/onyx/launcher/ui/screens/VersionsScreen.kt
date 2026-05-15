package com.onyx.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.onyx.launcher.data.VersionType
import com.onyx.launcher.game.VersionManager
import com.onyx.launcher.game.download.GameDownloader
import kotlinx.coroutines.launch

@Composable
fun VersionsScreen() {
    val scope = rememberCoroutineScope()
    val downloader = remember { GameDownloader() }
    val availableVersions by VersionManager.availableVersions.collectAsState()
    val installedVersions by VersionManager.installedVersions.collectAsState()
    val isLoading by VersionManager.isLoading.collectAsState()
    val isDownloading by downloader.isDownloading.collectAsState()
    val downloadProgress by downloader.downloadProgress.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showSnapshots by remember { mutableStateOf(false) }
    var downloadingVersion by remember { mutableStateOf<String?>(null) }
    
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Versions", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { scope.launch { VersionManager.loadVersionManifest(); VersionManager.loadInstalledVersions() } }, enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Icon(Icons.Default.Refresh, "Refresh")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Installed (${installedVersions.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Available") })
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isDownloading) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Downloading: $downloadingVersion", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { downloadProgress.progress }, modifier = Modifier.fillMaxWidth())
                    Text(downloadProgress.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        when (selectedTab) {
            0 -> if (installedVersions.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inbox, "No versions", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(16.dp)); Text("No versions installed", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                 else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(installedVersions) { v -> Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, "Version", tint = MaterialTheme.colorScheme.onPrimaryContainer) }; Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(v.id, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(v.type.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(if (v.hasJar) Icons.Default.CheckCircle else Icons.Default.Warning, "Status", tint = if (v.hasJar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } } } }
            1 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = showSnapshots, onClick = { showSnapshots = !showSnapshots }, label = { Text("Snapshots") }, leadingIcon = if (showSnapshots) { { Icon(Icons.Default.Check, "Selected", Modifier.size(16.dp)) } } else null) }
                Spacer(modifier = Modifier.height(16.dp))
                val filtered = availableVersions.filter { it.versionType == VersionType.RELEASE || (showSnapshots && it.versionType == VersionType.SNAPSHOT) }
                val installedIds = installedVersions.map { it.id }.toSet()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(filtered) { v ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { val (icon, color) = if (v.versionType == VersionType.RELEASE) Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary else Icons.Default.Science to MaterialTheme.colorScheme.tertiary; Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(icon, v.type, tint = color) }; Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(v.id, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(v.releaseTime.take(10), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (v.id in installedIds) Text("Installed", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) else IconButton(onClick = { scope.launch { downloadingVersion = v.id; downloader.downloadVersion(v.id); downloadingVersion = null } }, enabled = !isDownloading) { Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.primary) } } }
                } }
            }
        }
    }
}
