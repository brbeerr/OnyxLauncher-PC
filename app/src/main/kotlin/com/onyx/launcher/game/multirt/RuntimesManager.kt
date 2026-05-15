package com.onyx.launcher.game.multirt

import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object RuntimesManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _runtimes = MutableStateFlow<List<JavaRuntime>>(emptyList())
    val runtimes = _runtimes.asStateFlow()
    
    private val configFile: File get() = File(PathManager.DIR_LAUNCHER, "java_runtimes.json")
    
    suspend fun initialize() = withContext(Dispatchers.IO) {
        loadSaved()
        detectSystemJava()
    }
    
    private fun loadSaved() {
        try {
            if (configFile.exists()) {
                _runtimes.value = json.decodeFromString<List<JavaRuntime>>(configFile.readText())
            }
        } catch (e: Exception) {
            Logger.error("Failed to load runtimes config", e)
        }
    }
    
    private fun save() {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(_runtimes.value))
        } catch (e: Exception) {
            Logger.error("Failed to save runtimes config", e)
        }
    }
    
    suspend fun detectSystemJava() = withContext(Dispatchers.IO) {
        val detected = mutableListOf<JavaRuntime>()
        val os = System.getProperty("os.name").lowercase()
        
        // Check JAVA_HOME
        System.getenv("JAVA_HOME")?.let { jh ->
            probeJavaHome(File(jh))?.let { detected.add(it) }
        }
        
        // Check common locations
        val searchPaths = when {
            os.contains("win") -> listOf(
                File("C:/Program Files/Java"),
                File("C:/Program Files (x86)/Java"),
                File("C:/Program Files/Eclipse Adoptium"),
                File("C:/Program Files/Microsoft"),
                File(System.getenv("LOCALAPPDATA") ?: "", "Programs/Eclipse Adoptium")
            )
            os.contains("mac") -> listOf(
                File("/Library/Java/JavaVirtualMachines"),
                File("/usr/local/opt/openjdk"),
                File(System.getProperty("user.home"), ".sdkman/candidates/java")
            )
            else -> listOf(
                File("/usr/lib/jvm"),
                File("/usr/java"),
                File(System.getProperty("user.home"), ".sdkman/candidates/java"),
                File(System.getProperty("user.home"), ".jdks")
            )
        }
        
        for (searchPath in searchPaths) {
            if (!searchPath.exists()) continue
            searchPath.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val home = if (os.contains("mac") && File(dir, "Contents/Home").exists()) 
                    File(dir, "Contents/Home") else dir
                probeJavaHome(home)?.let { detected.add(it) }
            }
        }
        
        // Also check launcher's own runtime dir
        PathManager.DIR_RUNTIME.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            probeJavaHome(dir)?.let { detected.add(it.copy(isAutoDetected = false)) }
        }
        
        // Merge with existing (keep custom entries, update auto-detected)
        val existing = _runtimes.value.filter { !it.isAutoDetected }
        val unique = (existing + detected).distinctBy { it.path }
        _runtimes.value = unique.sortedBy { it.majorVersion }
        save()
        Logger.info("Detected ${detected.size} Java runtimes")
    }
    
    private fun probeJavaHome(home: File): JavaRuntime? {
        val os = System.getProperty("os.name").lowercase()
        val bin = if (os.contains("win")) "java.exe" else "java"
        val javaExec = File(home, "bin/$bin")
        if (!javaExec.exists()) return null
        
        return try {
            val proc = ProcessBuilder(javaExec.absolutePath, "-version")
                .redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(proc.inputStream)).readLines().joinToString("\n")
            proc.waitFor()
            
            val versionRegex = Regex("""(?:java|openjdk) version "([^"]+)"""")
            val runtimeRegex = Regex("""(?:OpenJDK|Java\(TM\)|Java HotSpot).*?(\d+)""")
            
            val versionMatch = versionRegex.find(output)
            val version = versionMatch?.groupValues?.get(1) ?: ""
            val major = version.split(".").firstOrNull()?.toIntOrNull() 
                ?: if (version.startsWith("1.")) version.split(".").getOrNull(1)?.toIntOrNull() ?: 8 else 8
            
            val arch = if (output.contains("64-Bit")) "x64" else "x86"
            val name = "Java $major ($version)"
            
            JavaRuntime(name, home.absolutePath, version, major, arch, true)
        } catch (e: Exception) {
            null
        }
    }
    
    fun addRuntime(runtime: JavaRuntime) {
        _runtimes.value = (_runtimes.value + runtime).distinctBy { it.path }.sortedBy { it.majorVersion }
        save()
    }
    
    fun removeRuntime(runtime: JavaRuntime) {
        _runtimes.value = _runtimes.value.filter { it.path != runtime.path }
        save()
    }
    
    fun findBestRuntime(mcVersion: String): JavaRuntime? {
        val recommended = JavaRuntime.getRecommendedJava(mcVersion)
        return _runtimes.value.find { it.majorVersion == recommended }
            ?: _runtimes.value.find { it.majorVersion >= recommended }
            ?: _runtimes.value.lastOrNull()
    }
}
