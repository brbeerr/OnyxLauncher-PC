package com.onyx.launcher.game.multiplayer

import com.onyx.launcher.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

@Serializable
data class ServerStatus(
    val address: String,
    val port: Int = 25565,
    val online: Boolean = false,
    val motd: String = "",
    val playersOnline: Int = 0,
    val playersMax: Int = 0,
    val playerList: List<String> = emptyList(),
    val version: String = "",
    val protocol: Int = 0,
    val latency: Long = -1,
    val favicon: String? = null,
    val error: String? = null
)

@Serializable
data class SavedServer(
    val name: String,
    val address: String,
    val port: Int = 25565
)

object ServerPinger {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    suspend fun ping(address: String, port: Int = 25565, timeout: Int = 5000): ServerStatus = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(address, port), timeout)
            socket.soTimeout = timeout
            
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            
            // Handshake packet
            val handshake = buildHandshakePacket(address, port)
            writeVarInt(output, handshake.size)
            output.write(handshake)
            
            // Status request
            writeVarInt(output, 1)
            output.writeByte(0x00)
            
            // Read response
            val responseLength = readVarInt(input)
            val packetId = readVarInt(input)
            if (packetId != 0x00) {
                socket.close()
                return@withContext ServerStatus(address, port, error = "Invalid response")
            }
            
            val jsonLength = readVarInt(input)
            val jsonBytes = ByteArray(jsonLength)
            input.readFully(jsonBytes)
            val responseJson = String(jsonBytes)
            
            // Ping packet for latency
            val pingStart = System.currentTimeMillis()
            writeVarInt(output, 9)
            output.writeByte(0x01)
            output.writeLong(pingStart)
            
            readVarInt(input) // length
            input.readByte() // packet id
            input.readLong() // payload
            val latency = System.currentTimeMillis() - pingStart
            
            socket.close()
            
            parseStatusResponse(responseJson, address, port, latency)
        } catch (e: Exception) {
            Logger.warn("Failed to ping $address:$port - ${e.message}")
            ServerStatus(address, port, error = e.message ?: "Connection failed")
        }
    }
    
    private fun parseStatusResponse(responseJson: String, address: String, port: Int, latency: Long): ServerStatus {
        val obj = json.parseToJsonElement(responseJson).jsonObject
        
        val description = obj["description"]
        val motd = when {
            description is JsonPrimitive -> description.content
            description is JsonObject -> description["text"]?.jsonPrimitive?.content ?: ""
            else -> ""
        }
        
        val players = obj["players"]?.jsonObject
        val playersOnline = players?.get("online")?.jsonPrimitive?.intOrNull ?: 0
        val playersMax = players?.get("max")?.jsonPrimitive?.intOrNull ?: 0
        val playerList = players?.get("sample")?.jsonArray?.mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.content
        } ?: emptyList()
        
        val version = obj["version"]?.jsonObject
        val versionName = version?.get("name")?.jsonPrimitive?.content ?: ""
        val protocol = version?.get("protocol")?.jsonPrimitive?.intOrNull ?: 0
        
        val favicon = obj["favicon"]?.jsonPrimitive?.content
        
        return ServerStatus(
            address = address,
            port = port,
            online = true,
            motd = motd.replace(Regex("§[0-9a-fk-or]"), ""),
            playersOnline = playersOnline,
            playersMax = playersMax,
            playerList = playerList,
            version = versionName,
            protocol = protocol,
            latency = latency,
            favicon = favicon
        )
    }
    
    private fun buildHandshakePacket(host: String, port: Int): ByteArray {
        val buf = mutableListOf<Byte>()
        buf.addAll(encodeVarInt(0x00)) // packet id
        buf.addAll(encodeVarInt(763)) // protocol (1.20.1)
        buf.addAll(encodeVarInt(host.length))
        buf.addAll(host.toByteArray().toList())
        buf.add((port shr 8 and 0xFF).toByte())
        buf.add((port and 0xFF).toByte())
        buf.addAll(encodeVarInt(1)) // next state = status
        return buf.toByteArray()
    }
    
    private fun writeVarInt(output: DataOutputStream, value: Int) {
        output.write(encodeVarInt(value).toByteArray())
    }
    
    private fun encodeVarInt(value: Int): List<Byte> {
        var v = value
        val bytes = mutableListOf<Byte>()
        do {
            var temp = (v and 0x7F).toByte()
            v = v ushr 7
            if (v != 0) temp = (temp.toInt() or 0x80).toByte()
            bytes.add(temp)
        } while (v != 0)
        return bytes
    }
    
    private fun readVarInt(input: DataInputStream): Int {
        var numRead = 0
        var result = 0
        var read: Byte
        do {
            read = input.readByte()
            result = result or ((read.toInt() and 0x7F) shl (7 * numRead))
            numRead++
            if (numRead > 5) throw RuntimeException("VarInt too big")
        } while (read.toInt() and 0x80 != 0)
        return result
    }
}
