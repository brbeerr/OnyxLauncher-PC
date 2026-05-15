package com.onyx.launcher.game.mods

import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.PathManager
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

enum class Platform(val displayName: String) { CURSEFORGE("CurseForge"), MODRINTH("Modrinth") }
enum class ContentType { MOD, MODPACK, RESOURCE_PACK, SHADER, WORLD }
enum class ReleaseType { RELEASE, BETA, ALPHA }
enum class SortField { RELEVANCE, POPULARITY, DOWNLOADS, UPDATED, NEWEST }

@Serializable data class ModProject(val id: String, val platform: Platform, val slug: String, val title: String, val description: String? = null, val author: String? = null, val iconUrl: String? = null, val downloadCount: Long = 0, val gameVersions: List<String> = emptyList(), val loaders: List<String> = emptyList())
@Serializable data class ModVersion(val id: String, val projectId: String, val platform: Platform, val versionNumber: String, val gameVersions: List<String> = emptyList(), val loaders: List<String> = emptyList(), val releaseType: ReleaseType = ReleaseType.RELEASE, val downloadUrl: String, val fileName: String, val sha1: String? = null)
data class SearchResult(val projects: List<ModProject>, val totalHits: Int)
data class SearchParams(val query: String = "", val platform: Platform = Platform.MODRINTH, val contentType: ContentType = ContentType.MOD, val gameVersion: String? = null, val loader: String? = null, val limit: Int = 20)
data class InstalledMod(val file: File, val name: String, val enabled: Boolean)

@Serializable data class ModrinthSearchResponse(val hits: List<ModrinthHit>, val total_hits: Int)
@Serializable data class ModrinthHit(val project_id: String, val slug: String, val title: String, val description: String? = null, val author: String? = null, val icon_url: String? = null, val downloads: Long = 0, val versions: List<String> = emptyList(), val loaders: List<String> = emptyList())
@Serializable data class ModrinthVersion(val id: String, val project_id: String, val version_number: String, val game_versions: List<String> = emptyList(), val loaders: List<String> = emptyList(), val version_type: String = "release", val files: List<ModrinthFile> = emptyList())
@Serializable data class ModrinthFile(val url: String, val filename: String, val hashes: Map<String, String> = emptyMap(), val primary: Boolean = false)

object ModsRepository {
    private const val MODRINTH_API = "https://api.modrinth.com/v2"
    private val json = LauncherHttpClient.json
    
    suspend fun search(params: SearchParams): Result<SearchResult> = withContext(Dispatchers.IO) {
        try {
            if (params.platform == Platform.MODRINTH) searchModrinth(params)
            else Result.failure(Exception("CurseForge requires API key"))
        } catch (e: Exception) { Result.failure(e) }
    }
    
    private suspend fun searchModrinth(params: SearchParams): Result<SearchResult> {
        val pt = when (params.contentType) { ContentType.MOD -> "mod"; ContentType.MODPACK -> "modpack"; ContentType.RESOURCE_PACK -> "resourcepack"; ContentType.SHADER -> "shader"; else -> "mod" }
        val facets = mutableListOf("[\"project_type:$pt\"]")
        params.gameVersion?.let { facets.add("[\"versions:$it\"]") }
        params.loader?.let { facets.add("[\"categories:${it.lowercase()}\"]") }
        val url = "$MODRINTH_API/search?query=${params.query.encodeURLParameter()}&limit=${params.limit}&facets=[${facets.joinToString(",")}]"
        val resp = LauncherHttpClient.client.get(url) { header("User-Agent", "OnyxLauncher/1.0") }.bodyAsText()
        val sr = json.decodeFromString<ModrinthSearchResponse>(resp)
        return Result.success(SearchResult(sr.hits.map { ModProject(it.project_id, Platform.MODRINTH, it.slug, it.title, it.description, it.author, it.icon_url, it.downloads, it.versions, it.loaders) }, sr.total_hits))
    }
    
    suspend fun getVersions(projectId: String, platform: Platform): Result<List<ModVersion>> = withContext(Dispatchers.IO) {
        try {
            if (platform == Platform.MODRINTH) {
                val resp = LauncherHttpClient.client.get("$MODRINTH_API/project/$projectId/version") { header("User-Agent", "OnyxLauncher/1.0") }.bodyAsText()
                val vs = json.decodeFromString<List<ModrinthVersion>>(resp)
                Result.success(vs.mapNotNull { v -> v.files.firstOrNull { it.primary }?.let { f -> ModVersion(v.id, v.project_id, Platform.MODRINTH, v.version_number, v.game_versions, v.loaders, when (v.version_type) { "beta" -> ReleaseType.BETA; "alpha" -> ReleaseType.ALPHA; else -> ReleaseType.RELEASE }, f.url, f.filename, f.hashes["sha1"]) } })
            } else Result.failure(Exception("CurseForge requires API key"))
        } catch (e: Exception) { Result.failure(e) }
    }
    
    suspend fun downloadMod(version: ModVersion, dest: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(dest, version.fileName)
            if (LauncherHttpClient.downloadFileWithHash(version.downloadUrl, file, version.sha1)) Result.success(file)
            else Result.failure(Exception("Download failed"))
        } catch (e: Exception) { Result.failure(e) }
    }
    
    fun getInstalledMods(dir: File = PathManager.DIR_MODS): List<InstalledMod> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.name.endsWith(".jar") || it.name.endsWith(".jar.disabled") }?.map { InstalledMod(it, it.name.removeSuffix(".disabled").removeSuffix(".jar"), !it.name.endsWith(".disabled")) }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    
    fun toggleMod(mod: InstalledMod) = mod.file.renameTo(File(mod.file.parentFile, if (mod.enabled) "${mod.file.name}.disabled" else mod.file.name.removeSuffix(".disabled")))
    fun deleteMod(mod: InstalledMod) = mod.file.delete()
}
