package com.onyx.launcher.utils

import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Logger {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    
    enum class Level { DEBUG, INFO, WARN, ERROR }
    
    fun debug(message: String, tag: String = "Launcher") = log(Level.DEBUG, tag, message)
    fun info(message: String, tag: String = "Launcher") = log(Level.INFO, tag, message)
    fun warn(message: String, tag: String = "Launcher") = log(Level.WARN, tag, message)
    fun error(message: String, throwable: Throwable? = null, tag: String = "Launcher") {
        log(Level.ERROR, tag, message)
        throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            log(Level.ERROR, tag, sw.toString())
        }
    }
    
    private fun log(level: Level, tag: String, message: String) {
        val timestamp = LocalDateTime.now().format(dateFormatter)
        val logMessage = "[$timestamp] [${level.name}] [$tag] $message"
        if (level == Level.ERROR) System.err.println(logMessage) else println(logMessage)
    }
}
