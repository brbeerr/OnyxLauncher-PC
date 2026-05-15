package com.onyx.launcher.utils

import java.io.File

object PathManager {
    private val userHome: String = System.getProperty("user.home")
    
    val DIR_LAUNCHER: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> File(System.getenv("APPDATA"), "OnyxLauncher")
            os.contains("mac") -> File(userHome, "Library/Application Support/OnyxLauncher")
            else -> File(userHome, ".onyxlauncher")
        }
    }
    
    val DIR_GAME: File by lazy { File(DIR_LAUNCHER, "game") }
    val DIR_VERSIONS: File by lazy { File(DIR_GAME, "versions") }
    val DIR_LIBRARIES: File by lazy { File(DIR_GAME, "libraries") }
    val DIR_ASSETS: File by lazy { File(DIR_GAME, "assets") }
    val DIR_ACCOUNTS: File by lazy { File(DIR_LAUNCHER, "accounts") }
    val DIR_ACCOUNT_SKIN: File by lazy { File(DIR_ACCOUNTS, "skins") }
    val DIR_CACHE: File by lazy { File(DIR_LAUNCHER, "cache") }
    val DIR_RUNTIME: File by lazy { File(DIR_LAUNCHER, "runtime") }
    val DIR_MODS: File by lazy { File(DIR_GAME, "mods") }
    val DIR_INSTANCES: File by lazy { File(DIR_GAME, "instances") }
    val DIR_RESOURCEPACKS: File by lazy { File(DIR_GAME, "resourcepacks") }
    val DIR_SHADERPACKS: File by lazy { File(DIR_GAME, "shaderpacks") }
    val DIR_SAVES: File by lazy { File(DIR_GAME, "saves") }
    val DIR_LOGS: File by lazy { File(DIR_GAME, "logs") }
    val FILE_SETTINGS: File by lazy { File(DIR_LAUNCHER, "settings.json") }
    val FILE_ACCOUNTS: File by lazy { File(DIR_ACCOUNTS, "accounts.json") }
    
    fun initialize() {
        listOf(DIR_LAUNCHER, DIR_GAME, DIR_VERSIONS, DIR_LIBRARIES, DIR_ASSETS, 
               DIR_ACCOUNTS, DIR_ACCOUNT_SKIN, DIR_CACHE, DIR_RUNTIME, DIR_MODS,
               DIR_INSTANCES, DIR_RESOURCEPACKS, DIR_SAVES, DIR_LOGS)
            .forEach { if (!it.exists()) it.mkdirs() }
    }
    
    fun getVersionDir(versionId: String): File = File(DIR_VERSIONS, versionId)
    fun getVersionJson(versionId: String): File = File(getVersionDir(versionId), "$versionId.json")
    fun getVersionJar(versionId: String): File = File(getVersionDir(versionId), "$versionId.jar")
    fun getVersionNatives(versionId: String): File = File(getVersionDir(versionId), "natives")
}
