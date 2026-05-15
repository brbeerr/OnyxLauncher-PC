package com.onyx.launcher.game.addons.modloader

import kotlinx.serialization.Serializable

enum class ModLoaderType(val displayName: String) {
    FORGE("Forge"),
    NEOFORGE("NeoForge"),
    FABRIC("Fabric"),
    QUILT("Quilt"),
    LEGACY_FABRIC("Legacy Fabric")
}

@Serializable
data class ModLoaderVersion(
    val loaderType: ModLoaderType,
    val loaderVersion: String,
    val gameVersion: String,
    val stable: Boolean = true
)
