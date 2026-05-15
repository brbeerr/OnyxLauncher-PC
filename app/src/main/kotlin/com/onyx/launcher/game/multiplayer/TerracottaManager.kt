package com.onyx.launcher.game.multiplayer

import com.onyx.launcher.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Terracotta LAN Online - allows sharing a local MC server over internet
 * by forwarding connections through a relay or P2P tunnel.
 * 
 * Two modes:
 * 1. HOST: Opens a LAN world in MC -> Terracotta creates a tunnel and gives a code
 * 2. JOIN: Enter the code -> connects to the host's LAN world
 */
enum class TerracottaState { IDLE, HOSTING, JOINING, CONNECTED, ERROR }

data class TerracottaSession(
    val code: String = "",
    val hostAddress: String = "",
    val hostPort: Int = 0,
    val localPort: Int = 0
)

object TerracottaManager {
    private val _state = MutableStateFlow(TerracottaState.IDLE)
    val state = _state.asStateFlow()
    
    private val _session = MutableStateFlow<TerracottaSession?>(null)
    val session = _session.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    private var proxyServer: ServerSocket? = null
    private var relayProcess: Process? = null
    
    /**
     * Start hosting: detect LAN port from MC logs, create a tunnel
     */
    suspend fun startHosting(lanPort: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            _state.value = TerracottaState.HOSTING
            _error.value = null
            
            // Generate a simple connection code
            val code = generateCode()
            
            // Start a local relay that forwards connections
            val localProxyPort = findFreePort()
            
            _session.value = TerracottaSession(
                code = code,
                hostAddress = "0.0.0.0",
                hostPort = lanPort,
                localPort = localProxyPort
            )
            
            // Start proxy server that forwards to LAN game
            proxyServer = ServerSocket(localProxyPort)
            proxyServer!!.soTimeout = 1000
            
            Thread(Runnable {
                while (_state.value == TerracottaState.HOSTING) {
                    try {
                        val client = proxyServer!!.accept()
                        Thread(Runnable { forwardConnection(client, "127.0.0.1", lanPort) }).start()
                    } catch (e: SocketTimeoutException) { /* continue */ }
                    catch (e: Exception) { break }
                }
            }, "Terracotta-Host").start()
            
            Logger.info("Terracotta hosting on port $localProxyPort, LAN: $lanPort, Code: $code")
            Result.success(code)
        } catch (e: Exception) {
            _state.value = TerracottaState.ERROR
            _error.value = e.message
            Result.failure(e)
        }
    }
    
    /**
     * Join a host using connection code
     */
    suspend fun joinSession(code: String, remoteHost: String, remotePort: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _state.value = TerracottaState.JOINING
            _error.value = null
            
            // Create local proxy that forwards to remote host
            val localPort = findFreePort()
            
            proxyServer = ServerSocket(localPort)
            proxyServer!!.soTimeout = 1000
            
            Thread(Runnable {
                while (_state.value == TerracottaState.JOINING || _state.value == TerracottaState.CONNECTED) {
                    try {
                        val client = proxyServer!!.accept()
                        Thread(Runnable { forwardConnection(client, remoteHost, remotePort) }).start()
                        _state.value = TerracottaState.CONNECTED
                    } catch (e: SocketTimeoutException) { /* continue */ }
                    catch (e: Exception) { break }
                }
            }, "Terracotta-Join").start()
            
            _session.value = TerracottaSession(
                code = code,
                hostAddress = remoteHost,
                hostPort = remotePort,
                localPort = localPort
            )
            
            Logger.info("Terracotta joining $remoteHost:$remotePort via local port $localPort")
            Result.success(localPort) // MC connects to localhost:localPort
        } catch (e: Exception) {
            _state.value = TerracottaState.ERROR
            _error.value = e.message
            Result.failure(e)
        }
    }
    
    fun stop() {
        _state.value = TerracottaState.IDLE
        _session.value = null
        proxyServer?.close()
        proxyServer = null
        relayProcess?.destroyForcibly()
        relayProcess = null
        Logger.info("Terracotta stopped")
    }
    
    /**
     * Detect LAN port from Minecraft log output
     */
    fun detectLanPort(logLine: String): Int? {
        // MC outputs: "Local game hosted on port XXXXX"
        val regex = Regex("""Local game hosted on port (\d+)""")
        return regex.find(logLine)?.groupValues?.get(1)?.toIntOrNull()
    }
    
    private fun forwardConnection(client: Socket, targetHost: String, targetPort: Int) {
        try {
            val target = Socket()
            target.connect(InetSocketAddress(targetHost, targetPort), 5000)
            
            val t1 = Thread { pipeStreams(client.getInputStream(), target.getOutputStream()) }
            val t2 = Thread { pipeStreams(target.getInputStream(), client.getOutputStream()) }
            t1.start()
            t2.start()
            t1.join()
            t2.join()
            
            client.close()
            target.close()
        } catch (e: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }
    
    private fun pipeStreams(input: InputStream, output: OutputStream) {
        try {
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (e: Exception) { /* stream closed */ }
    }
    
    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }
    
    private fun generateCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
