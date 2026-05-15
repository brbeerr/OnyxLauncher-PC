package com.onyx.launcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.launcher.utils.PathManager
import java.awt.Desktop
import java.io.File

@Composable
fun SettingsScreen() {
    var maxMemory by remember { mutableStateOf(4096) }
    var minMemory by remember { mutableStateOf(512) }
    var javaPath by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        
        SettingsSection("Java", Icons.Default.Code) {
            OutlinedTextField(value = javaPath, onValueChange = { javaPath = it }, label = { Text("Java Path (leave empty for auto)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Memory Allocation", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) { Text("Minimum: ${minMemory}MB", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Slider(value = minMemory.toFloat(), onValueChange = { minMemory = it.toInt() }, valueRange = 256f..maxMemory.toFloat()) }
                Column(modifier = Modifier.weight(1f)) { Text("Maximum: ${maxMemory}MB", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Slider(value = maxMemory.toFloat(), onValueChange = { maxMemory = it.toInt() }, valueRange = minMemory.toFloat()..16384f) }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSection("Directories", Icons.Default.Folder) {
            DirItem("Launcher", PathManager.DIR_LAUNCHER.absolutePath)
            Spacer(modifier = Modifier.height(12.dp))
            DirItem("Game", PathManager.DIR_GAME.absolutePath)
            Spacer(modifier = Modifier.height(12.dp))
            DirItem("Versions", PathManager.DIR_VERSIONS.absolutePath)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSection("About", Icons.Default.Info) {
            Text("Onyx Launcher", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Version 1.0.0", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Based on ZalithLauncher - GPLv3", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, title, tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(12.dp)); Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun DirItem(label: String, path: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Medium); Text(path, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = { try { Desktop.getDesktop().open(File(path)) } catch (e: Exception) {} }) { Icon(Icons.Default.FolderOpen, "Open", tint = MaterialTheme.colorScheme.primary) }
    }
}
