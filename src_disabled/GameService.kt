package com.hacklab.minecraft_legends.application.service

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.*
import com.hacklab.minecraft_legends.domain.usecase.*
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import com.hacklab.minecraft_legends.infrastructure.ring.RingManagerImpl
import com.hacklab.minecraft_legends.infrastructure.supplybox.SupplyBoxManager
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.LocalDateTime
import java.util.*

interface GameService {
    suspend fun createGame(settings: GameSettings): Result<Game>
    suspend fun startGame(gameId: UUID): Result<Game>
    suspend fun endGame(gameId: UUID, winnerTeamId: UUID? = null): Result<Game>
    suspend fun joinGame(player: Player): Result<Game?>
    suspend fun leaveGame(player: Player): Result<Boolean>
    suspend fun getCurrentGame(): Game?
    suspend fun isGameActive(): Boolean
}

class GameServiceImpl(
    private val plugin: Plugin,
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val worldRepository: WorldRepository,
    private val generateWorldUseCase: GenerateWorldUseCase,
    private val manageRingUseCase: ManageRingUseCase,
    private val manageSupplyBoxUseCase: ManageSupplyBoxUseCase,
    private val ringManager: RingManagerImpl,
    private val supplyBoxManager: SupplyBoxManager
) : GameService {
    
    private val logger = LoggerFactory.getLogger()
    private val gameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentGameId: UUID? = null
    
    override suspend fun createGame(settings: GameSettings): Result<Game> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info("Creating new game with settings: $settings")
            
            // 現在のゲームがアクティブかチェック
            if (isGameActive()) {
                return@withContext Result.failure(Exception("Game is already active"))
            }
            
            val gameId = UUID.randomUUID()
            
            // ワールドを生成
            val worldRequest = GenerateWorldRequest(
                gameId = gameId.toString(),
                settings = WorldGenerationSettings(
                    size = settings.worldSize,
                    respawnBeaconCount = settings.respawnBeaconCount
                )
            )
            
            val worldResult = generateWorldUseCase.execute(worldRequest)
            if (worldResult.isFailure) {
                return@withContext Result.failure(worldResult.exceptionOrNull() ?: Exception("World generation failed"))
            }
            
            val world = worldResult.getOrThrow()
            
            // ゲームエンティティを作成
            val game = Game(
                id = gameId,
                worldName = world.worldName,
                state = GameState.WAITING,
                teams = emptyList(),
                settings = settings,
                createdAt = LocalDateTime.now()
            )
            
            // ゲームを保存
            val savedGame = gameRepository.save(game)
            currentGameId = gameId
            
            // リングを初期化
            val ringResult = manageRingUseCase.initializeRing(gameId)
            if (ringResult.isFailure) {
                logger.error("Failed to initialize ring", ringResult.exceptionOrNull())
            }
            
            // サプライボックスを生成
            val supplyBoxResult = supplyBoxManager.spawnSupplyBoxes(gameId, world.worldName)
            if (supplyBoxResult.isFailure) {
                logger.error("Failed to spawn supply boxes", supplyBoxResult.exceptionOrNull())
            } else {
                logger.info("Spawned ${supplyBoxResult.getOrThrow().size} supply boxes")
            }
            
            logger.info("Game created successfully: $gameId")
            Result.success(savedGame)
            
        } catch (e: Exception) {
            logger.error("Failed to create game", e)
            Result.failure(e)
        }
    }
    
    override suspend fun startGame(gameId: UUID): Result<Game> = withContext(Dispatchers.IO) {
        return@withContext try {
            val game = gameRepository.findById(gameId)
                ?: return@withContext Result.failure(Exception("Game not found"))
            
            if (game.state != GameState.WAITING) {
                return@withContext Result.failure(Exception("Game is not in waiting state"))
            }
            
            logger.info("Starting game: $gameId")
            
            // ゲーム状態を更新
            val startedGame = game.start()
            val savedGame = gameRepository.save(startedGame)
            
            // プレイヤーをゲームワールドにテレポート
            teleportPlayersToGame(savedGame)
            
            // リング管理を開始
            ringManager.startRingManagement(gameId)
            
            // 最初のリングフェーズを開始（遅延あり）
            gameScope.launch {
                delay(10000) // 10秒後に開始
                ringManager.startRingPhase(gameId, 1)
            }
            
            logger.info("Game started successfully: $gameId")
            Result.success(savedGame)
            
        } catch (e: Exception) {
            logger.error("Failed to start game", e)
            Result.failure(e)
        }
    }
    
    override suspend fun endGame(gameId: UUID, winnerTeamId: UUID?): Result<Game> = withContext(Dispatchers.IO) {
        return@withContext try {
            val game = gameRepository.findById(gameId)
                ?: return@withContext Result.failure(Exception("Game not found"))
            
            logger.info("Ending game: $gameId, winner: $winnerTeamId")
            
            // ゲーム状態を更新
            val endedGame = game.finish(winnerTeamId)
            val savedGame = gameRepository.save(endedGame)
            
            // リング管理を停止
            ringManager.stopRingManagement(gameId)
            
            // サプライボックスをクリーンアップ
            val supplyBoxCleanup = supplyBoxManager.cleanupSupplyBoxes(gameId)
            if (supplyBoxCleanup.isFailure) {
                logger.error("Failed to cleanup supply boxes", supplyBoxCleanup.exceptionOrNull())
            }
            
            // プレイヤーをメインワールドに戻す
            teleportPlayersToLobby(savedGame)
            
            // 統計を更新
            updatePlayerStatistics(savedGame)
            
            // ワールドをクリーンアップ（遅延実行）
            gameScope.launch {
                delay(30000) // 30秒後にクリーンアップ
                cleanupGameWorld(savedGame.worldName)
            }
            
            currentGameId = null
            
            logger.info("Game ended successfully: $gameId")
            Result.success(savedGame)
            
        } catch (e: Exception) {
            logger.error("Failed to end game", e)
            Result.failure(e)
        }
    }
    
    override suspend fun joinGame(player: Player): Result<Game?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentGame = getCurrentGame()
            if (currentGame == null) {
                return@withContext Result.success(null)
            }
            
            if (currentGame.state != GameState.WAITING) {
                return@withContext Result.failure(Exception("Game is not accepting new players"))
            }
            
            // プレイヤーの総数をチェック
            val totalPlayers = currentGame.teams.sumOf { it.players.size }
            if (totalPlayers >= currentGame.settings.maxPlayers) {
                return@withContext Result.failure(Exception("Game is full"))
            }
            
            logger.info("Player ${player.name} joining game ${currentGame.id}")
            
            // プレイヤーをチームに配置
            val updatedGame = assignPlayerToTeam(currentGame, player)
            val savedGame = gameRepository.save(updatedGame)
            
            // 最小プレイヤー数に達したらゲーム開始
            val newTotalPlayers = savedGame.teams.sumOf { it.players.size }
            if (newTotalPlayers >= savedGame.settings.minPlayers) {
                // 自動開始（遅延あり）
                gameScope.launch {
                    delay(5000) // 5秒後に開始
                    startGame(savedGame.id)
                }
            }
            
            Result.success(savedGame)
            
        } catch (e: Exception) {
            logger.error("Failed to join game", e)
            Result.failure(e)
        }
    }
    
    override suspend fun leaveGame(player: Player): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentGame = getCurrentGame()
            if (currentGame == null) {
                return@withContext Result.success(false)
            }
            
            logger.info("Player ${player.name} leaving game ${currentGame.id}")
            
            // プレイヤーをゲームから削除
            val updatedGame = removePlayerFromGame(currentGame, player)
            gameRepository.save(updatedGame)
            
            // メインワールドにテレポート
            val mainWorld = Bukkit.getWorlds()[0]
            player.teleport(mainWorld.spawnLocation)
            
            Result.success(true)
            
        } catch (e: Exception) {
            logger.error("Failed to leave game", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentGame(): Game? = withContext(Dispatchers.IO) {
        return@withContext currentGameId?.let { gameId ->
            gameRepository.findById(gameId)
        }
    }
    
    override suspend fun isGameActive(): Boolean = withContext(Dispatchers.IO) {
        val game = getCurrentGame()
        return@withContext game?.isActive == true
    }
    
    private suspend fun teleportPlayersToGame(game: Game) = withContext(Dispatchers.Main) {
        val world = Bukkit.getWorld(game.worldName) ?: return@withContext
        
        game.teams.forEach { team ->
            team.players.forEach { gamePlayer ->
                val player = Bukkit.getPlayer(gamePlayer.id)
                if (player != null && player.isOnline) {
                    // スポーン地点にテレポート
                    val spawnLocation = world.spawnLocation
                    player.teleport(spawnLocation)
                    player.sendMessage("§aゲームが開始されました！")
                }
            }
        }
    }
    
    private suspend fun teleportPlayersToLobby(game: Game) = withContext(Dispatchers.Main) {
        val mainWorld = Bukkit.getWorlds()[0]
        
        game.teams.forEach { team ->
            team.players.forEach { gamePlayer ->
                val player = Bukkit.getPlayer(gamePlayer.id)
                if (player != null && player.isOnline) {
                    player.teleport(mainWorld.spawnLocation)
                    
                    if (team.id == game.winnerTeamId) {
                        player.sendTitle("§6🏆 CHAMPION! 🏆", "§eYou won the game!", 10, 100, 20)
                    } else {
                        player.sendMessage("§cGame ended. Better luck next time!")
                    }
                }
            }
        }
    }
    
    private suspend fun updatePlayerStatistics(game: Game) = withContext(Dispatchers.IO) {
        game.teams.forEach { team ->
            team.players.forEach { gamePlayer ->
                try {
                    val player = playerRepository.findById(gamePlayer.id)
                    if (player != null) {
                        val updatedStats = player.statistics
                            .addKill() // TODO: 実際のキル数を使用
                            .addDamage(gamePlayer.damage)
                            .apply {
                                if (team.id == game.winnerTeamId) {
                                    addWin()
                                } else {
                                    addDeath()
                                }
                            }
                        
                        val updatedPlayer = player.updateStatistics(updatedStats)
                        playerRepository.save(updatedPlayer)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to update statistics for player ${gamePlayer.id}", e)
                }
            }
        }
    }
    
    private suspend fun cleanupGameWorld(worldName: String) = withContext(Dispatchers.IO) {
        try {
            worldRepository.deleteWorld(worldName)
            logger.info("Game world cleaned up: $worldName")
        } catch (e: Exception) {
            logger.error("Failed to cleanup game world: $worldName", e)
        }
    }
    
    private fun assignPlayerToTeam(game: Game, player: Player): Game {
        // 既存のチームに配置するか、新しいチームを作成
        val availableTeam = game.teams.find { it.players.size < game.settings.teamSize }
        
        return if (availableTeam != null) {
            // 既存チームに追加
            val gamePlayer = GamePlayer(
                id = player.uniqueId,
                name = player.name,
                legend = "" // 後で選択
            )
            val updatedPlayers = availableTeam.players + gamePlayer
            val updatedTeam = availableTeam.copy(players = updatedPlayers)
            val updatedTeams = game.teams.map { if (it.id == updatedTeam.id) updatedTeam else it }
            game.copy(teams = updatedTeams)
        } else {
            // 新しいチームを作成
            val newTeam = GameTeam(
                id = UUID.randomUUID(),
                name = "Team ${game.teams.size + 1}",
                players = listOf(
                    GamePlayer(
                        id = player.uniqueId,
                        name = player.name,
                        legend = ""
                    )
                )
            )
            game.copy(teams = game.teams + newTeam)
        }
    }
    
    private fun removePlayerFromGame(game: Game, player: Player): Game {
        val updatedTeams = game.teams.map { team ->
            val updatedPlayers = team.players.filter { it.id != player.uniqueId }
            team.copy(players = updatedPlayers)
        }.filter { it.players.isNotEmpty() } // 空のチームを削除
        
        return game.copy(teams = updatedTeams)
    }
}