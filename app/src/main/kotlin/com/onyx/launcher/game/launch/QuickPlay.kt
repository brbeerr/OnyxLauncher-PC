package com.onyx.launcher.game.launch

import kotlinx.serialization.Serializable

enum class QuickPlayType(val displayName: String) {
    NONE("None"),
    MULTIPLAYER("Join Server"),
    SINGLEPLAYER("Load World"),
    REALMS("Join Realm")
}

@Serializable
data class QuickPlayConfig(
    val type: QuickPlayType = QuickPlayType.NONE,
    val serverAddress: String = "",
    val serverPort: Int = 25565,
    val worldName: String = "",
    val realmId: String = ""
) {
    fun toArguments(mcVersion: String): List<String> {
        if (type == QuickPlayType.NONE) return emptyList()
        
        val parts = mcVersion.split(".").mapNotNull { it.toIntOrNull() }
        val minor = parts.getOrElse(1) { 0 }
        
        // 1.20+ uses new --quickPlayMultiplayer/--quickPlaySingleplayer args
        return if (minor >= 20) {
            when (type) {
                QuickPlayType.MULTIPLAYER -> listOf(
                    "--quickPlayMultiplayer", "$serverAddress:$serverPort"
                )
                QuickPlayType.SINGLEPLAYER -> listOf(
                    "--quickPlaySingleplayer", worldName
                )
                QuickPlayType.REALMS -> listOf(
                    "--quickPlayRealms", realmId
                )
                else -> emptyList()
            }
        } else {
            // Legacy: only server auto-connect supported
            when (type) {
                QuickPlayType.MULTIPLAYER -> listOf(
                    "--server", serverAddress,
                    "--port", serverPort.toString()
                )
                else -> emptyList()
            }
        }
    }
}
