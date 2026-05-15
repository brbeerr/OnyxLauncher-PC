package com.onyx.launcher.game.mods

import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest

data class ModUpdate(
    val modName: String,
    val currentFile: File,
    val currentVersion: String,
    val latestVersion: String,
    val latestVersionId: String,
    val downloadUrl: String,
    val fileName: String,
    val changelog: String = ""
)

object ModUpdateChecker {
    private const val MODRINTH_API = "https://api.modrinth.com/v2"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    suspend fun checkUpdates(
        modsDir: File = PathManager.DIR_MODS,
        gameVersion: String,
        loader: String? = null
    ): List<ModUpdate> = withContext(Dispatchers.IO) {
        val updates = mutableListOf<ModUpdate>()
        val modFiles = modsDir.listFiles()?.filter { it.extension == "jar" } ?: return@withContext emptyList()
        
        // Batch lookup using SHA-1 hashes via Modrinth's version_file API
        val hashMap = mutableMapOf<String, File>()
        modFiles.forEach { file ->
            val hash = file.sha1()
            hashMap[hash] = file
        }
        
        // Use Modrinth batch update check
        try {
            val hashes = hashMap.keys.toList()
            if (hashes.isEmpty()) return@withContext emptyList()
            
            val requestBody = buildJsonObject {
                putJsonArray("hashes") { hashes.forEach { add(it) } }
                put("algorithm", "sha1")
                putJsonArray("loaders") { loader?.let { add(it.lowercase()) } ?: run { add("fabric"); add("forge") } }
                putJsonArray("game_versions") { add(gameVersion) }
            }
            
            val response = LauncherHttpClient.client.post("$MODRINTH_API/version_files/update") {
                contentType(ContentType.Application.Json)
                header("User-Agent", "OnyxLauncher/1.0")
                setBody(requestBody.toString())
            }.bodyAsText()
            
            val results = json.parseToJsonElement(response).jsonObject
            
            for ((hash, versionElement) in results) {
                val file = hashMap[hash] ?: continue
                val version = versionElement.jsonObject
                val newHash = version["files"]?.jsonArray
                    ?.firstOrNull { it.jsonObject["primary"]?.jsonPrimitive?.booleanOrNull == true }
                    ?.jsonObject?.get("hashes")?.jsonObject?.get("sha1")?.jsonPrimitive?.content
                
                // If the returned version has a different hash, it's an update
                if (newHash != null && newHash != hash) {
                    val metadata = ModMetadataReader.readMetadata(file)
                    val primaryFile = version["files"]?.jsonArray
                        ?.firstOrNull { it.jsonObject["primary"]?.jsonPrimitive?.booleanOrNull == true }
                        ?.jsonObject
                    
                    updates.add(ModUpdate(
                        modName = metadata?.name ?: file.nameWithoutExtension,
                        currentFile = file,
                        currentVersion = metadata?.version ?: "?",
                        latestVersion = version["version_number"]?.jsonPrimitive?.content ?: "?",
                        latestVersionId = version["id"]?.jsonPrimitive?.content ?: "",
                        downloadUrl = primaryFile?.get("url")?.jsonPrimitive?.content ?: "",
                        fileName = primaryFile?.get("filename")?.jsonPrimitive?.content ?: "",
                        changelog = version["changelog"]?.jsonPrimitive?.content ?: ""
                    ))
                }
            }
        } catch (e: Exception) {
            Logger.error("Failed to check mod updates", e)
        }
        
        updates
    }
    
    suspend fun updateMod(update: ModUpdate, modsDir: File = PathManager.DIR_MODS): Result<File> = withContext(Dispatchers.IO) {
        try {
            val newFile = File(modsDir, update.fileName)
            if (LauncherHttpClient.downloadFile(update.downloadUrl, newFile)) {
                // Disable old file
                update.currentFile.renameTo(File(update.currentFile.parentFile, "${update.currentFile.name}.old"))
                Logger.info("Updated mod: ${update.modName} -> ${update.latestVersion}")
                Result.success(newFile)
            } else {
                Result.failure(Exception("Download failed"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }
    
    private fun File.sha1(): String {
        val digest = MessageDigest.getInstance("SHA-1")
        inputStream().use { inp ->
            val buf = ByteArray(8192)
            var r: Int
            while (inp.read(buf).also { r = it } != -1) digest.update(buf, 0, r)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
