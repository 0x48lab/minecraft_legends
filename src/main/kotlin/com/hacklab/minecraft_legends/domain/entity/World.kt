package com.hacklab.minecraft_legends.domain.entity

import org.bukkit.Location
import java.util.*

data class GameWorld(
    val id: UUID = UUID.randomUUID(),
    val worldName: String,
    val size: Int,
    val centerX: Int = 0,
    val centerZ: Int = 0,
    val respawnBeacons: List<RespawnBeacon> = emptyList(),
    val supplyBoxes: List<SupplyBox> = emptyList(),
    val spawnPoints: List<SpawnPoint> = emptyList(),
    val safeBounds: WorldBounds,
    val isGenerated: Boolean = false
) {
    val minX: Int get() = centerX - size / 2
    val maxX: Int get() = centerX + size / 2
    val minZ: Int get() = centerZ - size / 2
    val maxZ: Int get() = centerZ + size / 2
    
    fun isLocationInBounds(x: Int, z: Int): Boolean {
        return x in minX..maxX && z in minZ..maxZ
    }
    
    fun addRespawnBeacon(beacon: RespawnBeacon): GameWorld {
        return copy(respawnBeacons = respawnBeacons + beacon)
    }
    
    fun addSupplyBox(supplyBox: SupplyBox): GameWorld {
        return copy(supplyBoxes = supplyBoxes + supplyBox)
    }
    
    fun addSpawnPoint(spawnPoint: SpawnPoint): GameWorld {
        return copy(spawnPoints = spawnPoints + spawnPoint)
    }
    
    fun markAsGenerated(): GameWorld {
        return copy(isGenerated = true)
    }
}

data class RespawnBeacon(
    val id: UUID = UUID.randomUUID(),
    val x: Int,
    val y: Int,
    val z: Int,
    val isActive: Boolean = true,
    val isDestroyed: Boolean = false,
    val usageCount: Int = 0
) {
    fun use(): RespawnBeacon = copy(usageCount = usageCount + 1)
    fun destroy(): RespawnBeacon = copy(isDestroyed = true, isActive = false)
    fun activate(): RespawnBeacon = copy(isActive = true)
    fun deactivate(): RespawnBeacon = copy(isActive = false)
}


enum class LootRarity {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY
}

data class SpawnPoint(
    val id: UUID = UUID.randomUUID(),
    val x: Int,
    val y: Int,
    val z: Int,
    val teamId: UUID? = null,
    val isOccupied: Boolean = false
) {
    fun occupy(teamId: UUID): SpawnPoint = copy(teamId = teamId, isOccupied = true)
    fun release(): SpawnPoint = copy(teamId = null, isOccupied = false)
}

data class WorldBounds(
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int,
    val minY: Int = 0,
    val maxY: Int = 256
) {
    fun contains(x: Int, y: Int, z: Int): Boolean {
        return x in minX..maxX && y in minY..maxY && z in minZ..maxZ
    }
    
    fun shrink(newMinX: Int, newMaxX: Int, newMinZ: Int, newMaxZ: Int): WorldBounds {
        return copy(
            minX = maxOf(minX, newMinX),
            maxX = minOf(maxX, newMaxX),
            minZ = maxOf(minZ, newMinZ),
            maxZ = minOf(maxZ, newMaxZ)
        )
    }
    
    fun getCenter(): Pair<Int, Int> {
        return Pair((minX + maxX) / 2, (minZ + maxZ) / 2)
    }
    
    fun getSize(): Int {
        return maxOf(maxX - minX, maxZ - minZ)
    }
}

data class WorldGenerationSettings(
    val size: Int = 3000,
    val centerX: Int = 0,
    val centerZ: Int = 0,
    val respawnBeaconCount: Int = 6,
    val minBeaconDistance: Int = 200,
    val supplyBoxesPerBeacon: Int = 4,
    val supplyBoxSpawnRadius: Int = 20,
    val teamSpawnRadius: Int = 100,
    val worldType: WorldType = WorldType.NORMAL,
    val generateStructures: Boolean = true,
    val seed: Long? = null
)

enum class WorldType {
    NORMAL,
    FLAT,
    AMPLIFIED,
    LARGE_BIOMES
}