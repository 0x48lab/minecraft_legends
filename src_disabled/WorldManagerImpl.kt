package com.hacklab.minecraft_legends.infrastructure.world

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.WorldRepository
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.*

class WorldManagerImpl(
    private val plugin: Plugin
) : WorldRepository {
    
    private val logger = LoggerFactory.getLogger()
    private val worlds = mutableMapOf<UUID, GameWorld>()
    
    override suspend fun findById(id: UUID): GameWorld? = withContext(Dispatchers.Main) {
        worlds[id]
    }
    
    override suspend fun findByWorldName(worldName: String): GameWorld? = withContext(Dispatchers.Main) {
        worlds.values.find { it.worldName == worldName }
    }
    
    override suspend fun save(world: GameWorld): GameWorld = withContext(Dispatchers.Main) {
        worlds[world.id] = world
        world
    }
    
    override suspend fun delete(id: UUID): Boolean = withContext(Dispatchers.Main) {
        val world = worlds[id]
        if (world != null) {
            deleteWorld(world.worldName)
            worlds.remove(id)
            true
        } else {
            false
        }
    }
    
    override suspend fun deleteByWorldName(worldName: String): Boolean = withContext(Dispatchers.Main) {
        val world = worlds.values.find { it.worldName == worldName }
        return@withContext if (world != null) {
            delete(world.id)
        } else {
            deleteWorld(worldName)
        }
    }
    
    override suspend fun findAll(): List<GameWorld> = withContext(Dispatchers.Main) {
        worlds.values.toList()
    }
    
    override suspend fun createWorld(settings: WorldGenerationSettings): GameWorld = withContext(Dispatchers.Main) {
        val worldName = "br_world_${UUID.randomUUID()}"
        
        logger.info("Creating world: $worldName with settings: $settings")
        
        try {
            // ワールド作成設定
            val worldCreator = WorldCreator.name(worldName).apply {
                type(convertWorldType(settings.worldType))
                generateStructures(settings.generateStructures)
                settings.seed?.let { seed(it) }
            }
            
            // ワールドを作成
            val bukkitWorld = Bukkit.createWorld(worldCreator)
                ?: throw Exception("Failed to create Bukkit world")
            
            // ワールドの基本設定
            bukkitWorld.apply {
                difficulty = Difficulty.NORMAL
                setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                setGameRule(GameRule.DO_MOB_SPAWNING, false)
                setGameRule(GameRule.KEEP_INVENTORY, true)
                setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                setGameRule(GameRule.DO_FIRE_TICK, false)
                setGameRule(GameRule.MOB_GRIEFING, false)
                time = 6000 // 昼間に固定
            }
            
            // ワールドボーダーを設定
            val worldBorder = bukkitWorld.worldBorder
            worldBorder.center = bukkitWorld.spawnLocation
            worldBorder.size = settings.size.toDouble()
            worldBorder.damageAmount = 1.0
            worldBorder.damageBuffer = 5.0
            worldBorder.warningDistance = 50
            
            logger.info("World created successfully: $worldName")
            
            // GameWorldエンティティを作成
            val safeBounds = WorldBounds(
                minX = settings.centerX - settings.size / 2,
                maxX = settings.centerX + settings.size / 2,
                minZ = settings.centerZ - settings.size / 2,
                maxZ = settings.centerZ + settings.size / 2
            )
            
            GameWorld(
                worldName = worldName,
                size = settings.size,
                centerX = settings.centerX,
                centerZ = settings.centerZ,
                safeBounds = safeBounds
            )
            
        } catch (e: Exception) {
            logger.error("Failed to create world: $worldName", e)
            throw e
        }
    }
    
    override suspend fun generateWorld(world: GameWorld): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            val bukkitWorld = Bukkit.getWorld(world.worldName)
                ?: throw Exception("World not found: ${world.worldName}")
            
            logger.info("Generating structures for world: ${world.worldName}")
            
            // リスポーンビーコンを生成
            world.respawnBeacons.forEach { beacon ->
                generateRespawnBeacon(bukkitWorld, beacon)
            }
            
            // サプライボックスを生成
            world.supplyBoxes.forEach { supplyBox ->
                generateSupplyBox(bukkitWorld, supplyBox)
            }
            
            // スポーン地点を準備
            world.spawnPoints.forEach { spawnPoint ->
                prepareSpawnPoint(bukkitWorld, spawnPoint)
            }
            
            logger.info("World generation completed: ${world.worldName}")
            true
            
        } catch (e: Exception) {
            logger.error("Failed to generate world structures", e)
            false
        }
    }
    
    override suspend fun deleteWorld(worldName: String): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            val bukkitWorld = Bukkit.getWorld(worldName)
            
            if (bukkitWorld != null) {
                // プレイヤーを別のワールドに移動
                bukkitWorld.players.forEach { player ->
                    player.teleport(Bukkit.getWorlds()[0].spawnLocation)
                }
                
                // ワールドをアンロード
                Bukkit.unloadWorld(bukkitWorld, false)
            }
            
            // ワールドファイルを削除
            val worldFolder = File(Bukkit.getWorldContainer(), worldName)
            if (worldFolder.exists()) {
                worldFolder.deleteRecursively()
                logger.info("Deleted world folder: $worldName")
            }
            
            true
        } catch (e: Exception) {
            logger.error("Failed to delete world: $worldName", e)
            false
        }
    }
    
    override suspend fun isWorldGenerated(worldName: String): Boolean = withContext(Dispatchers.Main) {
        Bukkit.getWorld(worldName) != null
    }
    
    override suspend fun loadWorld(worldName: String): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            if (Bukkit.getWorld(worldName) == null) {
                val worldCreator = WorldCreator.name(worldName)
                Bukkit.createWorld(worldCreator) != null
            } else {
                true
            }
        } catch (e: Exception) {
            logger.error("Failed to load world: $worldName", e)
            false
        }
    }
    
    override suspend fun unloadWorld(worldName: String): Boolean = withContext(Dispatchers.Main) {
        return@withContext try {
            val world = Bukkit.getWorld(worldName)
            if (world != null) {
                // プレイヤーを移動
                world.players.forEach { player ->
                    player.teleport(Bukkit.getWorlds()[0].spawnLocation)
                }
                Bukkit.unloadWorld(world, true)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to unload world: $worldName", e)
            false
        }
    }
    
    // リスポーンビーコン関連の実装は簡略化
    override suspend fun saveRespawnBeacon(beacon: RespawnBeacon): Boolean = true
    override suspend fun findRespawnBeaconById(id: UUID): RespawnBeacon? = null
    override suspend fun findRespawnBeaconsByWorld(worldId: UUID): List<RespawnBeacon> = emptyList()
    override suspend fun updateRespawnBeaconStatus(beaconId: UUID, isActive: Boolean): Boolean = true
    override suspend fun destroyRespawnBeacon(beaconId: UUID): Boolean = true
    
    // サプライボックス関連の実装は簡略化
    override suspend fun saveSupplyBox(supplyBox: SupplyBox): Boolean = true
    override suspend fun findSupplyBoxById(id: UUID): SupplyBox? = null
    override suspend fun findSupplyBoxesByWorld(worldId: UUID): List<SupplyBox> = emptyList()
    override suspend fun markSupplyBoxAsLooted(supplyBoxId: UUID): Boolean = true
    override suspend fun resetSupplyBoxes(worldId: UUID): Boolean = true
    
    // 位置関連の実装は簡略化
    override suspend fun findNearestRespawnBeacon(worldId: UUID, x: Int, z: Int): RespawnBeacon? = null
    override suspend fun findSupplyBoxesInRadius(worldId: UUID, x: Int, z: Int, radius: Int): List<SupplyBox> = emptyList()
    override suspend fun isLocationSafe(worldId: UUID, x: Int, y: Int, z: Int): Boolean = true
    
    override suspend fun cleanupOldWorlds(maxAge: Long): Int = 0
    override suspend fun getWorldSize(worldName: String): Long = 0L
    
    private fun convertWorldType(worldType: com.hacklab.minecraft_legends.domain.entity.WorldType): org.bukkit.WorldType {
        return when (worldType) {
            com.hacklab.minecraft_legends.domain.entity.WorldType.NORMAL -> org.bukkit.WorldType.NORMAL
            com.hacklab.minecraft_legends.domain.entity.WorldType.FLAT -> org.bukkit.WorldType.FLAT
            com.hacklab.minecraft_legends.domain.entity.WorldType.AMPLIFIED -> org.bukkit.WorldType.AMPLIFIED
            com.hacklab.minecraft_legends.domain.entity.WorldType.LARGE_BIOMES -> org.bukkit.WorldType.LARGE_BIOMES
        }
    }
    
    private fun generateRespawnBeacon(world: org.bukkit.World, beacon: RespawnBeacon) {
        try {
            val location = Location(world, beacon.x.toDouble(), beacon.y.toDouble(), beacon.z.toDouble())
            
            // 地面を平坦化
            for (x in -1..1) {
                for (z in -1..1) {
                    val block = world.getBlockAt(location.x.toInt() + x, beacon.y - 1, location.z.toInt() + z)
                    block.type = Material.IRON_BLOCK
                }
            }
            
            // ビーコンを設置
            val beaconBlock = world.getBlockAt(beacon.x, beacon.y, beacon.z)
            beaconBlock.type = Material.BEACON
            
            // ボタンを設置（復活用）
            val buttonBlock = world.getBlockAt(beacon.x + 1, beacon.y, beacon.z)
            buttonBlock.type = Material.STONE_BUTTON
            
            // ビーコン台座を作成（3x3の鉄ブロック）
            for (x in -1..1) {
                for (z in -1..1) {
                    if (x != 0 || z != 0) { // 中央はビーコン
                        val block = world.getBlockAt(beacon.x + x, beacon.y, beacon.z + z)
                        block.type = Material.IRON_BLOCK
                    }
                }
            }
            
            logger.debug("Generated respawn beacon at ${beacon.x}, ${beacon.y}, ${beacon.z}")
            
        } catch (e: Exception) {
            logger.error("Failed to generate respawn beacon", e)
        }
    }
    
    private fun generateSupplyBox(world: org.bukkit.World, supplyBox: SupplyBox) {
        try {
            val location = Location(world, supplyBox.x.toDouble(), supplyBox.y.toDouble(), supplyBox.z.toDouble())
            
            // 地面を確保
            val groundBlock = world.getBlockAt(supplyBox.x, supplyBox.y - 1, supplyBox.z)
            if (groundBlock.type == Material.AIR) {
                groundBlock.type = Material.STONE
            }
            
            // チェストを設置
            val chestBlock = world.getBlockAt(supplyBox.x, supplyBox.y, supplyBox.z)
            chestBlock.type = Material.CHEST
            
            // TODO: チェストの中身を設定（LootTableから）
            
            logger.debug("Generated supply box at ${supplyBox.x}, ${supplyBox.y}, ${supplyBox.z}")
            
        } catch (e: Exception) {
            logger.error("Failed to generate supply box", e)
        }
    }
    
    private fun prepareSpawnPoint(world: org.bukkit.World, spawnPoint: SpawnPoint) {
        try {
            // スポーン地点を平坦化
            for (x in -2..2) {
                for (z in -2..2) {
                    val groundBlock = world.getBlockAt(spawnPoint.x + x, spawnPoint.y - 1, spawnPoint.z + z)
                    groundBlock.type = Material.STONE
                    
                    val airBlock1 = world.getBlockAt(spawnPoint.x + x, spawnPoint.y, spawnPoint.z + z)
                    airBlock1.type = Material.AIR
                    
                    val airBlock2 = world.getBlockAt(spawnPoint.x + x, spawnPoint.y + 1, spawnPoint.z + z)
                    airBlock2.type = Material.AIR
                }
            }
            
            logger.debug("Prepared spawn point at ${spawnPoint.x}, ${spawnPoint.y}, ${spawnPoint.z}")
            
        } catch (e: Exception) {
            logger.error("Failed to prepare spawn point", e)
        }
    }
}