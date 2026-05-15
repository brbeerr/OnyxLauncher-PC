package com.onyx.launcher.data

import kotlinx.serialization.Serializable
import java.util.UUID

enum class AccountType(val tag: String) {
    OFFLINE("offline"), MICROSOFT("microsoft"), AUTH_SERVER("auth_server");
    companion object { fun fromTag(tag: String?) = entries.find { it.tag == tag } ?: OFFLINE }
}

enum class SkinModelType { NONE, SLIM, CLASSIC }

@Serializable
data class Account(
    val uniqueUUID: String = UUID.randomUUID().toString().lowercase(),
    var accessToken: String = "0",
    var expiresAt: Long = 0L,
    var clientToken: String = "0",
    var username: String = "Steve",
    var profileId: String = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray()).toString().replace("-", ""),
    var refreshToken: String = "0",
    var xUid: String? = null,
    var otherBaseUrl: String? = null,
    var accountType: String? = AccountType.OFFLINE.tag,
    var skinModelType: SkinModelType = SkinModelType.NONE
) {
    fun isMicrosoftAccount() = accountType == AccountType.MICROSOFT.tag
    fun isOfflineAccount() = accountType == AccountType.OFFLINE.tag
    fun isTokenExpired() = System.currentTimeMillis() >= expiresAt
    fun accountTypePriority() = when { isMicrosoftAccount() -> 0; else -> 2 }
    
    companion object {
        fun createOffline(username: String) = Account(
            username = username,
            profileId = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray()).toString().replace("-", ""),
            accountType = AccountType.OFFLINE.tag
        )
    }
}
