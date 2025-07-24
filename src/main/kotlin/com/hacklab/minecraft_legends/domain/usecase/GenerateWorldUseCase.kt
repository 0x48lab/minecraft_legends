package com.hacklab.minecraft_legends.domain.usecase

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.WorldRepository
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlin.math.sqrt
import kotlin.random.Random
import java.util.UUID

interface GenerateWorldUseCase {
    suspend fun execute(request: GenerateWorldRequest): Result<GameWorld>
}

data class GenerateWorldRequest(
    val gameId: String,
    val settings: WorldGenerationSettings
)

class GenerateWorldUseCaseImpl(
    private val worldRepository: WorldRepository
) : GenerateWorldUseCase {
    
    private val logger = LoggerFactory.getLogger()
    
    override suspend fun execute(request: GenerateWorldRequest): Result<GameWorld> {
        return try {
            logger.info("Starting world generation for game: ${request.gameId}")
            
            val worldName = "br_world_${request.gameId}"
            
            // 既存のワールドをチェック
            val existingWorld = worldRepository.findByWorldName(worldName)
            if (existingWorld != null) {
                logger.warn("World already exists: $worldName")
                return Result.failure(Exception("World already exists"))
            }
            
            // ワールド基本情報を作成
            val worldBounds = WorldBounds(
                minX = request.settings.centerX - request.settings.size / 2,
                maxX = request.settings.centerX + request.settings.size / 2,
                minZ = request.settings.centerZ - request.settings.size / 2,
                maxZ = request.settings.centerZ + request.settings.size / 2
            )
            
            var gameWorld = GameWorld(
                worldName = worldName,
                size = request.settings.size,
                centerX = request.settings.centerX,
                centerZ = request.settings.centerZ,
                safeBounds = worldBounds
            )
            
            // 1. 物理的なワールドを生成
            gameWorld = worldRepository.createWorld(request.settings)
            logger.info("Physical world created: $worldName")
            
            // 2. リスポーンビーコンを配置
            val beacons = generateRespawnBeacons(gameWorld, request.settings)
            gameWorld = beacons.fold(gameWorld) { world, beacon ->
                world.addRespawnBeacon(beacon)
            }
            logger.info("Generated ${beacons.size} respawn beacons")
            
            // 3. サプライボックスを配置
            val supplyBoxes = generateSupplyBoxes(gameWorld, request.settings, UUID.fromString(request.gameId))
            gameWorld = supplyBoxes.fold(gameWorld) { world, box ->
                world.addSupplyBox(box)
            }
            logger.info("Generated ${supplyBoxes.size} supply boxes")
            
            // 4. チームスポーン地点を配置
            val spawnPoints = generateTeamSpawnPoints(gameWorld, request.settings)
            gameWorld = spawnPoints.fold(gameWorld) { world, spawn ->
                world.addSpawnPoint(spawn)
            }
            logger.info("Generated ${spawnPoints.size} team spawn points")
            
            // 5. ワールドを保存
            val savedWorld = worldRepository.save(gameWorld.markAsGenerated())
            
            // 6. 物理的な構造物を配置
            if (!worldRepository.generateWorld(savedWorld)) {
                logger.error("Failed to generate physical structures")
                return Result.failure(Exception("Failed to generate world structures"))
            }
            
            logger.info("World generation completed: $worldName")
            Result.success(savedWorld)
            
        } catch (e: Exception) {
            logger.error("Failed to generate world", e)
            Result.failure(e)
        }
    }
    
    private fun generateRespawnBeacons(
        world: GameWorld,
        settings: WorldGenerationSettings
    ): List<RespawnBeacon> {
        val beacons = mutableListOf<RespawnBeacon>()
        val attempts = settings.respawnBeaconCount * 10 // 最大試行回数
        var attemptsCount = 0
        
        while (beacons.size < settings.respawnBeaconCount && attemptsCount < attempts) {
            attemptsCount++
            
            val x = Random.nextInt(world.minX + 50, world.maxX - 50)
            val z = Random.nextInt(world.minZ + 50, world.maxZ - 50)
            val y = 100 // 適切な高度を後で計算
            
            // 他のビーコンとの距離チェック
            val tooClose = beacons.any { beacon ->
                val distance = sqrt(
                    ((x - beacon.x) * (x - beacon.x) + (z - beacon.z) * (z - beacon.z)).toDouble()
                )
                distance < settings.minBeaconDistance
            }
            
            if (!tooClose) {
                beacons.add(
                    RespawnBeacon(
                        x = x,
                        y = y,
                        z = z
                    )
                )
            }
        }
        
        logger.info("Generated ${beacons.size} respawn beacons after $attemptsCount attempts")
        return beacons
    }
    
    private fun generateSupplyBoxes(
        world: GameWorld,
        settings: WorldGenerationSettings,
        gameId: UUID
    ): List<SupplyBox> {
        val supplyBoxes = mutableListOf<SupplyBox>()
        
        // 各リスポーンビーコンの周りにサプライボックスを配置
        world.respawnBeacons.forEach { beacon ->
            repeat(settings.supplyBoxesPerBeacon) {
                val angle = Random.nextDouble(0.0, 2 * Math.PI)
                val distance = Random.nextInt(10, settings.supplyBoxSpawnRadius)
                
                val x = beacon.x + (distance * kotlin.math.cos(angle)).toInt()
                val z = beacon.z + (distance * kotlin.math.sin(angle)).toInt()
                val y = beacon.y // ビーコンと同じ高度
                
                // ワールド境界内チェック
                if (world.isLocationInBounds(x, z)) {
                    val rarity = when (Random.nextInt(100)) {
                        in 0..69 -> LootRarity.COMMON  // 70%
                        in 70..89 -> LootRarity.RARE   // 20%
                        in 90..97 -> LootRarity.EPIC   // 8%
                        else -> LootRarity.LEGENDARY   // 2%
                    }
                    
                    supplyBoxes.add(
                        SupplyBox(
                            gameId = gameId,
                            location = BoxLocation(x.toDouble(), y.toDouble(), z.toDouble(), world.worldName),
                            tier = when (rarity) {
                                LootRarity.COMMON -> SupplyBoxTier.BASIC
                                LootRarity.RARE -> SupplyBoxTier.ADVANCED
                                LootRarity.EPIC -> SupplyBoxTier.ELITE
                                LootRarity.LEGENDARY -> SupplyBoxTier.LEGENDARY
                            },
                            contents = emptyList() // Will be populated later
                        )
                    )
                }
            }
        }
        
        // マップ中央にも高レアリティのサプライボックスを配置
        repeat(3) {
            val x = world.centerX + Random.nextInt(-50, 50)
            val z = world.centerZ + Random.nextInt(-50, 50)
            val y = 100
            
            supplyBoxes.add(
                SupplyBox(
                    gameId = gameId,
                    location = BoxLocation(x.toDouble(), y.toDouble(), z.toDouble(), world.worldName),
                    tier = SupplyBoxTier.ELITE,
                    contents = emptyList() // Will be populated later
                )
            )
        }
        
        return supplyBoxes
    }
    
    private fun generateTeamSpawnPoints(
        world: GameWorld,
        settings: WorldGenerationSettings
    ): List<SpawnPoint> {
        val spawnPoints = mutableListOf<SpawnPoint>()
        val maxTeams = 8 // 設定可能にする
        
        // マップの外周に等間隔でスポーン地点を配置
        val centerX = world.centerX
        val centerZ = world.centerZ
        val spawnRadius = settings.teamSpawnRadius
        
        repeat(maxTeams) { i ->
            val angle = (2 * Math.PI * i) / maxTeams
            val x = centerX + (spawnRadius * kotlin.math.cos(angle)).toInt()
            val z = centerZ + (spawnRadius * kotlin.math.sin(angle)).toInt()
            val y = 100 // 適切な高度を後で計算
            
            if (world.isLocationInBounds(x, z)) {
                spawnPoints.add(
                    SpawnPoint(
                        x = x,
                        y = y,
                        z = z
                    )
                )
            }
        }
        
        return spawnPoints
    }
}