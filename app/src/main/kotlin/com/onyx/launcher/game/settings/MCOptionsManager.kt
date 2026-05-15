package com.onyx.launcher.game.settings

import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import java.io.File

data class MCOption(
    val key: String,
    val value: String,
    val displayName: String = key,
    val category: OptionCategory = OptionCategory.OTHER
)

enum class OptionCategory { VIDEO, CONTROLS, SOUND, CHAT, OTHER }

object MCOptionsManager {
    
    private val displayNames = mapOf(
        "renderDistance" to "Render Distance",
        "simulationDistance" to "Simulation Distance",
        "gamma" to "Brightness",
        "fov" to "FOV",
        "maxFps" to "Max FPS",
        "graphicsMode" to "Graphics",
        "lang" to "Language",
        "soundCategory_master" to "Master Volume",
        "soundCategory_music" to "Music Volume",
        "soundCategory_ambient" to "Ambient Volume",
        "guiScale" to "GUI Scale",
        "fullscreen" to "Fullscreen",
        "vsync" to "VSync",
        "entityShadows" to "Entity Shadows",
        "particles" to "Particles",
        "mipmapLevels" to "Mipmap Levels",
        "biomeBlendRadius" to "Biome Blend",
        "chatVisibility" to "Chat Visibility",
        "autoJump" to "Auto Jump",
        "toggleCrouch" to "Toggle Crouch",
        "toggleSprint" to "Toggle Sprint",
        "mouseSensitivity" to "Mouse Sensitivity",
        "difficulty" to "Difficulty"
    )
    
    private val categories = mapOf(
        "renderDistance" to OptionCategory.VIDEO,
        "simulationDistance" to OptionCategory.VIDEO,
        "gamma" to OptionCategory.VIDEO,
        "fov" to OptionCategory.VIDEO,
        "maxFps" to OptionCategory.VIDEO,
        "graphicsMode" to OptionCategory.VIDEO,
        "guiScale" to OptionCategory.VIDEO,
        "fullscreen" to OptionCategory.VIDEO,
        "vsync" to OptionCategory.VIDEO,
        "entityShadows" to OptionCategory.VIDEO,
        "particles" to OptionCategory.VIDEO,
        "mipmapLevels" to OptionCategory.VIDEO,
        "biomeBlendRadius" to OptionCategory.VIDEO,
        "soundCategory_master" to OptionCategory.SOUND,
        "soundCategory_music" to OptionCategory.SOUND,
        "soundCategory_ambient" to OptionCategory.SOUND,
        "mouseSensitivity" to OptionCategory.CONTROLS,
        "autoJump" to OptionCategory.CONTROLS,
        "toggleCrouch" to OptionCategory.CONTROLS,
        "toggleSprint" to OptionCategory.CONTROLS,
        "chatVisibility" to OptionCategory.CHAT,
        "lang" to OptionCategory.OTHER,
        "difficulty" to OptionCategory.OTHER
    )
    
    fun readOptions(gameDir: File = PathManager.DIR_GAME): List<MCOption> {
        val optionsFile = File(gameDir, "options.txt")
        if (!optionsFile.exists()) return emptyList()
        
        return try {
            optionsFile.readLines()
                .filter { it.contains(":") }
                .map { line ->
                    val parts = line.split(":", limit = 2)
                    val key = parts[0]
                    val value = parts.getOrElse(1) { "" }
                    MCOption(
                        key = key,
                        value = value,
                        displayName = displayNames[key] ?: key,
                        category = categories[key] ?: OptionCategory.OTHER
                    )
                }
        } catch (e: Exception) {
            Logger.error("Failed to read options.txt", e)
            emptyList()
        }
    }
    
    fun writeOption(key: String, value: String, gameDir: File = PathManager.DIR_GAME): Boolean {
        val optionsFile = File(gameDir, "options.txt")
        if (!optionsFile.exists()) return false
        
        return try {
            val lines = optionsFile.readLines().toMutableList()
            val index = lines.indexOfFirst { it.startsWith("$key:") }
            if (index >= 0) {
                lines[index] = "$key:$value"
            } else {
                lines.add("$key:$value")
            }
            optionsFile.writeText(lines.joinToString("\n"))
            true
        } catch (e: Exception) {
            Logger.error("Failed to write option $key", e)
            false
        }
    }
    
    fun writeOptions(options: Map<String, String>, gameDir: File = PathManager.DIR_GAME): Boolean {
        val optionsFile = File(gameDir, "options.txt")
        return try {
            val lines = if (optionsFile.exists()) optionsFile.readLines().toMutableList() else mutableListOf()
            for ((key, value) in options) {
                val index = lines.indexOfFirst { it.startsWith("$key:") }
                if (index >= 0) lines[index] = "$key:$value"
                else lines.add("$key:$value")
            }
            optionsFile.parentFile?.mkdirs()
            optionsFile.writeText(lines.joinToString("\n"))
            true
        } catch (e: Exception) { false }
    }
    
    fun getGraphicsModeDisplay(value: String): String = when (value) {
        "0" -> "Fancy"
        "1" -> "Fast"
        "2" -> "Fabulous"
        else -> value
    }
    
    fun getParticlesDisplay(value: String): String = when (value) {
        "0" -> "All"
        "1" -> "Decreased"
        "2" -> "Minimal"
        else -> value
    }
}
