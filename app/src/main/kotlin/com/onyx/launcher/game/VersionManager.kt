package com.onyx.launcher.game

import com.onyx.launcher.data.*
import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

data class InstalledVersion(val id: String, val type: String, val directory: File, val hasJar: Boolean, val mainClass: String, val inheritsFrom: String? = null)

object VersionManager {
    private const val VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _availableVersions = MutableStateFlow<List<VersionInfo>>(emptyList())
    val availableVersions = _availableVersions.asStateFlow()
    
    private val _installedVersions = MutableStateFlow<List<InstalledVersion>>(emptyList())
    val installedVersions = _installedVersions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private var manifest: VersionManifest? = null
    
    suspend fun loadVersionManifest(): Result<VersionManifest> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            manifest = LauncherHttpClient.get<VersionManifest>(VERSION_MANIFEST_URL)
            _availableVersions.value = manifest!!.versions
            Logger.info("Loaded ${manifest!!.versions.size} versions")
            Result.success(manifest!!)
        } catch (e: Exception) { Logger.error("Failed to load version manifest", e); Result.failure(e) }
        finally { _isLoading.value = false }
    }
    
    suspend fun loadInstalledVersions() = withContext(Dispatchers.IO) {
        try {
            val dir = PathManager.DIR_VERSIONS
            if (!dir.exists()) { _installedVersions.value = emptyList(); return@withContext }
            _installedVersions.value = dir.listFiles()?.filter { it.isDirectory }?.mapNotNull { d ->
                val jf = File(d, "${d.name}.json")
                if (jf.exists()) try {
                    val vj = json.decodeFromString<VersionJson>(jf.readText())
                    InstalledVersion(d.name, vj.type ?: "release", d, File(d, "${d.name}.jar").exists(), vj.mainClass, vj.inheritsFrom)
                } catch (e: Exception) { null } else null
            }?.sortedByDescending { it.id } ?: emptyList()
        } catch (e: Exception) { Logger.error("Failed to load installed versions", e) }
    }
    
    suspend fun getVersionInfo(versionId: String): VersionInfo? {
        if (manifest == null) loadVersionManifest()
        return manifest?.versions?.find { it.id == versionId }
    }
    
    suspend fun getVersionJson(versionId: String): Result<VersionJson> = withContext(Dispatchers.IO) {
        try {
            val localFile = PathManager.getVersionJson(versionId)
            if (localFile.exists()) return@withContext Result.success(json.decodeFromString<VersionJson>(localFile.readText()))
            val vi = getVersionInfo(versionId) ?: return@withContext Result.failure(Exception("Version not found"))
            val vj = LauncherHttpClient.get<VersionJson>(vi.url)
            localFile.parentFile?.mkdirs(); localFile.writeText(json.encodeToString(VersionJson.serializer(), vj))
            Result.success(vj)
        } catch (e: Exception) { Logger.error("Failed to get version JSON", e); Result.failure(e) }
    }
}
