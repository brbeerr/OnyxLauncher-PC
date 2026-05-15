package com.onyx.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.launcher.game.mods.*
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.launch

@Composable
fun ModsScreen() {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLoader by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<ModProject>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var installedMods by remember { mutableStateOf(ModsRepository.getInstalledMods()) }
    var selectedProject by remember { mutableStateOf<ModProject?>(null) }
    var projectVersions by remember { mutableStateOf<List<ModVersion>>(emptyList()) }
    var isLoadingVersions by remember { mutableStateOf(false) }
    
    fun doSearch() {
        if (searchQuery.isBlank()) { searchResults = emptyList(); return }
        scope.launch {
            isSearching = true
            ModsRepository.search(SearchParams(query = searchQuery, loader = selectedLoader)).onSuccess { searchResults = it.projects }
            isSearching = false
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text("Mods", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Browse") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; installedMods = ModsRepository.getInstalledMods() }, text = { Text("Installed (${installedMods.size})") })
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        when (selectedTab) {
            0 -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.weight(1f), placeholder = { Text("Search mods...") }, leadingIcon = { Icon(Icons.Default.Search, "Search") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    Button(onClick = { doSearch() }, enabled = searchQuery.isNotBlank() && !isSearching, shape = RoundedCornerShape(12.dp)) {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Search")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Forge", "Fabric", "NeoForge", "Quilt").forEach { loader ->
                        FilterChip(selected = selectedLoader == loader, onClick = { selectedLoader = if (selectedLoader == loader) null else loader; if (searchQuery.isNotBlank()) doSearch() }, label = { Text(loader) })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(searchResults) { project ->
                        Card(modifier = Modifier.fillMaxWidth().clickable {
                            selectedProject = project
                            scope.launch { isLoadingVersions = true; ModsRepository.getVersions(project.id, project.platform).onSuccess { projectVersions = it }; isLoadingVersions = false }
                        }, shape = RoundedCornerShape(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Extension, "Mod", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(project.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    project.author?.let { Text("by $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                                    project.description?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Download, "Downloads", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${project.downloadCount / 1000}K", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (installedMods.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Extension, "No mods", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(16.dp)); Text("No mods installed", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(installedMods) { mod ->
                        var showDelete by remember { mutableStateOf(false) }
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (mod.enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (mod.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Extension, "Mod", tint = if (mod.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) { Text(mod.name, fontWeight = FontWeight.Medium); Text(mod.file.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Switch(checked = mod.enabled, onCheckedChange = { ModsRepository.toggleMod(mod); installedMods = ModsRepository.getInstalledMods() })
                                IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                        if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Delete mod?") }, text = { Text("Delete '${mod.name}'?") }, confirmButton = { Button(onClick = { ModsRepository.deleteMod(mod); installedMods = ModsRepository.getInstalledMods(); showDelete = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } })
                    }
                }
            }
        }
    }
    
    selectedProject?.let { project ->
        AlertDialog(onDismissRequest = { selectedProject = null; projectVersions = emptyList() }, title = { Text(project.title) },
            text = { Column(modifier = Modifier.heightIn(max = 400.dp)) {
                project.description?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(16.dp)) }
                Text("Versions", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoadingVersions) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) { items(projectVersions.take(20)) { version ->
                    var isDownloading by remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(version.versionNumber, fontWeight = FontWeight.Medium, fontSize = 14.sp); Text(version.gameVersions.take(3).joinToString(", "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = { scope.launch { isDownloading = true; ModsRepository.downloadMod(version, PathManager.DIR_MODS); installedMods = ModsRepository.getInstalledMods(); isDownloading = false } }, enabled = !isDownloading) {
                            if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Download, "Download")
                        }
                    }
                }}
            }},
            confirmButton = { TextButton(onClick = { selectedProject = null; projectVersions = emptyList() }) { Text("Close") } }
        )
    }
}
