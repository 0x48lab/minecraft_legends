package com.hacklab.minecraft_legends.infrastructure.logger

import org.bukkit.plugin.Plugin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class LogLevel(val value: Int) {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3)
}

interface Logger {
    fun debug(message: String, vararg args: Any)
    fun info(message: String, vararg args: Any)
    fun warn(message: String, vararg args: Any)
    fun error(message: String, throwable: Throwable? = null, vararg args: Any)
    fun setLevel(level: LogLevel)
    fun getLevel(): LogLevel
}

class MinecraftLogger(
    private val plugin: Plugin,
    private var logLevel: LogLevel = LogLevel.INFO,
    private val enableFileOutput: Boolean = true,
    private val enableConsoleOutput: Boolean = true
) : Logger {
    
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    override fun debug(message: String, vararg args: Any) {
        log(LogLevel.DEBUG, message, null, *args)
    }
    
    override fun info(message: String, vararg args: Any) {
        log(LogLevel.INFO, message, null, *args)
    }
    
    override fun warn(message: String, vararg args: Any) {
        log(LogLevel.WARN, message, null, *args)
    }
    
    override fun error(message: String, throwable: Throwable?, vararg args: Any) {
        log(LogLevel.ERROR, message, throwable, *args)
    }
    
    override fun setLevel(level: LogLevel) {
        this.logLevel = level
    }
    
    override fun getLevel(): LogLevel = logLevel
    
    private fun log(level: LogLevel, message: String, throwable: Throwable?, vararg args: Any) {
        if (level.value < logLevel.value) {
            return
        }
        
        val formattedMessage = formatMessage(level, message, *args)
        
        if (enableConsoleOutput) {
            when (level) {
                LogLevel.DEBUG -> plugin.logger.fine(formattedMessage)
                LogLevel.INFO -> plugin.logger.info(formattedMessage)
                LogLevel.WARN -> plugin.logger.warning(formattedMessage)
                LogLevel.ERROR -> {
                    plugin.logger.severe(formattedMessage)
                    throwable?.printStackTrace()
                }
            }
        }
        
        if (enableFileOutput) {
            writeToFile(level, formattedMessage, throwable)
        }
    }
    
    private fun formatMessage(level: LogLevel, message: String, vararg args: Any): String {
        val timestamp = LocalDateTime.now().format(timeFormatter)
        val formattedMessage = if (args.isNotEmpty()) {
            String.format(message, *args)
        } else {
            message
        }
        return "[$timestamp] [${level.name}] $formattedMessage"
    }
    
    private fun writeToFile(level: LogLevel, message: String, throwable: Throwable?) {
        try {
            val logFile = plugin.dataFolder.resolve("logs/${getCurrentLogFileName()}")
            logFile.parentFile.mkdirs()
            
            logFile.appendText("$message\n")
            
            throwable?.let { t ->
                logFile.appendText("Exception: ${t.message}\n")
                logFile.appendText(t.stackTraceToString() + "\n")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to write to log file: ${e.message}")
        }
    }
    
    private fun getCurrentLogFileName(): String {
        val today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return "minecraft-legends-$today.log"
    }
}

object LoggerFactory {
    private var instance: Logger? = null
    
    fun initialize(plugin: Plugin, config: LogConfig) {
        instance = MinecraftLogger(
            plugin = plugin,
            logLevel = config.level,
            enableFileOutput = config.fileOutput,
            enableConsoleOutput = config.consoleOutput
        )
    }
    
    fun getLogger(): Logger {
        return instance ?: throw IllegalStateException("Logger not initialized")
    }
}

data class LogConfig(
    val level: LogLevel = LogLevel.INFO,
    val fileOutput: Boolean = true,
    val consoleOutput: Boolean = true,
    val maxFileSize: String = "10MB"
)