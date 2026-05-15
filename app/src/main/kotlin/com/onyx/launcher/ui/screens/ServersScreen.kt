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
import com.onyx.launcher.game.multiplayer.SavedServer
import com.onyx.launcher.game.multiplayer.ServerPinger
import com.onyx.launcher.game.multiplayer.ServerStatus
import com.onyx.launcher.game.multiplayer.TerracottaManager
import com.onyx.launcher.game.multiplayer.TerracottaState
import kotlinx.coroutines.launch

@Composable
fun ServersScreen() {
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf(listOf(
        SavedServer("Hypixel", "mc.hypixel.net"),
        SavedServer("Local", "127.0.0.1")
    )) }
    var serverStatuses by remember { mutableStateOf<Map<String, ServerStatus>>(emptyMap()) }
    var isPinging by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showTerracottaDialog by remember { mutableStateOf(false) }
    val terracottaState by TerracottaManager.state.collectAsState()
    val terracottaSession by TerracottaManager.session.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Servers", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showTerracottaDialog = true }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Share, "LAN Online")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LAN Online")
                }
                Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Add, "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Server")
                }
                IconButton(onClick = {
                    scope.launch {
                        isPinging = true
                        servers.forEach { server ->
                            val status = ServerPinger.ping(server.address, server.port)
                            serverStatuses = serverStatuses + (server.address to status)
                        }
                        isPinging = false
                    }
                }, enabled = !isPinging) {
                    if (isPinging) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(servers) { server ->
                val status = serverStatuses[server.address]
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(
                            if (status?.online == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ), contentAlignment = Alignment.Center) {
                            Icon(
                                if (status?.online == true) Icons.Default.CheckCircle else Icons.Default.CloudOff,
                                "Status",
                                tint = if (status?.online == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(server.address + (if (server.port != 25565) ":${server.port}" else ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (status?.online == true) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(status.motd, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        if (status?.online == true) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${status.playersOnline}/${status.playersMax}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                Text(status.version, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (status.latency > 0) Text("${status.latency}ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (status != null) {
                            Text("Offline", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Server") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Server Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address (e.g. mc.server.com:25565)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = { Button(onClick = {
                val parts = address.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 25565
                servers = servers + SavedServer(name.ifBlank { host }, host, port)
                showAddDialog = false
            }, enabled = address.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
    
    if (showTerracottaDialog) {
        var lanPort by remember { mutableStateOf("25565") }
        var joinCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTerracottaDialog = false },
            title = { Text("LAN Online (Terracotta)") },
            text = {
                Column {
                    Text("Share your LAN world with friends over the internet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (terracottaState == TerracottaState.HOSTING) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Hosting!", fontWeight = FontWeight.Bold)
                                Text("Share this code: ${terracottaSession?.code}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Text("Host a LAN game:", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = lanPort, onValueChange = { lanPort = it }, label = { Text("LAN Port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch { TerracottaManager.startHosting(lanPort.toIntOrNull() ?: 25565) }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Start Hosting") }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Or join a friend:", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = joinCode, onValueChange = { joinCode = it }, label = { Text("Connection Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (terracottaState == TerracottaState.HOSTING || terracottaState == TerracottaState.CONNECTED)
                    Button(onClick = { TerracottaManager.stop() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Stop") }
                else TextButton(onClick = { showTerracottaDialog = false }) { Text("Close") }
            },
            dismissButton = { TextButton(onClick = { showTerracottaDialog = false }) { Text("Close") } }
        )
    }
}
