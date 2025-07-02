package com.hacklab.minecraft_legends.infrastructure.config

import com.hacklab.minecraft_legends.infrastructure.logger.LogLevel
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.InputStream

interface ConfigurationManager {
    fun loadConfig()
    fun reloadConfig()
    fun saveDefaultConfig()
    fun getConfig(): FileConfiguration
    fun getLogLevel(): LogLevel
    fun getLanguage(): String
    fun getDatabaseFile(): File
    fun getWorldSize(): Int
    fun getMaxPlayers(): Int
    fun getMinPlayers(): Int
    fun getTeamSize(): Int
    fun getRespawnBeaconCount(): Int
    fun isApiEnabled(): Boolean
    fun getApiBaseUrl(): String
    fun getApiToken(): String
}

class ConfigurationManagerImpl(
    private val plugin: Plugin
) : ConfigurationManager {
    
    private val logger = LoggerFactory.getLogger()
    private var config: FileConfiguration = YamlConfiguration()
    
    override fun loadConfig() {
        try {
            saveDefaultConfig()
            reloadConfig()
            logger.info("Configuration loaded successfully")
        } catch (e: Exception) {
            logger.error("Failed to load configuration", e)
            throw e
        }
    }
    
    override fun reloadConfig() {
        try {
            val configFile = File(plugin.dataFolder, "config.yml")
            if (!configFile.exists()) {
                saveDefaultConfig()
            }
            
            config = YamlConfiguration.loadConfiguration(configFile)
            
            // デフォルト値をマージ
            val defaultConfig = getDefaultConfig()
            config.setDefaults(defaultConfig)
            config.options().copyDefaults(true)
            
            logger.info("Configuration reloaded")
        } catch (e: Exception) {
            logger.error("Failed to reload configuration", e)
            throw e
        }
    }
    
    override fun saveDefaultConfig() {
        val configFile = File(plugin.dataFolder, "config.yml")
        if (!configFile.exists()) {
            plugin.dataFolder.mkdirs()
            plugin.saveResource("config.yml", false)
        }
    }
    
    override fun getConfig(): FileConfiguration = config
    
    override fun getLogLevel(): LogLevel {
        val levelString = config.getString("logging.level", "INFO") ?: "INFO"
        return try {
            LogLevel.valueOf(levelString.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid log level: $levelString, using INFO")
            LogLevel.INFO
        }
    }
    
    override fun getLanguage(): String {
        return config.getString("language.default", "ja") ?: "ja"
    }
    
    override fun getDatabaseFile(): File {
        val databasePath = config.getString("database.sqlite.file", "plugins/MinecraftLegends/data.db")
        return File(databasePath)
    }
    
    override fun getWorldSize(): Int {
        return config.getInt("world.size", 3000)
    }
    
    override fun getMaxPlayers(): Int {
        return config.getInt("game.max-players", 24)
    }
    
    override fun getMinPlayers(): Int {
        return config.getInt("game.min-players", 12)
    }
    
    override fun getTeamSize(): Int {
        return config.getInt("game.team-size", 3)
    }
    
    override fun getRespawnBeaconCount(): Int {
        return config.getInt("respawn-beacon.count", 6)
    }
    
    override fun isApiEnabled(): Boolean {
        return config.getBoolean("api.enabled", false)
    }
    
    override fun getApiBaseUrl(): String {
        return config.getString("api.base-url", "") ?: ""
    }
    
    override fun getApiToken(): String {
        return config.getString("api.token", "") ?: ""
    }
    
    private fun getDefaultConfig(): YamlConfiguration {
        val defaultConfig = YamlConfiguration()
        
        // ワールド設定のデフォルト値
        defaultConfig.set("world.size", 3000)
        defaultConfig.set("world.name-prefix", "br_world")
        
        // ゲーム設定のデフォルト値
        defaultConfig.set("game.max-players", 24)
        defaultConfig.set("game.min-players", 12)
        defaultConfig.set("game.team-size", 3)
        defaultConfig.set("game.legend-selection-time", 60)
        defaultConfig.set("game.auto-start", true)
        
        // リスポーンビーコン設定のデフォルト値
        defaultConfig.set("respawn-beacon.count", 6)
        defaultConfig.set("respawn-beacon.respawn-time", 7)
        defaultConfig.set("respawn-beacon.min-distance", 200)
        
        // リング設定のデフォルト値
        defaultConfig.set("ring.update-interval", 3)
        
        // データベース設定のデフォルト値
        defaultConfig.set("database.type", "sqlite")
        defaultConfig.set("database.sqlite.file", "plugins/MinecraftLegends/data.db")
        
        // 言語設定のデフォルト値
        defaultConfig.set("language.default", "ja")
        defaultConfig.set("language.available.0", "ja")
        defaultConfig.set("language.available.1", "en")
        
        // ログ設定のデフォルト値
        defaultConfig.set("logging.level", "INFO")
        defaultConfig.set("logging.file-output", true)
        defaultConfig.set("logging.console-output", true)
        
        // API設定のデフォルト値
        defaultConfig.set("api.enabled", false)
        defaultConfig.set("api.base-url", "")
        defaultConfig.set("api.token", "")
        defaultConfig.set("api.timeout", 5000)
        
        // パフォーマンス設定のデフォルト値
        defaultConfig.set("performance.async-database", true)
        defaultConfig.set("performance.cache-player-data", true)
        defaultConfig.set("performance.world-cleanup", true)
        
        // デバッグ設定のデフォルト値
        defaultConfig.set("debug.enabled", false)
        defaultConfig.set("debug.show-coordinates", false)
        defaultConfig.set("debug.broadcast-events", false)
        
        return defaultConfig
    }
}