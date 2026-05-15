package com.onyx.launcher.game.skin

import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.util.Base64

@Serializable
data class SkinProfile(
    val uuid: String,
    val name: String,
    val skinUrl: String? = null,
    val capeUrl: String? = null,
    val model: SkinModel = SkinModel.CLASSIC
)

enum class SkinModel { CLASSIC, SLIM }

object SkinManager {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val skinsDir: File get() = PathManager.DIR_ACCOUNT_SKIN
    
    suspend fun fetchSkinProfile(uuid: String): Result<SkinProfile> = withContext(Dispatchers.IO) {
        try {
            val profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/$uuid"
            val response = LauncherHttpClient.getString(profileUrl)
            val profile = json.parseToJsonElement(response).jsonObject
            
            val name = profile["name"]?.jsonPrimitive?.content ?: ""
            val properties = profile["properties"]?.jsonArray
            val texturesProp = properties?.firstOrNull {
                it.jsonObject["name"]?.jsonPrimitive?.content == "textures"
            }
            
            var skinUrl: String? = null
            var capeUrl: String? = null
            var model = SkinModel.CLASSIC
            
            texturesProp?.jsonObject?.get("value")?.jsonPrimitive?.content?.let { encoded ->
                val decoded = String(Base64.getDecoder().decode(encoded))
                val textures = json.parseToJsonElement(decoded).jsonObject["textures"]?.jsonObject
                
                textures?.get("SKIN")?.jsonObject?.let { skin ->
                    skinUrl = skin["url"]?.jsonPrimitive?.content
                    if (skin["metadata"]?.jsonObject?.get("model")?.jsonPrimitive?.content == "slim") {
                        model = SkinModel.SLIM
                    }
                }
                textures?.get("CAPE")?.jsonObject?.let { cape ->
                    capeUrl = cape["url"]?.jsonPrimitive?.content
                }
            }
            
            Result.success(SkinProfile(uuid, name, skinUrl, capeUrl, model))
        } catch (e: Exception) {
            Logger.error("Failed to fetch skin profile for $uuid", e)
            Result.failure(e)
        }
    }
    
    suspend fun downloadSkin(profile: SkinProfile): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = profile.skinUrl ?: return@withContext Result.failure(Exception("No skin URL"))
            val file = File(skinsDir, "${profile.uuid}_skin.png")
            file.parentFile?.mkdirs()
            LauncherHttpClient.downloadFile(url, file)
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }
    
    suspend fun downloadCape(profile: SkinProfile): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = profile.capeUrl ?: return@withContext Result.failure(Exception("No cape URL"))
            val file = File(skinsDir, "${profile.uuid}_cape.png")
            file.parentFile?.mkdirs()
            LauncherHttpClient.downloadFile(url, file)
            Result.success(file)
        } catch (e: Exception) { Result.failure(e) }
    }
    
    fun getLocalSkins(): List<File> {
        val localDir = File(skinsDir, "local")
        if (!localDir.exists()) return emptyList()
        return localDir.listFiles()?.filter { it.extension == "png" }?.toList() ?: emptyList()
    }
    
    fun setLocalSkin(skinFile: File, accountUuid: String): Boolean {
        return try {
            val dest = File(skinsDir, "${accountUuid}_skin.png")
            dest.parentFile?.mkdirs()
            skinFile.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) { false }
    }
    
    fun getCachedSkin(uuid: String): File? {
        val file = File(skinsDir, "${uuid}_skin.png")
        return if (file.exists()) file else null
    }
    
    fun getCachedCape(uuid: String): File? {
        val file = File(skinsDir, "${uuid}_cape.png")
        return if (file.exists()) file else null
    }
}
