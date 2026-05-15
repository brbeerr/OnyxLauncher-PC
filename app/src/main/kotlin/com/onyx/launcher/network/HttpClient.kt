package com.onyx.launcher.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL

object LauncherHttpClient {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true; encodeDefaults = true }
    
    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 30_000 }
    }
    
    suspend fun getString(url: String) = withContext(Dispatchers.IO) { client.get(url).bodyAsText() }
    
    suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        json.decodeFromString<T>(client.get(url).bodyAsText())
    }
    
    suspend fun downloadFile(url: String, dest: File, onProgress: ((Long, Long) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            dest.parentFile?.mkdirs()
            val connection = URL(url).openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.getInputStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        onProgress?.invoke(downloaded, total)
                    }
                }
            }
            true
        } catch (e: Exception) { 
            e.printStackTrace()
            false 
        }
    }
    
    suspend fun downloadFileWithHash(url: String, dest: File, expectedSha1: String?, onProgress: ((Long, Long) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (dest.exists() && expectedSha1 != null && dest.sha1() == expectedSha1) return@withContext true
        if (!downloadFile(url, dest, onProgress)) return@withContext false
        if (expectedSha1 != null && dest.sha1() != expectedSha1) { dest.delete(); return@withContext false }
        true
    }
    
    private fun File.sha1(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        inputStream().use { inp -> val buf = ByteArray(8192); var r: Int; while (inp.read(buf).also { r = it } != -1) digest.update(buf, 0, r) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
