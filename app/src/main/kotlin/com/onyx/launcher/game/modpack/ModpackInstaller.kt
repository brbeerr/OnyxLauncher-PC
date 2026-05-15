package com.onyx.launcher.game.modpack

import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.ZipFile

enum class ModpackFormat { CURSEFORGE, MODRINTH, MULTIMC, UNKNOWN }

@Serializable
data class ModpackManifest(
    val name: String,
    val version: String = "",
    val author: String = "",
    val mcVersion: String,
    val modLoader: String = "",
    val modLoaderVersion: String = "",
    val files: List<ModpackFile> = emptyList(),
    val format: ModpackFormat = ModpackFormat.UNKNOWN
)

@Serializable
data class ModpackFile(
    val path: String,
    val url: String = "",
    val projectId: String = "",
    val fileId: String = "",
    val sha1: String = "",
    val size: Long = 0
)

object ModpackInstaller {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    private val _progress = MutableStateFlow("")
    val progress = _progress.asStateFlow()
    private val _isInstalling = MutableStateFlow(false)
    val isInstalling = _isInstalling.asStateFlow()
    private val _progressPercent = MutableStateFlow(0f)
    val progressPercent = _progressPercent.asStateFlow()
    
    fun detectFormat(zipFile: File): ModpackFormat {
        return try {
            ZipFile(zipFile).use { zip ->
                when {
                    zip.getEntry("manifest.json") != null -> ModpackFormat.CURSEFORGE
                    zip.getEntry("modrinth.index.json") != null -> ModpackFormat.MODRINTH
                    zip.getEntry("mmc-pack.json") != null || zip.getEntry("instance.cfg") != null -> ModpackFormat.MULTIMC
                    else -> ModpackFormat.UNKNOWN
                }
            }
        } catch (e: Exception) { ModpackFormat.UNKNOWN }
    }
    
