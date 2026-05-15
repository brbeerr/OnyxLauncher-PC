package com.onyx.launcher.game.addons.modloader

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

@Serializable
data class FabricLoaderEntry(val version: String, val stable: Boolean = true)
@Serializable
data class ForgeVersion(val version: String, val mcVersion: String)

object ModLoaderInstaller {
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _installProgress = MutableStateFlow("")
    val installProgress = _installProgress.asStateFlow()
    private val _isInstalling = MutableStateFlow(false)
    val isInstalling = _isInstalling.asStateFlow()
    
    // --- Fabric ---
    suspend fun getFabricLoaderVersions(): Result<List<FabricLoaderEntry>> = withContext(Dispatchers.IO) {
        try {
            val resp = LauncherHttpClient.getString("https://meta.fabricmc.net/v2/versions/loader")
            val arr = json.parseToJsonElement(resp).jsonArray
            Result.success(arr.map { FabricLoaderEntry(
                it.jsonObject["version"]!!.jsonPrimitive.content,
                it.jsonObject["stable"]?.jsonPrimitive?.booleanOrNull ?: true
            )})
        } catch (e: Exception) { Result.failure(e) }
    }
    
    suspend fun installFabric(gameVersion: String, loaderVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isInstalling.value = true
            _installProgress.value = "Downloading Fabric profile..."
            val versionId = "fabric-loader-$loaderVersion-$gameVersion"
            val profileUrl = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion/$loaderVersion/profile/json"
            val profileJson = LauncherHttpClient.getString(profileUrl)
            
            val versionDir = PathManager.getVersionDir(versionId)
            versionDir.mkdirs()
            File(versionDir, "$versionId.json").writeText(profileJson)
            
            _installProgress.value = "Downloading Fabric libraries..."
            downloadLibrariesFromProfile(profileJson)
            
            _installProgress.value = "Fabric $loaderVersion installed!"
            Logger.info("Installed Fabric $loaderVersion for $gameVersion")
            Result.success(Unit)
        } catch (e: Exception) { Logger.error("Fabric install failed", e); Result.failure(e) }
        finally { _isInstalling.value = false }
    }
    
    // --- Quilt ---
    suspend fun getQuiltLoaderVersions(): Result<List<FabricLoaderEntry>> = withContext(Dispatchers.IO) {
        try {
            val resp = LauncherHttpClient.getString("https://meta.quiltmc.org/v3/versions/loader")
            val arr = json.parseToJsonElement(resp).jsonArray
            Result.success(arr.map { FabricLoaderEntry(
                it.jsonObject["version"]!!.jsonPrimitive.content,
                it.jsonObject["stable"]?.jsonPrimitive?.booleanOrNull ?: true
            )})
        } catch (e: Exception) { Result.failure(e) }
    }
    
    suspend fun installQuilt(gameVersion: String, loaderVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isInstalling.value = true
            _installProgress.value = "Downloading Quilt profile..."
            val versionId = "quilt-loader-$loaderVersion-$gameVersion"
            val profileUrl = "https://meta.quiltmc.org/v3/versions/loader/$gameVersion/$loaderVersion/profile/json"
            val profileJson = LauncherHttpClient.getString(profileUrl)
            
            val versionDir = PathManager.getVersionDir(versionId)
            versionDir.mkdirs()
            File(versionDir, "$versionId.json").writeText(profileJson)
            
            _installProgress.value = "Downloading Quilt libraries..."
            downloadLibrariesFromProfile(profileJson)
            
            _installProgress.value = "Quilt $loaderVersion installed!"
            Logger.info("Installed Quilt $loaderVersion for $gameVersion")
            Result.success(Unit)
        } catch (e: Exception) { Logger.error("Quilt install failed", e); Result.failure(e) }
        finally { _isInstalling.value = false }
    }
    
    // --- Forge / NeoForge ---
    suspend fun getForgeVersions(gameVersion: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val resp = LauncherHttpClient.getString("https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml")
            val versions = Regex("""<version>$gameVersion-([^<]+)</version>""").findAll(resp)
                .map { it.groupValues[1] }.toList().reversed()
            Result.success(versions)
        } catch (e: Exception) { Result.failure(e) }
    }
    
    suspend fun getNeoForgeVersions(gameVersion: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val resp = LauncherHttpClient.getString("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge")
            val parsed = json.parseToJsonElement(resp).jsonObject
            val versions = parsed["versions"]?.jsonArray?.map { it.jsonPrimitive.content }
                ?.filter { it.startsWith(gameVersion.removePrefix("1.")) }
                ?.reversed() ?: emptyList()
            Result.success(versions)
        } catch (e: Exception) { Result.failure(e) }
    }
    
    suspend fun installForge(gameVersion: String, forgeVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isInstalling.value = true
            _installProgress.value = "Downloading Forge installer..."
            val installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$gameVersion-$forgeVersion/forge-$gameVersion-$forgeVersion-installer.jar"
            val installerFile = File(PathManager.DIR_CACHE, "forge-installer.jar")
            LauncherHttpClient.downloadFile(installerUrl, installerFile)
            
            _installProgress.value = "Running Forge installer..."
            val proc = ProcessBuilder("java", "-jar", installerFile.absolutePath, "--installClient", PathManager.DIR_GAME.absolutePath)
                .redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().forEachLine { _installProgress.value = it }
            val code = proc.waitFor()
            
            if (code != 0) return@withContext Result.failure(Exception("Forge installer exited with code $code"))
            _installProgress.value = "Forge $forgeVersion installed!"
            Logger.info("Installed Forge $forgeVersion for $gameVersion")
            Result.success(Unit)
        } catch (e: Exception) { Logger.error("Forge install failed", e); Result.failure(e) }
        finally { _isInstalling.value = false }
    }
    
    suspend fun installNeoForge(gameVersion: String, neoforgeVersion: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isInstalling.value = true
            _installProgress.value = "Downloading NeoForge installer..."
            val installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoforgeVersion/neoforge-$neoforgeVersion-installer.jar"
            val installerFile = File(PathManager.DIR_CACHE, "neoforge-installer.jar")
            LauncherHttpClient.downloadFile(installerUrl, installerFile)
            
            _installProgress.value = "Running NeoForge installer..."
            val proc = ProcessBuilder("java", "-jar", installerFile.absolutePath, "--installClient", PathManager.DIR_GAME.absolutePath)
                .redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().forEachLine { _installProgress.value = it }
            val code = proc.waitFor()
            
            if (code != 0) return@withContext Result.failure(Exception("NeoForge installer exited with code $code"))
            _installProgress.value = "NeoForge $neoforgeVersion installed!"
            Logger.info("Installed NeoForge $neoforgeVersion for $gameVersion")
            Result.success(Unit)
        } catch (e: Exception) { Logger.error("NeoForge install failed", e); Result.failure(e) }
        finally { _isInstalling.value = false }
    }
    
    private suspend fun downloadLibrariesFromProfile(profileJson: String) {
        val profile = json.parseToJsonElement(profileJson).jsonObject
        val libraries = profile["libraries"]?.jsonArray ?: return
        for (lib in libraries) {
            val url = lib.jsonObject["url"]?.jsonPrimitive?.content
                ?: lib.jsonObject["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("url")?.jsonPrimitive?.content
                ?: continue
            val path = lib.jsonObject["downloads"]?.jsonObject?.get("artifact")?.jsonObject?.get("path")?.jsonPrimitive?.content
                ?: extractPathFromName(lib.jsonObject["name"]?.jsonPrimitive?.content ?: continue)
            val dest = File(PathManager.DIR_LIBRARIES, path)
            if (!dest.exists()) {
                LauncherHttpClient.downloadFile(url, dest)
            }
        }
    }
    
    private fun extractPathFromName(name: String): String {
        val parts = name.split(":")
        if (parts.size < 3) return name
        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val version = parts[2]
        return "$group/$artifact/$version/$artifact-$version.jar"
    }
}
