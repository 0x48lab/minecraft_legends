package com.hacklab.minecraft_legends.infrastructure.ring

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.RingRepository
import com.hacklab.minecraft_legends.domain.repository.PhaseStatistics
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RingManagerImpl(
    private val plugin: Plugin,
    private val ringRepository: RingRepository?
) : RingRepository {
    
    private val logger = LoggerFactory.getLogger()
    private val rings = ConcurrentHashMap<UUID, Ring>()
    private val ringTasks = ConcurrentHashMap<UUID, BukkitTask>()
    private val damageScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Repository methods implementation
    override suspend fun findByGameId(gameId: UUID): Ring? = withContext(Dispatchers.IO) {
        rings[gameId]
    }
    
    override suspend fun save(ring: Ring): Ring = withContext(Dispatchers.IO) {
        rings[ring.gameId] = ring
        ring
    }
    
    override suspend fun delete(gameId: UUID): Boolean = withContext(Dispatchers.IO) {
        rings.remove(gameId) != null
    }
    
    override suspend fun updatePosition(gameId: UUID, centerX: Double, centerZ: Double, size: Double): Boolean = withContext(Dispatchers.IO) {
        val ring = rings[gameId] ?: return@withContext false
        val updatedRing = ring.copy(
            currentCenter = RingCenter(centerX, centerZ),
            currentSize = size
        )
        rings[gameId] = updatedRing
        
        // Bukkit WorldBorderを更新
        updateWorldBorder(gameId, updatedRing)
        true
    }
    
    override suspend fun updateDamage(gameId: UUID, damage: Double): Boolean = withContext(Dispatchers.IO) {
        val ring = rings[gameId] ?: return@withContext false
        rings[gameId] = ring.copy(damage = damage)
        true
    }
    
    override suspend fun setMovingStatus(gameId: UUID, isMoving: Boolean): Boolean = withContext(Dispatchers.IO) {
        val ring = rings[gameId] ?: return@withContext false
        rings[gameId] = ring.copy(isMoving = isMoving)
        true
    }
    
    override suspend fun loadRingConfiguration(): RingConfiguration = withContext(Dispatchers.IO) {
        // デフォルト設定を返す（実際はファイルから読み込み）
        RingConfiguration(
            phases = listOf(
                RingPhaseConfig(1, waitTime = 180, shrinkTime = 120, damage = 2.0),
                RingPhaseConfig(2, waitTime = 90, shrinkTime = 90, damage = 3.0),
                RingPhaseConfig(3, waitTime = 90, shrinkTime = 90, damage = 5.0),
                RingPhaseConfig(4, waitTime = 80, shrinkTime = 60, damage = 10.0),
                RingPhaseConfig(5, waitTime = 80, shrinkTime = 40, damage = 15.0),
                RingPhaseConfig(6, waitTime = 60, shrinkTime = 30, damage = 20.0),
                RingPhaseConfig(7, waitTime = 30, shrinkTime = 120, damage = 50.0, finalSize = 0.0)
            ),
            updateInterval = 3,
            initialSize = 2000.0,
            finalSize = 0.0
        )
    }
    
    override suspend fun saveRingConfiguration(config: RingConfiguration): Boolean = withContext(Dispatchers.IO) {
        // TODO: ファイルに保存
        true
    }
    
    override suspend fun getRingHistory(gameId: UUID): List<Ring> = withContext(Dispatchers.IO) {
        // TODO: 履歴を実装
        emptyList()
    }
    
    override suspend fun getAverageGameDuration(): Double = withContext(Dispatchers.IO) {
        // TODO: 統計を実装
        0.0
    }
    
    override suspend fun getPhaseStatistics(): Map<Int, PhaseStatistics> = withContext(Dispatchers.IO) {
        // TODO: 統計を実装
        emptyMap()
    }
    
    // Ring management methods
    suspend fun startRingManagement(gameId: UUID) = withContext(Dispatchers.Main) {
        logger.info("Starting ring management for game: $gameId")
        
        val ring = rings[gameId] ?: run {
            logger.error("Ring not found for game: $gameId")
            return@withContext
        }
        
        // リング収縮タスクを開始
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            damageScope.launch {
                processRingDamage(gameId)
            }
        }, 0L, 20L) // 1秒ごと
        
        ringTasks[gameId] = task
    }
    
    suspend fun stopRingManagement(gameId: UUID) = withContext(Dispatchers.Main) {
        logger.info("Stopping ring management for game: $gameId")
        
        ringTasks[gameId]?.cancel()
        ringTasks.remove(gameId)
        rings.remove(gameId)
    }
    
    suspend fun startRingPhase(gameId: UUID, phase: Int) = withContext(Dispatchers.Main) {
        val ring = rings[gameId] ?: return@withContext
        val config = loadRingConfiguration()
        val phaseConfig = config.getPhaseConfig(phase) ?: return@withContext
        
        logger.info("Starting ring phase $phase for game $gameId")
        
        // 警告フェーズ
        broadcastRingWarning(gameId, phaseConfig.waitTime)
        
        // 警告時間後に収縮開始
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            damageScope.launch {
                startRingShrinking(gameId, phase)
            }
        }, phaseConfig.waitTime * 20L) // tickに変換
    }
    
    private suspend fun startRingShrinking(gameId: UUID, phase: Int) = withContext(Dispatchers.Main) {
        val ring = rings[gameId] ?: return@withContext
        val config = loadRingConfiguration()
        val phaseConfig = config.getPhaseConfig(phase) ?: return@withContext
        
        logger.info("Ring shrinking started for phase $phase, game $gameId")
        
        // リングの移動開始
        setMovingStatus(gameId, true)
        broadcastRingShrinking(gameId)
        
        // 段階的に収縮
        val totalSteps = (phaseConfig.shrinkTime / config.updateInterval).toInt()
        var currentStep = 0
        
        val shrinkTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            damageScope.launch {
                currentStep++
                val progress = currentStep.toDouble() / totalSteps
                
                if (progress >= 1.0) {
                    // 収縮完了
                    completeRingShrinking(gameId)
                    ringTasks[gameId]?.cancel()
                } else {
                    // 位置を更新
                    updatePosition(gameId, 0.0, 0.0, 0.0) // Placeholder
                }
            }
        }, 0L, config.updateInterval * 20L)
        
        ringTasks[gameId] = shrinkTask
    }
    
    private suspend fun completeRingShrinking(gameId: UUID) = withContext(Dispatchers.Main) {
        val ring = rings[gameId] ?: return@withContext
        
        setMovingStatus(gameId, false)
        val completedRing = ring.completeMovement()
        rings[gameId] = completedRing
        
        updateWorldBorder(gameId, completedRing)
        
        logger.info("Ring shrinking completed for phase ${completedRing.currentPhase}, game $gameId")
        
        // 次のフェーズがあるかチェック
        val config = loadRingConfiguration()
        val nextPhase = completedRing.currentPhase + 1
        if (config.getPhaseConfig(nextPhase) != null) {
            // 次のフェーズを開始
            startRingPhase(gameId, nextPhase)
        } else {
            logger.info("All ring phases completed for game $gameId")
        }
    }
    
    private suspend fun processRingDamage(gameId: UUID) = withContext(Dispatchers.Main) {
        val ring = rings[gameId] ?: return@withContext
        if (ring.damage <= 0) return@withContext
        
        val world = getGameWorld(gameId) ?: return@withContext
        
        world.players.forEach { player ->
            val playerX = player.location.x
            val playerZ = player.location.z
            
            if (!ring.isPlayerInside(playerX, playerZ)) {
                // プレイヤーがリング外にいる場合ダメージを与える
                val currentHealth = player.health
                val newHealth = maxOf(0.0, currentHealth - ring.damage)
                player.health = newHealth
                
                // ダメージエフェクト
                player.sendTitle("§c⚠ RING DAMAGE ⚠", "§fMove to the safe zone!", 0, 20, 0)
                
                if (newHealth <= 0) {
                    // プレイヤーを倒す
                    player.sendMessage("§cYou were eliminated by the ring!")
                    // TODO: プレイヤー除去処理
                }
            }
        }
    }
    
    private fun updateWorldBorder(gameId: UUID, ring: Ring) {
        val world = getGameWorld(gameId) ?: return
        
        val worldBorder = world.worldBorder
        worldBorder.setCenter(ring.currentCenter.x, ring.currentCenter.z)
        worldBorder.size = ring.currentSize
        worldBorder.damageAmount = ring.damage
    }
    
    private fun broadcastRingWarning(gameId: UUID, seconds: Long) {
        val world = getGameWorld(gameId) ?: return
        
        world.players.forEach { player ->
            player.sendTitle(
                "§e⚠ RING WARNING ⚠",
                "§fRing closing in §e${seconds}§f seconds!",
                10, 60, 10
            )
        }
        
        logger.info("Ring warning broadcasted for game $gameId: $seconds seconds")
    }
    
    private fun broadcastRingShrinking(gameId: UUID) {
        val world = getGameWorld(gameId) ?: return
        
        world.players.forEach { player ->
            player.sendTitle(
                "§c⚠ RING MOVING ⚠",
                "§fGet to the safe zone!",
                10, 40, 10
            )
        }
        
        logger.info("Ring shrinking notification broadcasted for game $gameId")
    }
    
    private fun getGameWorld(gameId: UUID): org.bukkit.World? {
        // ゲームIDに対応するワールドを取得
        val worldName = "br_world_$gameId"
        return Bukkit.getWorld(worldName)
    }
}