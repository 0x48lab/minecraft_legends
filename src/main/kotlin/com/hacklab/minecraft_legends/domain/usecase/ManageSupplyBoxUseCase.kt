package com.hacklab.minecraft_legends.domain.usecase

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.SupplyBoxRepository
import com.hacklab.minecraft_legends.domain.repository.WorldRepository
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import org.bukkit.Location
import java.util.*
import kotlin.random.Random

interface ManageSupplyBoxUseCase {
    suspend fun generateSupplyBoxes(gameId: UUID, worldName: String): Result<List<SupplyBox>>
    suspend fun openSupplyBox(supplyBoxId: UUID, playerId: UUID): Result<SupplyBoxOpenResult>
    suspend fun getAvailableSupplyBoxes(gameId: UUID): Result<List<SupplyBox>>
    suspend fun cleanupSupplyBoxes(gameId: UUID): Result<Boolean>
    suspend fun getSupplyBoxNearLocation(gameId: UUID, location: Location, radius: Double): Result<List<SupplyBox>>
}

data class SupplyBoxOpenResult(
    val supplyBox: SupplyBox,
    val items: List<LootItem>,
    val success: Boolean,
    val message: String
)

class ManageSupplyBoxUseCaseImpl(
    private val supplyBoxRepository: SupplyBoxRepository,
    private val worldRepository: WorldRepository
) : ManageSupplyBoxUseCase {
    
    private val logger = LoggerFactory.getLogger()
    
    override suspend fun generateSupplyBoxes(gameId: UUID, worldName: String): Result<List<SupplyBox>> {
        return try {
            logger.info("Generating supply boxes for game: $gameId in world: $worldName")
            
            val config = supplyBoxRepository.loadSupplyBoxConfiguration()
            val lootTable = supplyBoxRepository.loadLootTable("default")
                ?: return Result.failure(Exception("Default loot table not found"))
            
            val world = worldRepository.findByWorldName(worldName)
                ?: return Result.failure(Exception("World not found: $worldName"))
            
            val supplyBoxes = mutableListOf<SupplyBox>()
            
            // Generate supply boxes based on configuration
            repeat(config.spawnCount) {
                val tier = selectSupplyBoxTier(config.tierDistribution)
                val location = generateSupplyBoxLocation(world, supplyBoxes)
                val itemCount = config.itemsPerBox[tier]?.random() ?: 3
                val contents = lootTable.generateLoot(itemCount)
                
                val supplyBox = SupplyBox(
                    gameId = gameId,
                    location = BoxLocation(
                        x = location.x,
                        y = location.y,
                        z = location.z,
                        worldName = worldName
                    ),
                    tier = tier,
                    contents = contents
                )
                
                val savedBox = supplyBoxRepository.save(supplyBox)
                supplyBoxes.add(savedBox)
                
                // Spawn physical chest in world
                spawnPhysicalChest(location, tier)
            }
            
            logger.info("Generated ${supplyBoxes.size} supply boxes for game $gameId")
            Result.success(supplyBoxes)
            
        } catch (e: Exception) {
            logger.error("Failed to generate supply boxes for game $gameId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun openSupplyBox(supplyBoxId: UUID, playerId: UUID): Result<SupplyBoxOpenResult> {
        return try {
            val supplyBox = supplyBoxRepository.findById(supplyBoxId)
                ?: return Result.failure(Exception("Supply box not found"))
            
            if (supplyBox.status != SupplyBoxStatus.AVAILABLE) {
                return Result.success(
                    SupplyBoxOpenResult(
                        supplyBox = supplyBox,
                        items = emptyList(),
                        success = false,
                        message = "Supply box is not available"
                    )
                )
            }
            
            // Mark as opened
            val openedBox = supplyBox.open(playerId)
            supplyBoxRepository.save(openedBox)
            
            logger.info("Player $playerId opened supply box $supplyBoxId (${supplyBox.tier})")
            
            Result.success(
                SupplyBoxOpenResult(
                    supplyBox = openedBox,
                    items = supplyBox.contents,
                    success = true,
                    message = "Supply box opened successfully"
                )
            )
            
        } catch (e: Exception) {
            logger.error("Failed to open supply box $supplyBoxId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAvailableSupplyBoxes(gameId: UUID): Result<List<SupplyBox>> {
        return try {
            val availableBoxes = supplyBoxRepository.findAvailableByGameId(gameId)
            Result.success(availableBoxes)
        } catch (e: Exception) {
            logger.error("Failed to get available supply boxes for game $gameId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun cleanupSupplyBoxes(gameId: UUID): Result<Boolean> {
        return try {
            val deleted = supplyBoxRepository.deleteByGameId(gameId)
            
            // Remove physical chests from world
            val supplyBoxes = supplyBoxRepository.findByGameId(gameId)
            supplyBoxes.forEach { box ->
                removePhysicalChest(box.location)
            }
            
            logger.info("Cleaned up supply boxes for game $gameId")
            Result.success(deleted)
            
        } catch (e: Exception) {
            logger.error("Failed to cleanup supply boxes for game $gameId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getSupplyBoxNearLocation(
        gameId: UUID, 
        location: Location, 
        radius: Double
    ): Result<List<SupplyBox>> {
        return try {
            val allBoxes = supplyBoxRepository.findByGameId(gameId)
            val nearbyBoxes = allBoxes.filter { box ->
                val boxLocation = box.location.toLocation()
                boxLocation != null && boxLocation.distance(location) <= radius
            }
            
            Result.success(nearbyBoxes)
            
        } catch (e: Exception) {
            logger.error("Failed to get supply boxes near location", e)
            Result.failure(e)
        }
    }
    
    private fun selectSupplyBoxTier(distribution: Map<SupplyBoxTier, Double>): SupplyBoxTier {
        val totalWeight = distribution.values.sum()
        val randomValue = Random.nextDouble(0.0, totalWeight)
        var currentWeight = 0.0
        
        for ((tier, weight) in distribution) {
            currentWeight += weight
            if (randomValue <= currentWeight) {
                return tier
            }
        }
        
        return SupplyBoxTier.BASIC
    }
    
    private fun generateSupplyBoxLocation(world: GameWorld, existingBoxes: List<SupplyBox>): Location {
        val worldSize = 3000.0 // Default world size
        val minDistance = 50.0 // Minimum distance between supply boxes
        
        var attempts = 0
        val maxAttempts = 100
        
        while (attempts < maxAttempts) {
            val x = Random.nextDouble(-worldSize / 2.0, worldSize / 2.0)
            val z = Random.nextDouble(-worldSize / 2.0, worldSize / 2.0)
            val y = findSafeY(world.worldName, x, z)
            
            val candidateLocation = Location(
                org.bukkit.Bukkit.getWorld(world.worldName),
                x, y, z
            )
            
            // Check distance from existing boxes
            val tooClose = existingBoxes.any { box ->
                val boxLocation = box.location.toLocation()
                boxLocation != null && boxLocation.distance(candidateLocation) < minDistance
            }
            
            if (!tooClose) {
                return candidateLocation
            }
            
            attempts++
        }
        
        // Fallback: return a random location even if it's close to others
        val x = Random.nextDouble(-worldSize / 2.0, worldSize / 2.0)
        val z = Random.nextDouble(-worldSize / 2.0, worldSize / 2.0)
        val y = findSafeY(world.worldName, x, z)
        
        return Location(org.bukkit.Bukkit.getWorld(world.worldName), x, y, z)
    }
    
    private fun findSafeY(worldName: String, x: Double, z: Double): Double {
        // Find highest solid block
        val bukkitWorld = org.bukkit.Bukkit.getWorld(worldName) ?: return 64.0
        val highestY = bukkitWorld.getHighestBlockYAt(x.toInt(), z.toInt())
        return highestY + 1.0 // Place chest on top of the highest block
    }
    
    private fun spawnPhysicalChest(location: Location, tier: SupplyBoxTier) {
        val world = location.world ?: return
        
        // Place chest block
        val block = world.getBlockAt(location)
        block.type = org.bukkit.Material.CHEST
        
        // Get chest inventory and set custom name
        val chest = block.state as? org.bukkit.block.Chest
        chest?.let {
            it.customName = "${tier.color}${tier.displayName}"
            it.update()
        }
        
        // Add particle effects based on tier
        when (tier) {
            SupplyBoxTier.LEGENDARY -> {
                // Gold particles
                world.spawnParticle(
                    org.bukkit.Particle.HAPPY_VILLAGER,
                    location.clone().add(0.5, 1.0, 0.5),
                    10, 0.3, 0.3, 0.3, 0.0
                )
            }
            SupplyBoxTier.ELITE -> {
                // Purple particles
                world.spawnParticle(
                    org.bukkit.Particle.ENCHANT,
                    location.clone().add(0.5, 1.0, 0.5),
                    5, 0.3, 0.3, 0.3, 0.0
                )
            }
            SupplyBoxTier.ADVANCED -> {
                // Blue particles
                world.spawnParticle(
                    org.bukkit.Particle.BUBBLE_COLUMN_UP,
                    location.clone().add(0.5, 1.0, 0.5),
                    3, 0.2, 0.2, 0.2, 0.0
                )
            }
            else -> {
                // No special effects for basic tier
            }
        }
    }
    
    private fun removePhysicalChest(boxLocation: BoxLocation) {
        val location = boxLocation.toLocation() ?: return
        val block = location.world?.getBlockAt(location)
        if (block?.type == org.bukkit.Material.CHEST) {
            block.type = org.bukkit.Material.AIR
        }
    }
}