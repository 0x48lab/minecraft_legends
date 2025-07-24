package com.hacklab.minecraft_legends.domain.usecase

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.GameRepository
import com.hacklab.minecraft_legends.domain.repository.RingRepository
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlin.random.Random

interface ManageRingUseCase {
    suspend fun initializeRing(gameId: UUID): Result<Ring>
    suspend fun startNextPhase(gameId: UUID): Result<Ring>
    suspend fun updateRingPosition(gameId: UUID, progress: Double): Result<Ring>
    suspend fun completePhase(gameId: UUID): Result<Ring>
    suspend fun checkPlayerInRing(gameId: UUID, playerId: UUID, x: Double, z: Double): Result<RingCheckResult>
}

data class RingCheckResult(
    val isInside: Boolean,
    val damage: Double,
    val distanceFromEdge: Double,
    val timeToShrink: Long? = null
)

class ManageRingUseCaseImpl(
    private val ringRepository: RingRepository,
    private val gameRepository: GameRepository
) : ManageRingUseCase {
    
    private val logger = LoggerFactory.getLogger()
    
    override suspend fun initializeRing(gameId: UUID): Result<Ring> {
        return try {
            logger.info("Initializing ring for game: $gameId")
            
            val game = gameRepository.findById(gameId)
                ?: return Result.failure(Exception("Game not found"))
            
            val config = ringRepository.loadRingConfiguration()
            
            // 初期リングの中心を設定（マップ中央）
            val initialCenter = RingCenter(
                x = 0.0, // ワールド中央
                z = 0.0
            )
            
            val ring = Ring(
                gameId = gameId,
                currentPhase = 0,
                currentCenter = initialCenter,
                currentSize = config.initialSize,
                damage = 0.0
            )
            
            val savedRing = ringRepository.save(ring)
            logger.info("Ring initialized for game $gameId with size ${savedRing.currentSize}")
            
            Result.success(savedRing)
            
        } catch (e: Exception) {
            logger.error("Failed to initialize ring for game $gameId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun startNextPhase(gameId: UUID): Result<Ring> {
        return try {
            val currentRing = ringRepository.findByGameId(gameId)
                ?: return Result.failure(Exception("Ring not found for game $gameId"))
            
            val config = ringRepository.loadRingConfiguration()
            val nextPhase = currentRing.currentPhase + 1
            
            val phaseConfig = config.getPhaseConfig(nextPhase)
                ?: return Result.failure(Exception("No configuration for phase $nextPhase"))
            
            logger.info("Starting ring phase $nextPhase for game $gameId")
            
            // 次のリング中心をランダムに決定
            val nextCenter = calculateNextRingCenter(currentRing, phaseConfig)
            
            // 次のリングサイズを計算
            val nextSize = calculateNextRingSize(currentRing, phaseConfig, config)
            
            val updatedRing = currentRing.startPhase(
                phase = nextPhase,
                newCenter = nextCenter,
                newSize = nextSize,
                damage = phaseConfig.damage,
                warningTime = phaseConfig.waitTime,
                shrinkDuration = phaseConfig.shrinkTime
            )
            
            val savedRing = ringRepository.save(updatedRing)
            
            logger.info("Ring phase $nextPhase started: center=(${nextCenter.x}, ${nextCenter.z}), size=$nextSize")
            Result.success(savedRing)
            
        } catch (e: Exception) {
            logger.error("Failed to start next ring phase for game $gameId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun updateRingPosition(gameId: UUID, progress: Double): Result<Ring> {
        return try {
            val ring = ringRepository.findByGameId(gameId)
                ?: return Result.failure(Exception("Ring not found"))
            
            if (!ring.isMoving) {
                return Result.failure(Exception("Ring is not currently moving"))
            }
            
            val interpolatedPosition = ring.getCurrentInterpolatedPosition(progress)
                ?: return Result.failure(Exception("Cannot calculate ring position"))
            
            // データベースでリング位置を更新
            ringRepository.updatePosition(
                gameId = gameId,
                centerX = interpolatedPosition.center.x,
                centerZ = interpolatedPosition.center.z,
                size = interpolatedPosition.size
            )
            
            // 更新されたリングを取得
            val updatedRing = ringRepository.findByGameId(gameId)
                ?: return Result.failure(Exception("Failed to get updated ring"))
            
            Result.success(updatedRing)
            
        } catch (e: Exception) {
            logger.error("Failed to update ring position for game $gameId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun completePhase(gameId: UUID): Result<Ring> {
        return try {
            val ring = ringRepository.findByGameId(gameId)
                ?: return Result.failure(Exception("Ring not found"))
            
            val completedRing = ring.completeMovement()
            val savedRing = ringRepository.save(completedRing)
            
            logger.info("Ring phase ${completedRing.currentPhase} completed for game $gameId")
            Result.success(savedRing)
            
        } catch (e: Exception) {
            logger.error("Failed to complete ring phase for game $gameId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun checkPlayerInRing(
        gameId: UUID, 
        playerId: UUID, 
        x: Double, 
        z: Double
    ): Result<RingCheckResult> {
        return try {
            val ring = ringRepository.findByGameId(gameId)
                ?: return Result.failure(Exception("Ring not found"))
            
            val isInside = ring.isPlayerInside(x, z)
            val distanceFromEdge = ring.getDistanceFromEdge(x, z)
            val damage = if (isInside) 0.0 else ring.damage
            
            // 次の収縮までの時間を計算
            val timeToShrink = ring.phaseEndTime?.let { endTime ->
                val now = LocalDateTime.now()
                if (now.isBefore(endTime)) {
                    java.time.Duration.between(now, endTime).seconds
                } else null
            }
            
            val result = RingCheckResult(
                isInside = isInside,
                damage = damage,
                distanceFromEdge = distanceFromEdge,
                timeToShrink = timeToShrink
            )
            
            Result.success(result)
            
        } catch (e: Exception) {
            logger.error("Failed to check player ring status", e)
            Result.failure(e)
        }
    }
    
    private fun calculateNextRingCenter(ring: Ring, phaseConfig: RingPhaseConfig): RingCenter {
        // 現在のリング内でランダムに次の中心を決定
        val maxDistance = ring.currentSize * 0.3 // 現在のサイズの30%以内
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val distance = Random.nextDouble(0.0, maxDistance)
        
        val newX = ring.currentCenter.x + distance * kotlin.math.cos(angle)
        val newZ = ring.currentCenter.z + distance * kotlin.math.sin(angle)
        
        return RingCenter(newX, newZ)
    }
    
    private fun calculateNextRingSize(
        ring: Ring, 
        phaseConfig: RingPhaseConfig, 
        config: RingConfiguration
    ): Double {
        return phaseConfig.finalSize ?: run {
            // 段階的にサイズを縮小
            val shrinkFactor = when (phaseConfig.phase) {
                1 -> 0.7  // 70%に縮小
                2 -> 0.6  // 60%に縮小
                3 -> 0.5  // 50%に縮小
                4 -> 0.4  // 40%に縮小
                5 -> 0.3  // 30%に縮小
                6 -> 0.2  // 20%に縮小
                7 -> 0.0  // 完全縮小
                else -> 0.8
            }
            ring.currentSize * shrinkFactor
        }
    }
}