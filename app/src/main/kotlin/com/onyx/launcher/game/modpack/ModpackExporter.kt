package com.onyx.launcher.game.modpack

import com.onyx.launcher.game.mods.ModMetadataReader
import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat { CURSEFORGE, MODRINTH, MULTIMC }

data class ExportConfig(
    val name: String,
    val version: String = "1.0.0",
    val author: String = "",
    val mcVersion: String,
    val modLoader: String = "",
    val modLoaderVersion: String = "",
    val format: ExportFormat,
    val includeMods: Boolean = true,
    val includeConfig: Boolean = true,
    val includeResourcePacks: Boolean = false,
    val includeShaders: Boolean = false,
    val includeWorlds: Boolean = false
)

object ModpackExporter {
    
    suspend fun export(config: ExportConfig, outputFile: File, gameDir: File = PathManager.DIR_GAME): Result<File> = withContext(Dispatchers.IO) {
        try {
            when (config.format) {
                ExportFormat.MODRINTH -> exportModrinth(config, outputFile, gameDir)
                ExportFormat.CURSEFORGE -> exportCurseForge(config, outputFile, gameDir)
                ExportFormat.MULTIMC -> exportMultiMC(config, outputFile, gameDir)
            }
        } catch (e: Exception) { Logger.error("Export failed", e); Result.failure(e) }
    }
    
    private fun exportModrinth(config: ExportConfig, outputFile: File, gameDir: File): Result<File> {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // Build modrinth.index.json
            val index = buildJsonObject {
                put("formatVersion", 1)
                put("game", "minecraft")
                put("versionId", config.version)
                put("name", config.name)
                putJsonObject("dependencies") {
                    put("minecraft", config.mcVersion)
                    if (config.modLoader == "fabric") put("fabric-loader", config.modLoaderVersion)
                    if (config.modLoader == "forge") put("forge", config.modLoaderVersion)
                    if (config.modLoader == "quilt") put("quilt-loader", config.modLoaderVersion)
                }
                putJsonArray("files") {} // Local mods go to overrides
            }
            
            zos.putNextEntry(ZipEntry("modrinth.index.json"))
            zos.write(index.toString().toByteArray())
            zos.closeEntry()
            
            // Add overrides
            val overrides = mutableListOf<Pair<String, File>>()
            if (config.includeMods) addDirectoryFiles(overrides, "mods", File(gameDir, "mods"))
            if (config.includeConfig) addDirectoryFiles(overrides, "config", File(gameDir, "config"))
            if (config.includeResourcePacks) addDirectoryFiles(overrides, "resourcepacks", File(gameDir, "resourcepacks"))
            if (config.includeShaders) addDirectoryFiles(overrides, "shaderpacks", File(gameDir, "shaderpacks"))
            if (config.includeWorlds) addDirectoryFiles(overrides, "saves", File(gameDir, "saves"))
            
            for ((path, file) in overrides) {
                zos.putNextEntry(ZipEntry("overrides/$path"))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return Result.success(outputFile)
    }
    
    private fun exportCurseForge(config: ExportConfig, outputFile: File, gameDir: File): Result<File> {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val loaderId = when {
                config.modLoader == "forge" -> "forge-${config.modLoaderVersion}"
                config.modLoader == "fabric" -> "fabric-${config.modLoaderVersion}"
                config.modLoader == "neoforge" -> "neoforge-${config.modLoaderVersion}"
                else -> ""
            }
            
            val manifest = buildJsonObject {
                putJsonObject("minecraft") {
                    put("version", config.mcVersion)
                    putJsonArray("modLoaders") {
                        if (loaderId.isNotEmpty()) addJsonObject { put("id", loaderId); put("primary", true) }
                    }
                }
                put("manifestType", "minecraftModpack")
                put("manifestVersion", 1)
                put("name", config.name)
                put("version", config.version)
                put("author", config.author)
                putJsonArray("files") {} // Would need CurseForge project IDs
                put("overrides", "overrides")
            }
            
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toString().toByteArray())
            zos.closeEntry()
            
            val overrides = mutableListOf<Pair<String, File>>()
            if (config.includeMods) addDirectoryFiles(overrides, "mods", File(gameDir, "mods"))
            if (config.includeConfig) addDirectoryFiles(overrides, "config", File(gameDir, "config"))
            
            for ((path, file) in overrides) {
                zos.putNextEntry(ZipEntry("overrides/$path"))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return Result.success(outputFile)
    }
    
    private fun exportMultiMC(config: ExportConfig, outputFile: File, gameDir: File): Result<File> {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val mmcPack = buildJsonObject {
                putJsonArray("components") {
                    addJsonObject { put("uid", "net.minecraft"); put("version", config.mcVersion) }
                    if (config.modLoader.isNotEmpty()) {
                        val uid = when (config.modLoader) {
                            "fabric" -> "net.fabricmc.fabric-loader"
                            "forge" -> "net.minecraftforge"
                            "quilt" -> "org.quiltmc.quilt-loader"
                            else -> config.modLoader
                        }
                        addJsonObject { put("uid", uid); put("version", config.modLoaderVersion) }
                    }
                }
                put("formatVersion", 1)
            }
            
            zos.putNextEntry(ZipEntry("mmc-pack.json"))
            zos.write(mmcPack.toString().toByteArray())
            zos.closeEntry()
            
            val instanceCfg = "InstanceType=OneSix\nname=${config.name}\n"
            zos.putNextEntry(ZipEntry("instance.cfg"))
            zos.write(instanceCfg.toByteArray())
            zos.closeEntry()
            
            val overrides = mutableListOf<Pair<String, File>>()
            if (config.includeMods) addDirectoryFiles(overrides, ".minecraft/mods", File(gameDir, "mods"))
            if (config.includeConfig) addDirectoryFiles(overrides, ".minecraft/config", File(gameDir, "config"))
            
            for ((path, file) in overrides) {
                zos.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return Result.success(outputFile)
    }
    
    private fun addDirectoryFiles(list: MutableList<Pair<String, File>>, prefix: String, dir: File) {
        if (!dir.exists()) return
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relativePath = file.relativeTo(dir).path.replace("\\", "/")
            list.add("$prefix/$relativePath" to file)
        }
    }
}
