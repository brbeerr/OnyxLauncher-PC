package com.onyx.launcher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.onyx.launcher.data.Account
import com.onyx.launcher.data.AccountType
import com.onyx.launcher.game.AccountsManager
import com.onyx.launcher.game.auth.MicrosoftAuth
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

@Composable
fun AccountsScreen() {
    val scope = rememberCoroutineScope()
    val accounts by AccountsManager.accounts.collectAsState()
    val currentAccount by AccountsManager.currentAccount.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showMsDialog by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf<com.onyx.launcher.game.auth.DeviceCodeResponse?>(null) }
    var authStatus by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Accounts", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, "Add"); Spacer(modifier = Modifier.width(8.dp)); Text("Add Account") }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        if (accounts.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PersonOff, "No accounts", modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(16.dp)); Text("No accounts added", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(accounts) { acc ->
            val isSelected = acc.uniqueUUID == currentAccount?.uniqueUUID
            var showDelete by remember { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().clickable { AccountsManager.setCurrentAccount(acc) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (acc.isMicrosoftAccount()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) { Text(acc.username.first().uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) { Text(acc.username, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Row(verticalAlignment = Alignment.CenterVertically) { val (icon, label, color) = if (acc.isMicrosoftAccount()) Triple(Icons.Default.Security, "Microsoft", MaterialTheme.colorScheme.primary) else Triple(Icons.Default.WifiOff, "Offline", MaterialTheme.colorScheme.secondary); Icon(icon, label, modifier = Modifier.size(14.dp), tint = color); Spacer(modifier = Modifier.width(4.dp)); Text(label, fontSize = 12.sp, color = color) } }
                    if (isSelected) { Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)) }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                }
            }
            if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Delete Account?") }, text = { Text("Delete '${acc.username}'?") }, confirmButton = { Button(onClick = { AccountsManager.deleteAccount(acc); showDelete = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } })
        } }
    }
    
    if (showAddDialog) AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Add Account") },
        text = { Column {
            Card(modifier = Modifier.fillMaxWidth().clickable { showAddDialog = false; showMsDialog = true; scope.launch { isAuthenticating = true; authError = null; MicrosoftAuth.authenticateWithDeviceCode({ authStatus = it.name }, { deviceCode = it; try { Desktop.getDesktop().browse(URI(it.verificationUri)) } catch (e: Exception) {} }).onSuccess { AccountsManager.addAccount(it); showMsDialog = false; deviceCode = null }.onFailure { authError = it.message }; isAuthenticating = false } }, shape = RoundedCornerShape(12.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Security, "Microsoft", tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(16.dp)); Column { Text("Microsoft Account", fontWeight = FontWeight.SemiBold); Text("Login with Microsoft", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth().clickable { showAddDialog = false; showOfflineDialog = true }, shape = RoundedCornerShape(12.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WifiOff, "Offline", tint = MaterialTheme.colorScheme.secondary); Spacer(modifier = Modifier.width(16.dp)); Column { Text("Offline Account", fontWeight = FontWeight.SemiBold); Text("Play without internet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        }},
        confirmButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
    )
    
    if (showMsDialog) AlertDialog(onDismissRequest = { if (!isAuthenticating) { showMsDialog = false; deviceCode = null } }, title = { Text("Microsoft Login") },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (deviceCode != null && authError == null) { Text("Open this link:"); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(8.dp)) { Text(deviceCode!!.verificationUri, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer) }; Spacer(modifier = Modifier.height(16.dp)); Text("Enter this code:"); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) { Text(deviceCode!!.userCode, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }; Spacer(modifier = Modifier.height(16.dp)); if (isAuthenticating) CircularProgressIndicator(modifier = Modifier.size(32.dp)); Text(authStatus, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else if (authError != null) { Icon(Icons.Default.Error, "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)); Spacer(modifier = Modifier.height(16.dp)); Text(authError!!, color = MaterialTheme.colorScheme.error) }
            else { CircularProgressIndicator(); Text("Getting device code...") }
        }},
        confirmButton = { TextButton(onClick = { showMsDialog = false; deviceCode = null; authError = null }) { Text(if (authError != null) "Close" else "Cancel") } }
    )
    
    if (showOfflineDialog) {
        var username by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { showOfflineDialog = false }, title = { Text("Add Offline Account") },
            text = { OutlinedTextField(value = username, onValueChange = { username = it; error = null }, label = { Text("Username") }, singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { when { username.isBlank() -> error = "Username cannot be empty"; username.length < 3 -> error = "Min 3 characters"; username.length > 16 -> error = "Max 16 characters"; !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> error = "Invalid characters"; else -> { AccountsManager.addAccount(Account.createOffline(username)); showOfflineDialog = false } } }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showOfflineDialog = false }) { Text("Cancel") } }
        )
    }
}