    suspend fun install(zipFile: File, instanceName: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            _isInstalling.value = true
            val format = detectFormat(zipFile)
            _progress.value = "Detected format: $format"
            
            val result = when (format) {
                ModpackFormat.CURSEFORGE -> installCurseForge(zipFile, instanceName)
                ModpackFormat.MODRINTH -> installModrinth(zipFile, instanceName)
                ModpackFormat.MULTIMC -> installMultiMC(zipFile, instanceName)
                else -> Result.failure(Exception("Unknown modpack format"))
            }
            result
        } catch (e: Exception) { Logger.error("Modpack install failed", e); Result.failure(e) }
        finally { _isInstalling.value = false; _progressPercent.value = 0f }
    }
    
    private suspend fun installCurseForge(zipFile: File, instanceName: String?): Result<String> {
        ZipFile(zipFile).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json") ?: return Result.failure(Exception("No manifest.json"))
            val content = zip.getInputStream(manifestEntry).bufferedReader().readText()
            val manifest = json.parseToJsonElement(content).jsonObject
            
            val name = instanceName ?: manifest["name"]?.jsonPrimitive?.content ?: "modpack"
            val mcVer = manifest["minecraft"]?.jsonObject?.get("version")?.jsonPrimitive?.content ?: ""
            val modLoaders = manifest["minecraft"]?.jsonObject?.get("modLoaders")?.jsonArray
            val loader = modLoaders?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content ?: ""
            
            val instanceDir = File(PathManager.DIR_GAME, "instances/$name")
            instanceDir.mkdirs()
            
            // Extract overrides
            _progress.value = "Extracting overrides..."
            val overridesDir = manifest["overrides"]?.jsonPrimitive?.content ?: "overrides"
            zip.entries().asSequence().filter { it.name.startsWith("$overridesDir/") && !it.isDirectory }.forEach { entry ->
                val relativePath = entry.name.removePrefix("$overridesDir/")
                val outFile = File(instanceDir, relativePath)
                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { it.copyTo(outFile.outputStream()) }
            }
            
            // Download mods from CurseForge
            val files = manifest["files"]?.jsonArray ?: JsonArray(emptyList())
            val modsDir = File(instanceDir, "mods")
            modsDir.mkdirs()
            
            files.forEachIndexed { index, fileEntry ->
                val projectId = fileEntry.jsonObject["projectID"]?.jsonPrimitive?.content ?: return@forEachIndexed
                val fileId = fileEntry.jsonObject["fileID"]?.jsonPrimitive?.content ?: return@forEachIndexed
                _progress.value = "Downloading mod ${index + 1}/${files.size}..."
                _progressPercent.value = index.toFloat() / files.size
                
                // Note: CurseForge requires API key for direct download
                // Using fallback URL pattern
                try {
                    val url = "https://www.curseforge.com/api/v1/mods/$projectId/files/$fileId/download"
                    val tempFile = File(modsDir, "mod_${projectId}_${fileId}.jar")
                    LauncherHttpClient.downloadFile(url, tempFile)
                } catch (e: Exception) {
                    Logger.warn("Failed to download CF mod $projectId/$fileId: ${e.message}")
                }
            }
            
            // Save instance info
            val info = buildJsonObject {
                put("name", name)
                put("mcVersion", mcVer)
                put("modLoader", loader)
                put("format", "curseforge")
            }
            File(instanceDir, "instance.json").writeText(info.toString())
            
            _progress.value = "Modpack '$name' installed!"
            return Result.success(name)
        }
    }
    
    private suspend fun installModrinth(zipFile: File, instanceName: String?): Result<String> {
        ZipFile(zipFile).use { zip ->
            val indexEntry = zip.getEntry("modrinth.index.json") ?: return Result.failure(Exception("No modrinth.index.json"))
            val content = zip.getInputStream(indexEntry).bufferedReader().readText()
            val index = json.parseToJsonElement(content).jsonObject
            
            val name = instanceName ?: index["name"]?.jsonPrimitive?.content ?: "modpack"
            val deps = index["dependencies"]?.jsonObject
            val mcVer = deps?.get("minecraft")?.jsonPrimitive?.content ?: ""
            val fabricVer = deps?.get("fabric-loader")?.jsonPrimitive?.content
            val forgeVer = deps?.get("forge")?.jsonPrimitive?.content
            val quiltVer = deps?.get("quilt-loader")?.jsonPrimitive?.content
            
            val instanceDir = File(PathManager.DIR_GAME, "instances/$name")
            instanceDir.mkdirs()
            
            // Extract overrides
            _progress.value = "Extracting overrides..."
            zip.entries().asSequence()
                .filter { (it.name.startsWith("overrides/") || it.name.startsWith("client-overrides/")) && !it.isDirectory }
                .forEach { entry ->
                    val relativePath = entry.name.removePrefix("overrides/").removePrefix("client-overrides/")
                    val outFile = File(instanceDir, relativePath)
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { it.copyTo(outFile.outputStream()) }
                }
            
            // Download files
            val files = index["files"]?.jsonArray ?: JsonArray(emptyList())
            files.forEachIndexed { idx, fileEntry ->
                val obj = fileEntry.jsonObject
                val path = obj["path"]?.jsonPrimitive?.content ?: return@forEachIndexed
                val downloads = obj["downloads"]?.jsonArray ?: return@forEachIndexed
                val url = downloads.firstOrNull()?.jsonPrimitive?.content ?: return@forEachIndexed
                
                _progress.value = "Downloading ${idx + 1}/${files.size}: ${File(path).name}"
                _progressPercent.value = idx.toFloat() / files.size
                
                val dest = File(instanceDir, path)
                dest.parentFile?.mkdirs()
                val sha1 = obj["hashes"]?.jsonObject?.get("sha1")?.jsonPrimitive?.content
                LauncherHttpClient.downloadFileWithHash(url, dest, sha1)
            }
            
            val info = buildJsonObject {
                put("name", name)
                put("mcVersion", mcVer)
                fabricVer?.let { put("fabricLoader", it) }
                forgeVer?.let { put("forge", it) }
                quiltVer?.let { put("quiltLoader", it) }
                put("format", "modrinth")
            }
            File(instanceDir, "instance.json").writeText(info.toString())
            
            _progress.value = "Modpack '$name' installed!"
            return Result.success(name)
        }
    }
    
    private suspend fun installMultiMC(zipFile: File, instanceName: String?): Result<String> {
        ZipFile(zipFile).use { zip ->
            val packEntry = zip.getEntry("mmc-pack.json")
            val name = instanceName ?: "multimc-pack"
            val instanceDir = File(PathManager.DIR_GAME, "instances/$name")
            instanceDir.mkdirs()
            
            // Extract all content
            _progress.value = "Extracting MultiMC pack..."
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                val outFile = File(instanceDir, entry.name)
                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { it.copyTo(outFile.outputStream()) }
            }
            
            // Parse mmc-pack.json if exists
            var mcVer = ""
            if (packEntry != null) {
                val content = zip.getInputStream(packEntry).bufferedReader().readText()
                val pack = json.parseToJsonElement(content).jsonObject
                val components = pack["components"]?.jsonArray
                components?.forEach { comp ->
                    val uid = comp.jsonObject["uid"]?.jsonPrimitive?.content
                    val ver = comp.jsonObject["version"]?.jsonPrimitive?.content ?: ""
                    if (uid == "net.minecraft") mcVer = ver
                }
            }
            
            val info = buildJsonObject {
                put("name", name)
                put("mcVersion", mcVer)
                put("format", "multimc")
            }
            File(instanceDir, "instance.json").writeText(info.toString())
            
            _progress.value = "MultiMC pack '$name' installed!"
            return Result.success(name)
        }
    }
}
