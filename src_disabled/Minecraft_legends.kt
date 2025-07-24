package com.hacklab.minecraft_legends

import com.hacklab.minecraft_legends.infrastructure.config.ConfigurationManager
import com.hacklab.minecraft_legends.infrastructure.config.ConfigurationManagerImpl
import com.hacklab.minecraft_legends.infrastructure.database.DatabaseManager
import com.hacklab.minecraft_legends.infrastructure.database.PlayerRepositoryImpl
import com.hacklab.minecraft_legends.infrastructure.database.SQLiteDatabaseManager
import com.hacklab.minecraft_legends.infrastructure.logger.LogConfig
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import com.hacklab.minecraft_legends.infrastructure.world.WorldManagerImpl
import com.hacklab.minecraft_legends.infrastructure.repository.SupplyBoxRepositoryImpl
import com.hacklab.minecraft_legends.infrastructure.supplybox.SupplyBoxManager
import com.hacklab.minecraft_legends.infrastructure.i18n.MessageManager
import com.hacklab.minecraft_legends.infrastructure.i18n.MessageManagerImpl
import com.hacklab.minecraft_legends.presentation.command.CommandManager
import com.hacklab.minecraft_legends.presentation.command.BRCommand
import com.hacklab.minecraft_legends.presentation.command.BRAdminCommand
import com.hacklab.minecraft_legends.presentation.gui.GUIManager
import com.hacklab.minecraft_legends.presentation.listener.GameEventListener
import com.hacklab.minecraft_legends.presentation.listener.RespawnBeaconListener
import com.hacklab.minecraft_legends.infrastructure.legend.LegendAbilityManager
import com.hacklab.minecraft_legends.application.service.GameService
import com.hacklab.minecraft_legends.application.service.GameServiceImpl
import com.hacklab.minecraft_legends.domain.repository.*
import com.hacklab.minecraft_legends.domain.usecase.*
import com.hacklab.minecraft_legends.infrastructure.ring.RingManagerImpl
import com.hacklab.minecraft_legends.infrastructure.repository.TeamRepositoryImpl
import com.hacklab.minecraft_legends.infrastructure.repository.GameRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

class Minecraft_legends : JavaPlugin() {
    
    // DI コンテナ的な役割
    private lateinit var configurationManager: ConfigurationManager
    private lateinit var databaseManager: DatabaseManager
    private lateinit var playerRepository: PlayerRepository
    private lateinit var worldRepository: WorldRepository
    private lateinit var supplyBoxRepository: SupplyBoxRepository
    private lateinit var teamRepository: TeamRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var ringRepository: RingRepository
    private lateinit var generateWorldUseCase: GenerateWorldUseCase
    private lateinit var manageSupplyBoxUseCase: ManageSupplyBoxUseCase
    private lateinit var manageRingUseCase: ManageRingUseCase
    private lateinit var supplyBoxManager: SupplyBoxManager
    private lateinit var ringManager: RingManagerImpl
    private lateinit var gameService: GameService
    private lateinit var messageManager: MessageManager
    private lateinit var commandManager: CommandManager
    private lateinit var guiManager: GUIManager
    private lateinit var legendAbilityManager: LegendAbilityManager
    private lateinit var gameEventListener: GameEventListener
    private lateinit var respawnBeaconListener: RespawnBeaconListener
    
