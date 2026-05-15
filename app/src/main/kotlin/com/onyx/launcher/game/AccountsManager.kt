package com.onyx.launcher.game

import com.onyx.launcher.data.Account
import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
private data class AccountsData(val accounts: List<Account> = emptyList(), val currentAccountId: String? = null)

object AccountsManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = LauncherHttpClient.json
    
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts = _accounts.asStateFlow()
    
    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount = _currentAccount.asStateFlow()
    
    fun loadAccounts() = scope.launch {
        try {
            val file = PathManager.FILE_ACCOUNTS
            if (!file.exists()) { _accounts.value = emptyList(); return@launch }
            val data = json.decodeFromString<AccountsData>(file.readText())
            _accounts.value = data.accounts.sortedWith(compareBy({ it.accountTypePriority() }, { it.username }))
            data.currentAccountId?.let { id -> _currentAccount.value = _accounts.value.find { it.uniqueUUID == id } }
            if (_currentAccount.value == null && _accounts.value.isNotEmpty()) _currentAccount.value = _accounts.value.first()
        } catch (e: Exception) { _accounts.value = emptyList() }
    }
    
    private fun saveAccounts() = scope.launch {
        try {
            PathManager.FILE_ACCOUNTS.parentFile?.mkdirs()
            PathManager.FILE_ACCOUNTS.writeText(json.encodeToString(AccountsData(_accounts.value, _currentAccount.value?.uniqueUUID)))
        } catch (e: Exception) { }
    }
    
    fun addAccount(account: Account) {
        val existing = _accounts.value.find { it.profileId == account.profileId && it.accountType == account.accountType }
        if (existing != null) updateAccount(account)
        else { _accounts.value = (_accounts.value + account).sortedWith(compareBy({ it.accountTypePriority() }, { it.username })); saveAccounts() }
        if (_currentAccount.value == null) setCurrentAccount(account)
    }
    
    fun updateAccount(account: Account) {
        _accounts.value = _accounts.value.map { if (it.uniqueUUID == account.uniqueUUID) account else it }
        if (_currentAccount.value?.uniqueUUID == account.uniqueUUID) _currentAccount.value = account
        saveAccounts()
    }
    
    fun deleteAccount(account: Account) {
        _accounts.value = _accounts.value.filter { it.uniqueUUID != account.uniqueUUID }
        if (_currentAccount.value?.uniqueUUID == account.uniqueUUID) _currentAccount.value = _accounts.value.firstOrNull()
        saveAccounts()
    }
    
    fun setCurrentAccount(account: Account) { _currentAccount.value = account; saveAccounts() }
}
