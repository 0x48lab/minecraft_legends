package com.hacklab.minecraft_legends

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import kotlin.random.Random

/**
 * 最小限のテスト用プラグイン
 * ワールド生成とビーコン配置の基本機能をテストするため
 */
class TestPlugin : JavaPlugin() {
    
    override fun onEnable() {
        logger.info("Battle Royale Test Plugin enabled!")
    }
    
    override fun onDisable() {
        logger.info("Battle Royale Test Plugin disabled!")
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by players!")
            return true
        }
        
        when (command.name.lowercase()) {
            "testworld" -> {
                createTestWorld(sender)
                return true
            }
            "testbeacons" -> {
                placeTestBeacons(sender)
                return true
            }
            "tptest" -> {
                teleportToTestWorld(sender)
                return true
            }
        }
        
        return false
    }
    
    private fun createTestWorld(player: Player) {
        player.sendMessage("§eCreating test world...")
        
        val worldName = "br_test_world"
        
        // 既存のワールドがあれば削除
        val existingWorld = Bukkit.getWorld(worldName)
        if (existingWorld != null) {
            player.sendMessage("§cTest world already exists!")
            return
        }
        
        try {
            // 新しいワールドを作成
            val worldCreator = WorldCreator(worldName)
            worldCreator.environment(World.Environment.NORMAL)
            worldCreator.type(org.bukkit.WorldType.FLAT)
            
            val world = Bukkit.createWorld(worldCreator)
            
            if (world != null) {
                player.sendMessage("§aTest world created successfully!")
                player.sendMessage("§eWorld name: §f$worldName")
                player.sendMessage("§eUse /tptest to teleport to the test world")
            } else {
                player.sendMessage("§cFailed to create test world!")
            }
        } catch (e: Exception) {
            player.sendMessage("§cError creating world: ${e.message}")
            logger.warning("Failed to create test world: ${e.message}")
        }
    }
    
    private fun placeTestBeacons(player: Player) {
        val world = player.world
        val centerLoc = player.location
        
        player.sendMessage("§ePlacing test beacons around your location...")
        
        val beaconCount = 6
        val radius = 100
        val beaconLocations = mutableListOf<Location>()
        
        // 円形にビーコンを配置
        for (i in 0 until beaconCount) {
            val angle = (2 * Math.PI * i) / beaconCount
            val x = centerLoc.x + (radius * kotlin.math.cos(angle))
            val z = centerLoc.z + (radius * kotlin.math.sin(angle))
            val y = getHighestBlockY(world, x.toInt(), z.toInt()) + 1
            
            val beaconLoc = Location(world, x, y.toDouble(), z)
            beaconLocations.add(beaconLoc)
            
            // ビーコンベースを配置（鉄ブロック3x3）
            for (dx in -1..1) {
                for (dz in -1..1) {
                    val baseLoc = beaconLoc.clone().add(dx.toDouble(), -1.0, dz.toDouble())
                    baseLoc.block.type = Material.IRON_BLOCK
                }
            }
            
            // ビーコンを配置
            beaconLoc.block.type = Material.BEACON
            
            // 目印となるパーティクルエフェクト
            world.spawnParticle(
                org.bukkit.Particle.FLAME,
                beaconLoc.clone().add(0.5, 2.0, 0.5),
                10, 0.5, 1.0, 0.5, 0.1
            )
            
            player.sendMessage("§aBeacon ${i + 1} placed at: §f${x.toInt()}, ${y}, ${z.toInt()}")
        }
        
        player.sendMessage("§aPlaced $beaconCount test beacons!")
        player.sendMessage("§eBeacons are placed in a circle with radius $radius blocks")
    }
    
    private fun teleportToTestWorld(player: Player) {
        val worldName = "br_test_world"
        val testWorld = Bukkit.getWorld(worldName)
        
        if (testWorld == null) {
            player.sendMessage("§cTest world does not exist! Use /testworld to create it first.")
            return
        }
        
        val spawnLoc = Location(testWorld, 0.5, 65.0, 0.5)
        
        // スポーン地点に安全な足場を作る
        for (x in -2..2) {
            for (z in -2..2) {
                val groundLoc = Location(testWorld, x.toDouble(), 64.0, z.toDouble())
                groundLoc.block.type = Material.STONE
            }
        }
        
        player.teleport(spawnLoc)
        player.sendMessage("§aTeleported to test world!")
        player.sendMessage("§eUse /testbeacons to place test beacons")
    }
    
    private fun getHighestBlockY(world: World, x: Int, z: Int): Int {
        for (y in world.maxHeight - 1 downTo world.minHeight) {
            val block = world.getBlockAt(x, y, z)
            if (block.type != Material.AIR) {
                return y
            }
        }
        return world.minHeight
    }
}