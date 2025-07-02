package com.hacklab.minecraft_legends

import com.hacklab.minecraft_legends.infrastructure.database.SQLiteDatabaseManager
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import com.hacklab.minecraft_legends.infrastructure.logger.LogConfig
import com.hacklab.minecraft_legends.infrastructure.repository.GameRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Minecraft_legends_simple : JavaPlugin() {
    
    private lateinit var customLogger: com.hacklab.minecraft_legends.infrastructure.logger.Logger
    private lateinit var databaseManager: SQLiteDatabaseManager
    private lateinit var gameRepository: GameRepositoryImpl
    
    override fun onEnable() {
        try {
            // Initialize logger first
            val logConfig = LogConfig()
            LoggerFactory.initialize(this, logConfig)
            customLogger = LoggerFactory.getLogger()
            
            customLogger.info("Battle Royale plugin starting...")
            
            // Initialize data folder
            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }
            
            // Initialize database
            val dbFile = File(dataFolder, "battle_royale.db")
            databaseManager = SQLiteDatabaseManager(dbFile)
            
            runBlocking {
                databaseManager.initialize()
            }
            
            // Initialize repositories
            gameRepository = GameRepositoryImpl(databaseManager)
            
            customLogger.info("Battle Royale plugin enabled successfully!")
            
        } catch (e: Exception) {
            logger.severe("Failed to enable Battle Royale plugin: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }
    
    override fun onDisable() {
        try {
            if (this::customLogger.isInitialized) {
                customLogger.info("Battle Royale plugin shutting down...")
            } else {
                logger.info("Battle Royale plugin shutting down...")
            }
            
            if (this::databaseManager.isInitialized) {
                runBlocking {
                    databaseManager.shutdown()
                }
            }
            
            if (this::customLogger.isInitialized) {
                customLogger.info("Battle Royale plugin disabled!")
            } else {
                logger.info("Battle Royale plugin disabled!")
            }
            
        } catch (e: Exception) {
            logger.severe("Error during shutdown: ${e.message}")
            e.printStackTrace()
        }
    }
}