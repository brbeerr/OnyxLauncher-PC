package com.onyx.launcher.game.crashlogs

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
import java.text.SimpleDateFormat
import java.util.*

data class CrashReport(
    val file: File,
    val date: Date,
    val title: String,
    val content: String
) {
    val dateFormatted: String get() = SimpleDateFormat("yyyy-MM-dd HH:mm").format(date)
}

@Serializable
data class MclogsResponse(val id: String? = null, val url: String? = null, val error: String? = null)

object CrashLogManager {
    private const val MCLOGS_API = "https://api.mclo.gs/1"
    private val json = Json { ignoreUnknownKeys = true }
    
    fun getCrashReports(gameDir: File = PathManager.DIR_GAME): List<CrashReport> {
        val crashDir = File(gameDir, "crash-reports")
        if (!crashDir.exists()) return emptyList()
        
        return crashDir.listFiles()?.filter { it.extension == "txt" }?.mapNotNull { file ->
            try {
                val content = file.readText()
                val title = content.lines().firstOrNull { it.startsWith("---- Minecraft Crash Report ----") || it.isNotBlank() }
                    ?: file.nameWithoutExtension
                CrashReport(
                    file = file,
                    date = Date(file.lastModified()),
                    title = extractCrashTitle(content) ?: file.nameWithoutExtension,
                    content = content
                )
            } catch (e: Exception) { null }
        }?.sortedByDescending { it.date } ?: emptyList()
    }
    
    fun getLatestLog(gameDir: File = PathManager.DIR_GAME): File? {
        val logsDir = File(gameDir, "logs")
        val latest = File(logsDir, "latest.log")
        return if (latest.exists()) latest else null
    }
    
    suspend fun uploadToMclogs(content: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = LauncherHttpClient.client.post("$MCLOGS_API/log") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("content=${content.take(10_000_000)}") // mclo.gs limit
            }.bodyAsText()
            
            val result = json.decodeFromString<MclogsResponse>(response)
            if (result.url != null) {
                Logger.info("Uploaded crash log to: ${result.url}")
                Result.success(result.url)
            } else {
                Result.failure(Exception(result.error ?: "Upload failed"))
            }
        } catch (e: Exception) {
            Logger.error("Failed to upload to mclo.gs", e)
            Result.failure(e)
        }
    }
    
    suspend fun uploadCrashReport(report: CrashReport): Result<String> = uploadToMclogs(report.content)
    
    suspend fun uploadLatestLog(gameDir: File = PathManager.DIR_GAME): Result<String> {
        val log = getLatestLog(gameDir) ?: return Result.failure(Exception("No latest.log found"))
        return uploadToMclogs(log.readText())
    }
    
    private fun extractCrashTitle(content: String): String? {
        val lines = content.lines()
        val descLine = lines.indexOfFirst { it.startsWith("Description:") }
        if (descLine >= 0) return lines[descLine].removePrefix("Description:").trim()
        return null
    }
    
    fun analyzeCrash(content: String): List<String> {
        val hints = mutableListOf<String>()
        
        if (content.contains("java.lang.OutOfMemoryError")) {
            hints.add("Out of memory - increase RAM allocation in settings")
        }
        if (content.contains("Pixel format not accelerated")) {
            hints.add("Graphics driver issue - update your GPU drivers")
        }
        if (content.contains("org.lwjgl.LWJGLException")) {
            hints.add("LWJGL error - try updating Java or graphics drivers")
        }
        if (content.contains("java.lang.ClassNotFoundException") || content.contains("java.lang.NoClassDefFoundError")) {
            hints.add("Missing class - a mod might be incompatible or missing a dependency")
        }
        if (content.contains("Mixin apply failed") || content.contains("MixinApplyError")) {
            hints.add("Mixin conflict - two mods are incompatible with each other")
        }
        if (content.contains("java.lang.UnsupportedClassVersionError")) {
            hints.add("Wrong Java version - this mod/version requires a newer Java")
        }
        if (content.contains("Mod ID") && content.contains("requires")) {
            hints.add("Missing mod dependency - check the required mods list")
        }
        
        return hints
    }
}
