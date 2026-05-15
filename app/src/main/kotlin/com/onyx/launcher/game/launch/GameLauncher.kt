package com.onyx.launcher.game.launch

import com.onyx.launcher.data.*
import com.onyx.launcher.game.VersionManager
import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GameLauncher {
    suspend fun launch(versionId: String, account: Account, javaPath: String? = null, maxMemory: Int = 2048, onOutput: (String) -> Unit = {}, onExit: (Int) -> Unit = {}): Result<Process> = withContext(Dispatchers.IO) {
        try {
            val vj = VersionManager.getVersionJson(versionId).getOrThrow()
            val java = findJava(javaPath)
            val cp = buildClasspath(versionId, vj)
            extractNatives(versionId, vj)
            val cmd = buildCommand(java, versionId, vj, account, cp, maxMemory)
            Logger.info("Launching: ${cmd.take(10).joinToString(" ")}...")
            
            val proc = ProcessBuilder(cmd).directory(PathManager.DIR_GAME).redirectErrorStream(true).start()
            Thread { proc.inputStream.bufferedReader().use { r -> r.forEachLine { onOutput(it) } } }.start()
            Thread { onExit(proc.waitFor()) }.start()
            Result.success(proc)
        } catch (e: Exception) { Logger.error("Launch failed", e); Result.failure(e) }
    }
    
    private fun findJava(custom: String?): String {
        if (!custom.isNullOrBlank() && File(custom).exists()) return custom
        System.getenv("JAVA_HOME")?.let { val f = File(it, "bin/java"); if (f.exists()) return f.absolutePath }
        return "java"
    }
    
    private fun buildClasspath(vid: String, vj: VersionJson): List<String> {
        val cp = mutableListOf<String>()
        vj.libraries.filter { isAllowed(it) }.forEach { lib -> lib.downloads?.artifact?.let { val f = File(PathManager.DIR_LIBRARIES, it.path); if (f.exists()) cp.add(f.absolutePath) } }
        val jar = PathManager.getVersionJar(vid); if (jar.exists()) cp.add(jar.absolutePath)
        return cp
    }
    
    private fun extractNatives(vid: String, vj: VersionJson) {
        val dir = PathManager.getVersionNatives(vid); dir.mkdirs()
        val os = getCurrentOS()
        vj.libraries.filter { isAllowed(it) }.forEach { lib ->
            lib.natives?.get(os)?.let { nk -> lib.downloads?.classifiers?.get(nk)?.let { na ->
                val nf = File(PathManager.DIR_LIBRARIES, na.path)
                if (nf.exists()) try {
                    java.util.zip.ZipFile(nf).use { zip ->
                        val excl = lib.extract?.exclude ?: emptyList()
                        zip.entries().asSequence().filter { !it.isDirectory && excl.none { e -> it.name.startsWith(e) } }.forEach { e ->
                            val out = File(dir, e.name); out.parentFile?.mkdirs()
                            zip.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                        }
                    }
                } catch (e: Exception) { }
            }}
        }
    }
    
    private fun buildCommand(java: String, vid: String, vj: VersionJson, acc: Account, cp: List<String>, maxMem: Int): List<String> {
        val cmd = mutableListOf(java, "-Xms512M", "-Xmx${maxMem}M", "-Djava.library.path=${PathManager.getVersionNatives(vid).absolutePath}")
        cmd.addAll(listOf("-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20", "-XX:MaxGCPauseMillis=50"))
        cmd.add("-cp"); cmd.add(cp.joinToString(if (System.getProperty("os.name").lowercase().contains("win")) ";" else ":"))
        cmd.add(vj.mainClass)
        val repl = mapOf("\${auth_player_name}" to acc.username, "\${version_name}" to vid, "\${game_directory}" to PathManager.DIR_GAME.absolutePath, "\${assets_root}" to PathManager.DIR_ASSETS.absolutePath, "\${assets_index_name}" to (vj.assetIndex?.id ?: vj.assets ?: "legacy"), "\${auth_uuid}" to acc.profileId, "\${auth_access_token}" to acc.accessToken, "\${user_type}" to if (acc.isMicrosoftAccount()) "msa" else "legacy", "\${version_type}" to (vj.type ?: "release"))
        vj.arguments?.getGameArgs()?.forEach { var a = it; repl.forEach { (k, v) -> a = a.replace(k, v) }; cmd.add(a) }
        vj.minecraftArguments?.let { var a = it; repl.forEach { (k, v) -> a = a.replace(k, v) }; cmd.addAll(a.split(" ")) }
        return cmd
    }
    
    private fun isAllowed(lib: Library): Boolean {
        val rules = lib.rules ?: return true
        var allowed = false
        for (r in rules) { if (r.os == null || r.os.name == getCurrentOS()) allowed = r.action == "allow" }
        return allowed
    }
    
    private fun getCurrentOS() = System.getProperty("os.name").lowercase().let { when { it.contains("win") -> "windows"; it.contains("mac") -> "osx"; else -> "linux" } }
}
