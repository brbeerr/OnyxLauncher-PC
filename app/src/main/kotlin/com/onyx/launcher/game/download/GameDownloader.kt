package com.onyx.launcher.game.download

import com.onyx.launcher.data.*
import com.onyx.launcher.game.VersionManager
import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class DownloadProgress(val message: String = "", val progress: Float = 0f)

class GameDownloader {
    private val json = Json { ignoreUnknownKeys = true }
    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress = _downloadProgress.asStateFlow()
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()
    
    suspend fun downloadVersion(versionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isDownloading.value = true
            updateProgress("Downloading version info...", 0f)
            val vj = VersionManager.getVersionJson(versionId).getOrThrow()
            
            updateProgress("Downloading client JAR...", 0.1f)
            vj.downloads?.client?.let { LauncherHttpClient.downloadFileWithHash(it.url, PathManager.getVersionJar(versionId), it.sha1) }
            
            updateProgress("Downloading libraries...", 0.2f)
            downloadLibraries(vj)
            
            updateProgress("Downloading assets...", 0.6f)
            downloadAssets(vj)
            
            updateProgress("Complete!", 1f)
            VersionManager.loadInstalledVersions()
            Result.success(Unit)
        } catch (e: Exception) { Logger.error("Download failed", e); Result.failure(e) }
        finally { _isDownloading.value = false }
    }
    
    private suspend fun downloadLibraries(vj: VersionJson) = coroutineScope {
        val libs = vj.libraries.filter { isAllowed(it) }
        var done = 0
        libs.map { lib -> async {
            lib.downloads?.artifact?.let { a ->
                val f = File(PathManager.DIR_LIBRARIES, a.path)
                if (!f.exists()) LauncherHttpClient.downloadFileWithHash(a.url, f, a.sha1)
            }
            synchronized(this@GameDownloader) { done++; updateProgress("Libraries ($done/${libs.size})", 0.2f + done.toFloat() / libs.size * 0.4f) }
        }}.awaitAll()
    }
    
    private suspend fun downloadAssets(vj: VersionJson) = coroutineScope {
        val ai = vj.assetIndex ?: return@coroutineScope
        val indexFile = File(PathManager.DIR_ASSETS, "indexes/${ai.id}.json")
        if (!indexFile.exists()) LauncherHttpClient.downloadFileWithHash(ai.url, indexFile, ai.sha1)
        val objs = json.parseToJsonElement(indexFile.readText()).jsonObject["objects"]?.jsonObject ?: return@coroutineScope
        val assets = objs.entries.toList()
        var done = 0
        assets.chunked(50).forEach { chunk ->
            chunk.map { (_, info) -> async {
                val hash = info.jsonObject["hash"]!!.jsonPrimitive.content
                val f = File(PathManager.DIR_ASSETS, "objects/${hash.take(2)}/$hash")
                if (!f.exists()) LauncherHttpClient.downloadFileWithHash("https://resources.download.minecraft.net/${hash.take(2)}/$hash", f, hash)
                synchronized(this@GameDownloader) { done++; if (done % 100 == 0) updateProgress("Assets ($done/${assets.size})", 0.6f + done.toFloat() / assets.size * 0.4f) }
            }}.awaitAll()
        }
    }
    
    private fun isAllowed(lib: Library): Boolean {
        val rules = lib.rules ?: return true
        var allowed = false
        for (r in rules) { if (r.os == null || r.os.name == getCurrentOS()) allowed = r.action == "allow" }
        return allowed
    }
    
    private fun getCurrentOS() = System.getProperty("os.name").lowercase().let { when { it.contains("win") -> "windows"; it.contains("mac") -> "osx"; else -> "linux" } }
    private fun updateProgress(msg: String, prog: Float) { _downloadProgress.value = DownloadProgress(msg, prog) }
}
