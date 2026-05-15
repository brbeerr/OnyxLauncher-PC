package com.onyx.launcher.game.saves

import com.onyx.launcher.utils.Logger
import com.onyx.launcher.utils.PathManager
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

data class WorldSave(
    val directory: File,
    val name: String,
    val levelName: String = "",
    val gameMode: GameMode = GameMode.SURVIVAL,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val lastPlayed: Long = 0,
    val sizeBytes: Long = 0,
    val seed: Long = 0,
    val hardcore: Boolean = false,
    val cheatsEnabled: Boolean = false,
    val version: String = ""
) {
    val sizeFormatted: String get() {
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
    }
}

enum class GameMode(val displayName: String) {
    SURVIVAL("Survival"), CREATIVE("Creative"), ADVENTURE("Adventure"), SPECTATOR("Spectator")
}

enum class Difficulty(val displayName: String) {
    PEACEFUL("Peaceful"), EASY("Easy"), NORMAL("Normal"), HARD("Hard")
}

object SavesManager {
    
    fun getWorlds(gameDir: File = PathManager.DIR_GAME): List<WorldSave> {
        val savesDir = File(gameDir, "saves")
        if (!savesDir.exists()) return emptyList()
        
        return savesDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            readWorld(dir)
        }?.sortedByDescending { it.lastPlayed } ?: emptyList()
    }
    
    private fun readWorld(dir: File): WorldSave? {
        val levelDat = File(dir, "level.dat")
        if (!levelDat.exists()) return null
        
        return try {
            val nbt = readNbtFile(levelDat)
            val data = nbt.getCompound("Data") ?: nbt
            
            WorldSave(
                directory = dir,
                name = dir.name,
                levelName = data.getString("LevelName") ?: dir.name,
                gameMode = when (data.getInt("GameType")) {
                    0 -> GameMode.SURVIVAL; 1 -> GameMode.CREATIVE
                    2 -> GameMode.ADVENTURE; 3 -> GameMode.SPECTATOR
                    else -> GameMode.SURVIVAL
                },
                difficulty = when (data.getInt("Difficulty")) {
                    0 -> Difficulty.PEACEFUL; 1 -> Difficulty.EASY
                    2 -> Difficulty.NORMAL; 3 -> Difficulty.HARD
                    else -> Difficulty.NORMAL
                },
                lastPlayed = data.getLong("LastPlayed"),
                sizeBytes = calculateDirSize(dir),
                seed = data.getLong("RandomSeed"),
                hardcore = data.getByte("hardcore") == 1.toByte(),
                cheatsEnabled = data.getByte("allowCommands") == 1.toByte(),
                version = data.getCompound("Version")?.getString("Name") ?: ""
            )
        } catch (e: Exception) {
            Logger.warn("Failed to read world: ${dir.name} - ${e.message}")
            WorldSave(directory = dir, name = dir.name, sizeBytes = calculateDirSize(dir))
        }
    }
    
    fun deleteWorld(world: WorldSave): Boolean = world.directory.deleteRecursively()
    
    fun duplicateWorld(world: WorldSave, newName: String): Boolean {
        val newDir = File(world.directory.parentFile, newName)
        if (newDir.exists()) return false
        return world.directory.copyRecursively(newDir)
    }
    
    private fun calculateDirSize(dir: File): Long {
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
    
    // Minimal NBT reader for level.dat
    private fun readNbtFile(file: File): NbtCompound {
        return try {
            DataInputStream(GZIPInputStream(FileInputStream(file))).use { input ->
                val tagType = input.readByte()
                if (tagType != 10.toByte()) return NbtCompound()
                input.readUTF() // root name
                readCompound(input)
            }
        } catch (e: Exception) { NbtCompound() }
    }
    
    private fun readCompound(input: DataInputStream): NbtCompound {
        val map = mutableMapOf<String, Any>()
        while (true) {
            val type = input.readByte().toInt()
            if (type == 0) break // TAG_End
            val name = input.readUTF()
            map[name] = readTag(input, type) ?: continue
        }
        return NbtCompound(map)
    }
    
    private fun readTag(input: DataInputStream, type: Int): Any? {
        return when (type) {
            1 -> input.readByte() // TAG_Byte
            2 -> input.readShort() // TAG_Short
            3 -> input.readInt() // TAG_Int
            4 -> input.readLong() // TAG_Long
            5 -> input.readFloat() // TAG_Float
            6 -> input.readDouble() // TAG_Double
            7 -> { val len = input.readInt(); ByteArray(len).also { input.readFully(it) } } // TAG_ByteArray
            8 -> input.readUTF() // TAG_String
            9 -> { // TAG_List
                val listType = input.readByte().toInt()
                val count = input.readInt()
                (0 until count).mapNotNull { readTag(input, listType) }
            }
            10 -> readCompound(input) // TAG_Compound
            11 -> { val len = input.readInt(); IntArray(len) { input.readInt() } } // TAG_IntArray
            12 -> { val len = input.readInt(); LongArray(len) { input.readLong() } } // TAG_LongArray
            else -> null
        }
    }
}

class NbtCompound(private val map: Map<String, Any> = emptyMap()) {
    fun getString(key: String): String? = map[key] as? String
    fun getInt(key: String): Int = (map[key] as? Int) ?: (map[key] as? Short)?.toInt() ?: 0
    fun getLong(key: String): Long = (map[key] as? Long) ?: (map[key] as? Int)?.toLong() ?: 0L
    fun getByte(key: String): Byte = (map[key] as? Byte) ?: 0
    fun getCompound(key: String): NbtCompound? = map[key] as? NbtCompound
}
