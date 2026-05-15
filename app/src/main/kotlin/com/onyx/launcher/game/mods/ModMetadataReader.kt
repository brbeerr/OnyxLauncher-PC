package com.onyx.launcher.game.mods

import com.onyx.launcher.utils.Logger
import kotlinx.serialization.json.*
import java.io.File
import java.util.jar.JarFile

data class ModMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val authors: List<String> = emptyList(),
    val loader: String = "unknown",
    val iconBytes: ByteArray? = null,
    val mcVersions: List<String> = emptyList(),
    val dependencies: List<ModDependency> = emptyList()
)

data class ModDependency(val modId: String, val versionRange: String = "")

object ModMetadataReader {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    fun readMetadata(jarFile: File): ModMetadata? {
        if (!jarFile.exists() || !jarFile.name.endsWith(".jar")) return null
        return try {
            JarFile(jarFile).use { jar ->
                readFabricMod(jar) ?: readForgeMod(jar) ?: readQuiltMod(jar) ?: readLegacyForgeMod(jar)
            }
        } catch (e: Exception) {
            Logger.warn("Failed to read metadata from ${jarFile.name}: ${e.message}")
            null
        }
    }
    
    private fun readFabricMod(jar: JarFile): ModMetadata? {
        val entry = jar.getEntry("fabric.mod.json") ?: return null
        val content = jar.getInputStream(entry).bufferedReader().readText()
        val obj = json.parseToJsonElement(content).jsonObject
        
        val authors = obj["authors"]?.jsonArray?.mapNotNull { el ->
            when {
                el is JsonPrimitive -> el.content
                el is JsonObject -> el["name"]?.jsonPrimitive?.content
                else -> null
            }
        } ?: emptyList()
        
        val deps = obj["depends"]?.jsonObject?.entries?.map { (k, v) ->
            ModDependency(k, v.jsonPrimitive.content)
        } ?: emptyList()
        
        val iconPath = obj["icon"]?.jsonPrimitive?.content
        val iconBytes = iconPath?.let { path ->
            jar.getEntry(path)?.let { iconEntry ->
                jar.getInputStream(iconEntry).readBytes()
            }
        }
        
        return ModMetadata(
            id = obj["id"]?.jsonPrimitive?.content ?: "unknown",
            name = obj["name"]?.jsonPrimitive?.content ?: jarFile(jar),
            version = obj["version"]?.jsonPrimitive?.content ?: "?",
            description = obj["description"]?.jsonPrimitive?.content ?: "",
            authors = authors,
            loader = "Fabric",
            iconBytes = iconBytes,
            dependencies = deps
        )
    }
    
    private fun readForgeMod(jar: JarFile): ModMetadata? {
        val entry = jar.getEntry("META-INF/mods.toml") ?: return null
        val content = jar.getInputStream(entry).bufferedReader().readText()
        
        // Simple TOML parser for mods.toml
        val modId = extractTomlValue(content, "modId") ?: return null
        val name = extractTomlValue(content, "displayName") ?: modId
        val version = extractTomlValue(content, "version") ?: "?"
        val description = extractTomlValue(content, "description") ?: ""
        val authors = extractTomlValue(content, "authors")?.let { listOf(it) } ?: emptyList()
        
        val logoFile = extractTomlValue(content, "logoFile")
        val iconBytes = logoFile?.let { path ->
            jar.getEntry(path)?.let { jar.getInputStream(it).readBytes() }
        }
        
        return ModMetadata(
            id = modId,
            name = name,
            version = version,
            description = description.trim(),
            authors = authors,
            loader = "Forge",
            iconBytes = iconBytes
        )
    }
    
    private fun readQuiltMod(jar: JarFile): ModMetadata? {
        val entry = jar.getEntry("quilt.mod.json") ?: return null
        val content = jar.getInputStream(entry).bufferedReader().readText()
        val obj = json.parseToJsonElement(content).jsonObject
        val ql = obj["quilt_loader"]?.jsonObject ?: return null
        val meta = ql["metadata"]?.jsonObject
        
        val authors = meta?.get("contributors")?.jsonObject?.keys?.toList() ?: emptyList()
        val iconPath = meta?.get("icon")?.jsonPrimitive?.content
        val iconBytes = iconPath?.let { path ->
            jar.getEntry(path)?.let { jar.getInputStream(it).readBytes() }
        }
        
        return ModMetadata(
            id = ql["id"]?.jsonPrimitive?.content ?: "unknown",
            name = meta?.get("name")?.jsonPrimitive?.content ?: jarFile(jar),
            version = ql["version"]?.jsonPrimitive?.content ?: "?",
            description = meta?.get("description")?.jsonPrimitive?.content ?: "",
            authors = authors,
            loader = "Quilt",
            iconBytes = iconBytes
        )
    }
    
    private fun readLegacyForgeMod(jar: JarFile): ModMetadata? {
        val entry = jar.getEntry("mcmod.info") ?: return null
        val content = jar.getInputStream(entry).bufferedReader().readText()
        return try {
            val arr = json.parseToJsonElement(content)
            val first = when {
                arr is JsonArray && arr.isNotEmpty() -> arr[0].jsonObject
                arr is JsonObject -> arr["modList"]?.jsonArray?.firstOrNull()?.jsonObject ?: arr
                else -> return null
            }
            ModMetadata(
                id = first["modid"]?.jsonPrimitive?.content ?: "unknown",
                name = first["name"]?.jsonPrimitive?.content ?: jarFile(jar),
                version = first["version"]?.jsonPrimitive?.content ?: "?",
                description = first["description"]?.jsonPrimitive?.content ?: "",
                authors = first["authorList"]?.jsonArray?.map { it.jsonPrimitive.content } 
                    ?: first["authors"]?.jsonPrimitive?.content?.split(",")?.map { it.trim() }
                    ?: emptyList(),
                loader = "Forge (Legacy)"
            )
        } catch (e: Exception) { null }
    }
    
    private fun extractTomlValue(content: String, key: String): String? {
        val regex = Regex("""$key\s*=\s*"([^"]*)"|('''([\s\S]*?)''')""")
        val multiLine = Regex("""$key\s*=\s*'''([\s\S]*?)'''""")
        multiLine.find(content)?.let { return it.groupValues[1] }
        regex.find(content)?.let { return it.groupValues[1].ifEmpty { it.groupValues[3] } }
        return null
    }
    
    private fun jarFile(jar: JarFile): String = File(jar.name).nameWithoutExtension
}