    // コルーチンスコープ
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onEnable() {
        try {
            logger.info("Minecraft Legends プラグインを開始しています...")
            
            // 1. 設定管理を初期化
            initializeConfiguration()
            
            // 2. ログシステムを初期化
            initializeLogger()
            
            // 3. データベースを初期化
            initializeDatabase()
            
            // 4. リポジトリを初期化
            initializeRepositories()
            
            // 5. ユースケースを初期化
            initializeUseCases()
            
            // 6. サービスを初期化
            initializeServices()
            
            // 7. マネージャーを初期化
            initializeManagers()
            
            // 8. イベントリスナーを登録
            registerEventListeners()
            
            // 9. コマンドを登録
            registerCommands()
            
            val logger = LoggerFactory.getLogger()
            logger.info("Minecraft Legends プラグインが正常に開始されました！")
            
        } catch (e: Exception) {
            logger.severe("プラグインの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }
    
    override fun onDisable() {
        try {
            val logger = LoggerFactory.getLogger()
            logger.info("Minecraft Legends プラグインを停止しています...")
            
            // マネージャーを停止
            if (::supplyBoxManager.isInitialized) {
                supplyBoxManager.shutdown()
            }
            if (::commandManager.isInitialized) {
                commandManager.unregisterAll()
            }
            if (::guiManager.isInitialized) {
                guiManager.shutdown()
            }
            if (::legendAbilityManager.isInitialized) {
                legendAbilityManager.cleanup()
            }
            
            // データベース接続を閉じる
            pluginScope.launch {
                try {
                    databaseManager.shutdown()
                    logger.info("データベース接続を正常に閉じました")
                } catch (e: Exception) {
                    logger.error("データベースの停止中にエラーが発生しました", e)
                }
            }
            
            logger.info("Minecraft Legends プラグインが正常に停止されました")
            
        } catch (e: Exception) {
            logger.severe("プラグインの停止中にエラーが発生しました: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun initializeConfiguration() {
        configurationManager = ConfigurationManagerImpl(this)
        configurationManager.loadConfig()
        logger.info("設定ファイルを読み込みました")
    }
    
    private fun initializeLogger() {
        val logConfig = LogConfig(
            level = configurationManager.getLogLevel(),
            fileOutput = true,
            consoleOutput = true
        )
        LoggerFactory.initialize(this, logConfig)
        val logger = LoggerFactory.getLogger()
        logger.info("ログシステムを初期化しました")
    }
    
    private fun initializeDatabase() {
        val databaseFile = configurationManager.getDatabaseFile()
        databaseManager = SQLiteDatabaseManager(databaseFile)
        
        // 非同期でデータベースを初期化
        pluginScope.launch {
            try {
                databaseManager.initialize()
                LoggerFactory.getLogger().info("データベースを初期化しました")
            } catch (e: Exception) {
                LoggerFactory.getLogger().error("データベースの初期化に失敗しました", e)
                throw e
            }
        }
    }
    
    private fun initializeRepositories() {
        playerRepository = PlayerRepositoryImpl(databaseManager)
        worldRepository = WorldManagerImpl(this)
        supplyBoxRepository = SupplyBoxRepositoryImpl(this, databaseManager)
        teamRepository = TeamRepositoryImpl(databaseManager)
        gameRepository = GameRepositoryImpl(databaseManager)
        // Initialize ring manager as repository
        ringManager = RingManagerImpl(this, null)
        ringRepository = ringManager
        
        val logger = LoggerFactory.getLogger()
        logger.info("リポジトリを初期化しました")
    }
    
    private fun initializeUseCases() {
        generateWorldUseCase = GenerateWorldUseCaseImpl(worldRepository)
        manageSupplyBoxUseCase = ManageSupplyBoxUseCaseImpl(supplyBoxRepository, worldRepository)
        manageRingUseCase = ManageRingUseCaseImpl(ringRepository, gameRepository)
        
        val logger = LoggerFactory.getLogger()
        logger.info("ユースケースを初期化しました")
    }
    
    private fun initializeServices() {
        // Services will be initialized after managers
        val logger = LoggerFactory.getLogger()
        logger.info("サービスの初期化を準備しました")
    }
    
    private fun initializeManagers() {
        supplyBoxManager = SupplyBoxManager(this, manageSupplyBoxUseCase)
        messageManager = MessageManagerImpl(this)
        commandManager = CommandManager(this)
        guiManager = GUIManager(this, messageManager)
        
        // Now initialize GameService after supplyBoxManager is ready
        gameService = GameServiceImpl(
            plugin = this,
            gameRepository = gameRepository,
            playerRepository = playerRepository,
            teamRepository = teamRepository,
            worldRepository = worldRepository,
            generateWorldUseCase = generateWorldUseCase,
            manageRingUseCase = manageRingUseCase,
            manageSupplyBoxUseCase = manageSupplyBoxUseCase,
            ringManager = ringManager,
            supplyBoxManager = supplyBoxManager
        )
        
        // Initialize ability manager
        legendAbilityManager = LegendAbilityManager(this, gameService, messageManager, pluginScope)
        
        val logger = LoggerFactory.getLogger()
        logger.info("マネージャーを初期化しました")
    }
    
    private fun registerEventListeners() {
        // Register game event listeners
        gameEventListener = GameEventListener(this, gameService, messageManager, pluginScope)
        server.pluginManager.registerEvents(gameEventListener, this)
        
        // Register respawn beacon listener  
        respawnBeaconListener = RespawnBeaconListener(this, gameService, messageManager, pluginScope)
        server.pluginManager.registerEvents(respawnBeaconListener, this)
        
        val logger = LoggerFactory.getLogger()
        logger.info("イベントリスナーを登録しました")
    }
    
    private fun registerCommands() {
        // Admin command
        val adminCommand = BRAdminCommand(gameService, configurationManager, pluginScope)
        commandManager.registerCommand(adminCommand)
        
        // Player command
        val playerCommand = BRCommand(gameService, playerRepository, messageManager, guiManager, pluginScope)
        commandManager.registerCommand(playerCommand)
        
        val logger = LoggerFactory.getLogger()
        logger.info("コマンドを登録しました")
    }
    
    // 他のクラスからアクセスできるようにゲッターを提供
    fun getConfigurationManager(): ConfigurationManager = configurationManager
    fun getDatabaseManager(): DatabaseManager = databaseManager
    fun getPlayerRepository(): PlayerRepository = playerRepository
    fun getWorldRepository(): WorldRepository = worldRepository
    fun getSupplyBoxRepository(): SupplyBoxRepository = supplyBoxRepository
    fun getTeamRepository(): TeamRepository = teamRepository
    fun getGameRepository(): GameRepository = gameRepository
    fun getRingRepository(): RingRepository = ringRepository
    fun getGenerateWorldUseCase(): GenerateWorldUseCase = generateWorldUseCase
    fun getManageSupplyBoxUseCase(): ManageSupplyBoxUseCase = manageSupplyBoxUseCase
    fun getManageRingUseCase(): ManageRingUseCase = manageRingUseCase
    fun getSupplyBoxManager(): SupplyBoxManager = supplyBoxManager
    fun getRingManager(): RingManagerImpl = ringManager
    fun getGameService(): GameService = gameService
    fun getMessageManager(): MessageManager = messageManager
    fun getCommandManager(): CommandManager = commandManager
    fun getPluginScope(): CoroutineScope = pluginScope
}