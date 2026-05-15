package com.onyx.launcher.game.auth

import com.onyx.launcher.data.Account
import com.onyx.launcher.data.AccountType
import com.onyx.launcher.data.SkinModelType
import com.onyx.launcher.network.LauncherHttpClient
import com.onyx.launcher.utils.Logger
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable data class DeviceCodeResponse(@SerialName("device_code") val deviceCode: String, @SerialName("user_code") val userCode: String, @SerialName("verification_uri") val verificationUri: String, @SerialName("expires_in") val expiresIn: Int, val interval: Int)
@Serializable data class MinecraftAuthResponse(@SerialName("access_token") val accessToken: String, @SerialName("expires_in") val expiresIn: Int)
@Serializable data class MinecraftProfile(val id: String, val name: String)
class NotPurchasedMinecraftException : Exception("Account does not own Minecraft")

object MicrosoftAuth {
    private const val MS_AUTH_URL = "https://login.microsoftonline.com"
    private const val XBL_URL = "https://user.auth.xboxlive.com"
    private const val XSTS_URL = "https://xsts.auth.xboxlive.com"
    private const val MC_URL = "https://api.minecraftservices.com"
    var oauthClientId = "00000000402b5328"
    private val json = LauncherHttpClient.json
    
    enum class AuthStatus { GETTING_DEVICE_CODE, WAITING_FOR_USER, GETTING_ACCESS_TOKEN, GETTING_XBL_TOKEN, GETTING_XSTS_TOKEN, AUTHENTICATE_MINECRAFT, VERIFY_GAME_OWNERSHIP, GETTING_PLAYER_PROFILE, COMPLETE }
    
    suspend fun getDeviceCode(): DeviceCodeResponse {
        val resp = LauncherHttpClient.client.submitForm(
            url = "$MS_AUTH_URL/consumers/oauth2/v2.0/devicecode",
            formParameters = Parameters.build { 
                append("client_id", oauthClientId)
                append("scope", "XboxLive.signin offline_access") 
            }
        ).bodyAsText()
        return json.decodeFromString<DeviceCodeResponse>(resp)
    }
    
    suspend fun authenticateWithDeviceCode(onStatusUpdate: (AuthStatus) -> Unit, onDeviceCode: (DeviceCodeResponse) -> Unit): Result<Account> {
        try {
            onStatusUpdate(AuthStatus.GETTING_DEVICE_CODE)
            val deviceCode = getDeviceCode()
            onDeviceCode(deviceCode)
            
            onStatusUpdate(AuthStatus.WAITING_FOR_USER)
            val tokenResp = pollForToken(deviceCode)
            
            onStatusUpdate(AuthStatus.GETTING_XBL_TOKEN)
            val xbl = authXBL(tokenResp.first)
            
            onStatusUpdate(AuthStatus.GETTING_XSTS_TOKEN)
            val xsts = authXSTS(xbl.first)
            
            onStatusUpdate(AuthStatus.AUTHENTICATE_MINECRAFT)
            val mcAuth = authMinecraft(xsts, xbl.second)
            
            onStatusUpdate(AuthStatus.VERIFY_GAME_OWNERSHIP)
            verifyOwnership(mcAuth.accessToken)
            
            onStatusUpdate(AuthStatus.GETTING_PLAYER_PROFILE)
            val profile = getProfile(mcAuth.accessToken)
            
            onStatusUpdate(AuthStatus.COMPLETE)
            return Result.success(Account(username = profile.name, accessToken = mcAuth.accessToken, expiresAt = System.currentTimeMillis() + mcAuth.expiresIn * 1000, accountType = AccountType.MICROSOFT.tag, profileId = profile.id, refreshToken = tokenResp.second, xUid = xbl.second, skinModelType = SkinModelType.NONE))
        } catch (e: Exception) { Logger.error("Auth failed", e); return Result.failure(e) }
    }
    
    private suspend fun pollForToken(dc: DeviceCodeResponse): Pair<String, String> {
        val interval = dc.interval * 1000L
        val expire = System.currentTimeMillis() + dc.expiresIn * 1000L
        while (System.currentTimeMillis() < expire) {
            delay(interval)
            try {
                val resp = LauncherHttpClient.client.submitForm(
                    url = "$MS_AUTH_URL/consumers/oauth2/v2.0/token",
                    formParameters = Parameters.build { 
                        append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                        append("device_code", dc.deviceCode)
                        append("client_id", oauthClientId) 
                    }
                ).bodyAsText()
                val j = json.parseToJsonElement(resp).jsonObject
                if (j["token_type"]?.jsonPrimitive?.content == "Bearer") return Pair(j["access_token"]!!.jsonPrimitive.content, j["refresh_token"]!!.jsonPrimitive.content)
            } catch (e: Exception) { }
        }
        throw Exception("Auth timed out")
    }
    
    private suspend fun authXBL(accessToken: String): Pair<String, String> {
        val body = buildJsonObject { putJsonObject("Properties") { put("AuthMethod", "RPS"); put("SiteName", "user.auth.xboxlive.com"); put("RpsTicket", "d=$accessToken") }; put("RelyingParty", "http://auth.xboxlive.com"); put("TokenType", "JWT") }
        val resp = LauncherHttpClient.client.post("$XBL_URL/user/authenticate") { contentType(ContentType.Application.Json); setBody(body.toString()) }.bodyAsText()
        val j = json.parseToJsonElement(resp).jsonObject
        return Pair(j["Token"]!!.jsonPrimitive.content, j["DisplayClaims"]!!.jsonObject["xui"]!!.jsonArray[0].jsonObject["uhs"]!!.jsonPrimitive.content)
    }
    
    private suspend fun authXSTS(xblToken: String): String {
        val body = buildJsonObject { putJsonObject("Properties") { put("SandboxId", "RETAIL"); putJsonArray("UserTokens") { add(xblToken) } }; put("RelyingParty", "rp://api.minecraftservices.com/"); put("TokenType", "JWT") }
        val resp = LauncherHttpClient.client.post("$XSTS_URL/xsts/authorize") { contentType(ContentType.Application.Json); setBody(body.toString()) }.bodyAsText()
        return json.parseToJsonElement(resp).jsonObject["Token"]!!.jsonPrimitive.content
    }
    
    private suspend fun authMinecraft(xsts: String, uhs: String): MinecraftAuthResponse {
        val body = buildJsonObject { put("identityToken", "XBL3.0 x=$uhs;$xsts") }
        val resp = LauncherHttpClient.client.post("$MC_URL/authentication/login_with_xbox") { contentType(ContentType.Application.Json); setBody(body.toString()) }.bodyAsText()
        return json.decodeFromString<MinecraftAuthResponse>(resp)
    }
    
    private suspend fun verifyOwnership(accessToken: String) {
        val resp = LauncherHttpClient.client.get("$MC_URL/entitlements/mcstore") { header(HttpHeaders.Authorization, "Bearer $accessToken") }.bodyAsText()
        if (json.parseToJsonElement(resp).jsonObject["items"]?.jsonArray?.isEmpty() != false) throw NotPurchasedMinecraftException()
    }
    
    private suspend fun getProfile(accessToken: String): MinecraftProfile {
        val resp = LauncherHttpClient.client.get("$MC_URL/minecraft/profile") { header(HttpHeaders.Authorization, "Bearer $accessToken") }.bodyAsText()
        return json.decodeFromString<MinecraftProfile>(resp)
    }
}
