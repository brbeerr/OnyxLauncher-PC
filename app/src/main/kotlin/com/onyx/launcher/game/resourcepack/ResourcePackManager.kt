package com.onyx.launcher.game.resourcepack

import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.ZipFile

data class ResourcePack(
    val file: File,
    val name: String,
    val description: String = "",
    val packFormat: Int = 0,
    val iconBytes: ByteArray? = null
) {
    val mcVersionRange: String get() = when (packFormat) {
        1 -> "1.6.1 - 1.8.9"
        2 -> "1.9 - 1.10.2"
        3 -> "1.11 - 1.12.2"
        4 -> "1.13 - 1.14.4"
        5 -> "1.15 - 1.16.1"
        6 -> "1.16.2 - 1.16.5"
        7 -> "1.17 - 1.17.1"
        8 -> "1.18 - 1.18.2"
        9 -> "1.19 - 1.19.2"
        12 -> "1.19.3"
        13 -> "1.19.4"
        15 -> "1.20 - 1.20.1"
        18 -> "1.20.2"
        22 -> "1.20.3 - 1.20.4"
        32 -> "1.20.5 - 1.20.6"
        34 -> "1.21+"
        else -> "Unknown"
    }
}

object ResourcePackManager {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    fun getResourcePacks(gameDir: File = PathManager.DIR_GAME): List<ResourcePack> {
        val rpDir = File(gameDir, "resourcepacks")
        if (!rpDir.exists()) return emptyList()
        
        return rpDir.listFiles()?.mapNotNull { file ->
            when {
                file.isFile && file.extension == "zip" -> readZipPack(file)
                file.isDirectory -> readFolderPack(file)
                else -> null
            }
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    
    private fun readZipPack(file: File): ResourcePack? {
        return try {
            ZipFile(file).use { zip ->
                val mcmeta = zip.getEntry("pack.mcmeta") ?: return null
                val content = zip.getInputStream(mcmeta).bufferedReader().readText()
                val pack = parseMcmeta(content)
                
                val icon = zip.getEntry("pack.png")?.let { entry ->
                    zip.getInputStream(entry).readBytes()
                }
                
                ResourcePack(
                    file = file,
                    name = file.nameWithoutExtension,
                    description = pack.first,
                    packFormat = pack.second,
                    iconBytes = icon
                )
            }
        } catch (e: Exception) {
            Logger.warn("Failed to read resource pack: ${file.name}")
            null
        }
    }
    
    private fun readFolderPack(dir: File): ResourcePack? {
        val mcmeta = File(dir, "pack.mcmeta")
        if (!mcmeta.exists()) return null
        
        return try {
            val pack = parseMcmeta(mcmeta.readText())
            val icon = File(dir, "pack.png").let { if (it.exists()) it.readBytes() else null }
            
            ResourcePack(
                file = dir,
                name = dir.name,
                description = pack.first,
                packFormat = pack.second,
                iconBytes = icon
            )
        } catch (e: Exception) { null }
    }
    
    private fun parseMcmeta(content: String): Pair<String, Int> {
        val obj = json.parseToJsonElement(content).jsonObject
        val pack = obj["pack"]?.jsonObject ?: return "" to 0
        val description = when (val desc = pack["description"]) {
            is JsonPrimitive -> desc.content
            is JsonObject -> desc["text"]?.jsonPrimitive?.content ?: ""
            is JsonArray -> desc.joinToString("") { it.jsonPrimitive.content }
            else -> ""
        }
        val format = pack["pack_format"]?.jsonPrimitive?.intOrNull ?: 0
        return description.replace(Regex("§[0-9a-fk-or]"), "") to format
    }
    
    fun deleteResourcePack(pack: ResourcePack): Boolean {
        return if (pack.file.isDirectory) pack.file.deleteRecursively()
        else pack.file.delete()
    }
}
