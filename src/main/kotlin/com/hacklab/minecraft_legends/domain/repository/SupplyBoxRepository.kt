package com.hacklab.minecraft_legends.domain.repository

import com.hacklab.minecraft_legends.domain.entity.*
import java.util.*

interface SupplyBoxRepository {
    suspend fun findById(id: UUID): SupplyBox?
    suspend fun findByGameId(gameId: UUID): List<SupplyBox>
    suspend fun findAvailableByGameId(gameId: UUID): List<SupplyBox>
    suspend fun save(supplyBox: SupplyBox): SupplyBox
    suspend fun deleteByGameId(gameId: UUID): Boolean
    suspend fun markAsOpened(id: UUID, playerId: UUID): Boolean
    suspend fun markAsDestroyed(id: UUID): Boolean
    
    // Configuration management
    suspend fun loadSupplyBoxConfiguration(): SupplyBoxConfiguration
    suspend fun saveSupplyBoxConfiguration(config: SupplyBoxConfiguration): Boolean
    suspend fun loadLootTable(tableName: String): LootTable?
    suspend fun saveLootTable(table: LootTable): Boolean
    
    // Statistics
    suspend fun getSupplyBoxStatistics(gameId: UUID): SupplyBoxStatistics
    suspend fun getPlayerSupplyBoxHistory(playerId: UUID): List<SupplyBoxInteraction>
}

data class SupplyBoxStatistics(
    val totalSpawned: Int,
    val totalOpened: Int,
    val openedByTier: Map<SupplyBoxTier, Int>,
    val averageItemsPerBox: Double,
    val mostCommonItems: List<Pair<String, Int>>
)

data class SupplyBoxInteraction(
    val supplyBoxId: UUID,
    val playerId: UUID,
    val tier: SupplyBoxTier,
    val itemsObtained: List<String>,
    val timestamp: java.time.LocalDateTime
)