package com.onyx.launcher.network

import com.onyx.launcher.utils.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Mirror(
    val name: String,
    val id: String,
    val replacements: Map<String, String>, // original URL prefix -> mirror URL prefix
    val enabled: Boolean = true
)

object DownloadMirrors {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    
    private val _mirrors = mutableListOf(
        Mirror(
            name = "BMCLAPI (China)",
            id = "bmclapi",
            replacements = mapOf(
                "https://piston-meta.mojang.com" to "https://bmclapi2.bangbang93.com",
                "https://piston-data.mojang.com" to "https://bmclapi2.bangbang93.com",
                "https://launchermeta.mojang.com" to "https://bmclapi2.bangbang93.com",
                "https://launcher.mojang.com" to "https://bmclapi2.bangbang93.com",
                "https://resources.download.minecraft.net" to "https://bmclapi2.bangbang93.com/assets",
                "https://libraries.minecraft.net" to "https://bmclapi2.bangbang93.com/maven",
                "https://files.minecraftforge.net/maven" to "https://bmclapi2.bangbang93.com/maven",
                "https://maven.minecraftforge.net" to "https://bmclapi2.bangbang93.com/maven",
                "https://meta.fabricmc.net" to "https://bmclapi2.bangbang93.com/fabric-meta",
                "https://maven.fabricmc.net" to "https://bmclapi2.bangbang93.com/maven"
            ),
            enabled = false
        ),
        Mirror(
            name = "MCBBS (China)",
            id = "mcbbs",
            replacements = mapOf(
                "https://piston-meta.mojang.com" to "https://download.mcbbs.net",
                "https://piston-data.mojang.com" to "https://download.mcbbs.net",
                "https://launchermeta.mojang.com" to "https://download.mcbbs.net",
                "https://launcher.mojang.com" to "https://download.mcbbs.net",
                "https://resources.download.minecraft.net" to "https://download.mcbbs.net/assets",
                "https://libraries.minecraft.net" to "https://download.mcbbs.net/maven",
                "https://files.minecraftforge.net/maven" to "https://download.mcbbs.net/maven",
                "https://maven.minecraftforge.net" to "https://download.mcbbs.net/maven"
            ),
            enabled = false
        ),
        Mirror(
            name = "Official (No Mirror)",
            id = "official",
            replacements = emptyMap(),
            enabled = true
        )
    )
    
    val mirrors: List<Mirror> get() = _mirrors.toList()
    
    private var activeMirrorId: String = "official"
    
    fun getActiveMirror(): Mirror? = _mirrors.find { it.id == activeMirrorId && it.enabled }
    
    fun setActiveMirror(mirrorId: String) {
        activeMirrorId = mirrorId
        Logger.info("Active download mirror set to: $mirrorId")
    }
    
    fun applyMirror(originalUrl: String): String {
        val mirror = getActiveMirror() ?: return originalUrl
        if (mirror.replacements.isEmpty()) return originalUrl
        
        for ((original, replacement) in mirror.replacements) {
            if (originalUrl.startsWith(original)) {
                return originalUrl.replaceFirst(original, replacement)
            }
        }
        return originalUrl
    }
    
    fun addCustomMirror(mirror: Mirror) {
        _mirrors.removeAll { it.id == mirror.id }
        _mirrors.add(0, mirror)
    }
    
    fun removeMirror(mirrorId: String) {
        if (mirrorId != "official") {
            _mirrors.removeAll { it.id == mirrorId }
        }
    }
    
    fun toggleMirror(mirrorId: String, enabled: Boolean) {
        val idx = _mirrors.indexOfFirst { it.id == mirrorId }
        if (idx >= 0) _mirrors[idx] = _mirrors[idx].copy(enabled = enabled)
    }
}
