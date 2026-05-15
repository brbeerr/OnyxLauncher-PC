package com.onyx.launcher.game.auth

import com.onyx.launcher.data.Account
import com.onyx.launcher.data.AccountType
import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

@Serializable
data class AuthServer(
    val name: String,
    val baseUrl: String,  // e.g. "https://skin.example.com/api/yggdrasil"
    val signaturePublicKey: String? = null
) {
    val authlibInjectorUrl: String get() = baseUrl.trimEnd('/')
}

@Serializable
data class AuthServerProfile(
    val id: String,
    val name: String,
    val properties: List<AuthProperty> = emptyList()
)

@Serializable
data class AuthProperty(val name: String, val value: String)

object AuthServerManager {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val serversFile: File get() = File(PathManager.DIR_LAUNCHER, "auth_servers.json")
    
    private var _servers = mutableListOf<AuthServer>()
    val servers: List<AuthServer> get() = _servers
    
    fun loadServers() {
        try {
            if (serversFile.exists()) {
                _servers = json.decodeFromString<MutableList<AuthServer>>(serversFile.readText())
            }
        } catch (e: Exception) { Logger.error("Failed to load auth servers", e) }
    }
    
    private fun saveServers() {
        try {
            serversFile.parentFile?.mkdirs()
            serversFile.writeText(json.encodeToString(_servers.toList()))
        } catch (e: Exception) { Logger.error("Failed to save auth servers", e) }
    }
    
    fun addServer(server: AuthServer) {
        _servers.removeAll { it.baseUrl == server.baseUrl }
        _servers.add(server)
        saveServers()
    }
    
    fun removeServer(server: AuthServer) {
        _servers.removeAll { it.baseUrl == server.baseUrl }
        saveServers()
    }
    
    suspend fun authenticate(server: AuthServer, username: String, password: String): Result<Account> = withContext(Dispatchers.IO) {
        try {
            val clientToken = UUID.randomUUID().toString()
            val body = buildJsonObject {
                putJsonObject("agent") {
                    put("name", "Minecraft")
                    put("version", 1)
                }
                put("username", username)
                put("password", password)
                put("clientToken", clientToken)
                put("requestUser", true)
            }
            
            val response = LauncherHttpClient.client.post("${server.baseUrl}/authserver/authenticate") {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.bodyAsText()
            
            val result = json.parseToJsonElement(response).jsonObject
            
            if (result.containsKey("error")) {
                return@withContext Result.failure(Exception(
                    result["errorMessage"]?.jsonPrimitive?.content ?: "Authentication failed"
                ))
            }
            
            val accessToken = result["accessToken"]?.jsonPrimitive?.content ?: ""
            val profile = result["selectedProfile"]?.jsonObject
            val profileId = profile?.get("id")?.jsonPrimitive?.content ?: ""
            val profileName = profile?.get("name")?.jsonPrimitive?.content ?: username
            
            val account = Account(
                username = profileName,
                accessToken = accessToken,
                clientToken = clientToken,
                profileId = profileId,
                accountType = AccountType.AUTH_SERVER.tag,
                otherBaseUrl = server.baseUrl,
                expiresAt = System.currentTimeMillis() + 86400_000 // 24h
            )
            
            Result.success(account)
        } catch (e: Exception) {
            Logger.error("Auth server login failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun refresh(account: Account): Result<Account> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = account.otherBaseUrl ?: return@withContext Result.failure(Exception("No auth server URL"))
            val body = buildJsonObject {
                put("accessToken", account.accessToken)
                put("clientToken", account.clientToken)
                put("requestUser", true)
            }
            
            val response = LauncherHttpClient.client.post("$baseUrl/authserver/refresh") {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.bodyAsText()
            
            val result = json.parseToJsonElement(response).jsonObject
            if (result.containsKey("error")) {
                return@withContext Result.failure(Exception(result["errorMessage"]?.jsonPrimitive?.content ?: "Refresh failed"))
            }
            
            val newToken = result["accessToken"]?.jsonPrimitive?.content ?: account.accessToken
            Result.success(account.copy(
                accessToken = newToken,
                expiresAt = System.currentTimeMillis() + 86400_000
            ))
        } catch (e: Exception) { Result.failure(e) }
    }
    
    fun getAuthlibInjectorArgs(account: Account): List<String> {
        val baseUrl = account.otherBaseUrl ?: return emptyList()
        val authlibJar = File(PathManager.DIR_LIBRARIES, "authlib-injector.jar")
        if (!authlibJar.exists()) return emptyList()
        return listOf(
            "-javaagent:${authlibJar.absolutePath}=$baseUrl",
            "-Dauthlibinjector.side=client"
        )
    }
    
    suspend fun downloadAuthlibInjector(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dest = File(PathManager.DIR_LIBRARIES, "authlib-injector.jar")
            if (dest.exists()) return@withContext Result.success(dest)
            
            // Get latest version URL
            val metaResp = LauncherHttpClient.getString("https://authlib-injector.yushi.moe/artifact/latest.json")
            val meta = json.parseToJsonElement(metaResp).jsonObject
            val downloadUrl = meta["download_url"]?.jsonPrimitive?.content
                ?: return@withContext Result.failure(Exception("No download URL"))
            
            dest.parentFile?.mkdirs()
            LauncherHttpClient.downloadFile(downloadUrl, dest)
            Logger.info("Downloaded authlib-injector")
            Result.success(dest)
        } catch (e: Exception) { Result.failure(e) }
    }
}
