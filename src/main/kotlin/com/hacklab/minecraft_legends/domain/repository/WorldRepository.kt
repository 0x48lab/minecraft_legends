package com.hacklab.minecraft_legends.domain.repository

import com.hacklab.minecraft_legends.domain.entity.GameWorld
import com.hacklab.minecraft_legends.domain.entity.RespawnBeacon
import com.hacklab.minecraft_legends.domain.entity.SupplyBox
import com.hacklab.minecraft_legends.domain.entity.WorldGenerationSettings
import java.util.*

interface WorldRepository {
    suspend fun findById(id: UUID): GameWorld?
    suspend fun findByWorldName(worldName: String): GameWorld?
    suspend fun save(world: GameWorld): GameWorld
    suspend fun delete(id: UUID): Boolean
    suspend fun deleteByWorldName(worldName: String): Boolean
    suspend fun findAll(): List<GameWorld>
    
    // ワールド生成管理
    suspend fun createWorld(settings: WorldGenerationSettings): GameWorld
    suspend fun generateWorld(world: GameWorld): Boolean
    suspend fun deleteWorld(worldName: String): Boolean
    suspend fun isWorldGenerated(worldName: String): Boolean
    suspend fun loadWorld(worldName: String): Boolean
    suspend fun unloadWorld(worldName: String): Boolean
    
    // リスポーンビーコン管理
    suspend fun saveRespawnBeacon(beacon: RespawnBeacon): Boolean
    suspend fun findRespawnBeaconById(id: UUID): RespawnBeacon?
    suspend fun findRespawnBeaconsByWorld(worldId: UUID): List<RespawnBeacon>
    suspend fun updateRespawnBeaconStatus(beaconId: UUID, isActive: Boolean): Boolean
    suspend fun destroyRespawnBeacon(beaconId: UUID): Boolean
    
    // サプライボックス管理
    suspend fun saveSupplyBox(supplyBox: SupplyBox): Boolean
    suspend fun findSupplyBoxById(id: UUID): SupplyBox?
    suspend fun findSupplyBoxesByWorld(worldId: UUID): List<SupplyBox>
    suspend fun markSupplyBoxAsLooted(supplyBoxId: UUID): Boolean
    suspend fun resetSupplyBoxes(worldId: UUID): Boolean
    
    // 座標・位置管理
    suspend fun findNearestRespawnBeacon(worldId: UUID, x: Int, z: Int): RespawnBeacon?
    suspend fun findSupplyBoxesInRadius(worldId: UUID, x: Int, z: Int, radius: Int): List<SupplyBox>
    suspend fun isLocationSafe(worldId: UUID, x: Int, y: Int, z: Int): Boolean
    
    // クリーンアップ
    suspend fun cleanupOldWorlds(maxAge: Long): Int
    suspend fun getWorldSize(worldName: String): Long
}