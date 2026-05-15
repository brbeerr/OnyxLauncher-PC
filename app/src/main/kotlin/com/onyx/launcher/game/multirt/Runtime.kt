package com.onyx.launcher.game.multirt

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class JavaRuntime(
    val name: String,
    val path: String,
    val version: String,
    val majorVersion: Int,
    val arch: String = "",
    val isAutoDetected: Boolean = true
) {
    val javaExecutable: String get() {
        val os = System.getProperty("os.name").lowercase()
        val bin = if (os.contains("win")) "java.exe" else "java"
        return File(path, "bin/$bin").absolutePath
    }
    
    companion object {
        fun getRecommendedJava(mcVersion: String): Int {
            val parts = mcVersion.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.size < 2) return 17
            val minor = parts[1]
            return when {
                minor >= 21 -> 21
                minor >= 17 -> 17
                minor >= 16 -> 16
                minor >= 12 -> 8
                else -> 8
            }
        }
    }
}
