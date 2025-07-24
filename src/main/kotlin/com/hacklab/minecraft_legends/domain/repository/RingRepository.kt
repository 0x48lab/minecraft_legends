package com.hacklab.minecraft_legends.domain.repository

import com.hacklab.minecraft_legends.domain.entity.Ring
import com.hacklab.minecraft_legends.domain.entity.RingConfiguration
import java.util.*

interface RingRepository {
    suspend fun findByGameId(gameId: UUID): Ring?
    suspend fun save(ring: Ring): Ring
    suspend fun delete(gameId: UUID): Boolean
    suspend fun updatePosition(gameId: UUID, centerX: Double, centerZ: Double, size: Double): Boolean
    suspend fun updateDamage(gameId: UUID, damage: Double): Boolean
    suspend fun setMovingStatus(gameId: UUID, isMoving: Boolean): Boolean
    
    // リング設定管理
    suspend fun loadRingConfiguration(): RingConfiguration
    suspend fun saveRingConfiguration(config: RingConfiguration): Boolean
    
    // 統計・履歴
    suspend fun getRingHistory(gameId: UUID): List<Ring>
    suspend fun getAverageGameDuration(): Double
    suspend fun getPhaseStatistics(): Map<Int, PhaseStatistics>
}

data class PhaseStatistics(
    val phase: Int,
    val averageDuration: Double,
    val playerEliminationRate: Double,
    val averagePlayersRemaining: Double
)