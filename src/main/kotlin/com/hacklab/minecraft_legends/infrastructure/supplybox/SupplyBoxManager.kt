package com.hacklab.minecraft_legends.infrastructure.supplybox

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.usecase.ManageSupplyBoxUseCase
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SupplyBoxManager(
    private val plugin: Plugin,
    private val manageSupplyBoxUseCase: ManageSupplyBoxUseCase
) : Listener {
    
    private val logger = LoggerFactory.getLogger()
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val locationToSupplyBoxMap = ConcurrentHashMap<String, UUID>()
    private val particleEffectTasks = ConcurrentHashMap<UUID, BukkitTask>()
    
    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }
    
    suspend fun spawnSupplyBoxes(gameId: UUID, worldName: String): Result<List<SupplyBox>> {
        return try {
            logger.info("Spawning supply boxes for game: $gameId")
            
            val result = manageSupplyBoxUseCase.generateSupplyBoxes(gameId, worldName)
            
            if (result.isSuccess) {
                val supplyBoxes = result.getOrThrow()
                
                // Map locations to supply box IDs
                supplyBoxes.forEach { box ->
                    val locationKey = getLocationKey(box.location)
                    locationToSupplyBoxMap[locationKey] = box.id
                }
                
                // Start particle effects for higher tier boxes
                startParticleEffects(supplyBoxes)
                
                logger.info("Successfully spawned ${supplyBoxes.size} supply boxes")
            }
            
            result
            
        } catch (e: Exception) {
            logger.error("Failed to spawn supply boxes for game $gameId", e)
            Result.failure(e)
        }
    }
    
    suspend fun cleanupSupplyBoxes(gameId: UUID): Result<Boolean> {
        return try {
            logger.info("Cleaning up supply boxes for game: $gameId")
            
            // Stop particle effects
            stopAllParticleEffects()
            
            // Clear location mappings
            locationToSupplyBoxMap.clear()
            
            // Remove from world and database
            val result = manageSupplyBoxUseCase.cleanupSupplyBoxes(gameId)
            
            if (result.isSuccess) {
                logger.info("Successfully cleaned up supply boxes for game $gameId")
            }
            
            result
            
        } catch (e: Exception) {
            logger.error("Failed to cleanup supply boxes for game $gameId", e)
            Result.failure(e)
        }
    }
    
    @EventHandler
    fun onPlayerInteractWithChest(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        
        val block = event.clickedBlock ?: return
        if (block.type != Material.CHEST) return
        
        val location = block.location
        val locationKey = getLocationKey(location)
        val supplyBoxId = locationToSupplyBoxMap[locationKey] ?: return
        
        // Cancel the default chest opening
        event.isCancelled = true
        
        // Handle supply box opening asynchronously
        managerScope.launch {
            openSupplyBox(supplyBoxId, event.player)
        }
    }
    
    private suspend fun openSupplyBox(supplyBoxId: UUID, player: Player) {
        try {
            val result = manageSupplyBoxUseCase.openSupplyBox(supplyBoxId, player.uniqueId)
            
            if (result.isSuccess) {
                val openResult = result.getOrThrow()
                
                if (openResult.success) {
                    // Show custom inventory with loot
                    showSupplyBoxInventory(player, openResult.supplyBox, openResult.items)
                    
                    // Remove from location mapping
                    val locationKey = getLocationKey(openResult.supplyBox.location)
                    locationToSupplyBoxMap.remove(locationKey)
                    
                    // Stop particle effects
                    stopParticleEffects(supplyBoxId)
                    
                    // Play opening effects
                    playOpeningEffects(player, openResult.supplyBox)
                    
                    logger.info("Player ${player.name} opened supply box $supplyBoxId (${openResult.supplyBox.tier})")
                } else {
                    player.sendMessage("§c${openResult.message}")
                }
            } else {
                player.sendMessage("§cFailed to open supply box: ${result.exceptionOrNull()?.message}")
            }
            
        } catch (e: Exception) {
            logger.error("Error opening supply box $supplyBoxId for player ${player.name}", e)
            player.sendMessage("§cAn error occurred while opening the supply box")
        }
    }
    
    private fun showSupplyBoxInventory(player: Player, supplyBox: SupplyBox, items: List<LootItem>) {
        val inventoryTitle = "${supplyBox.tier.color}${supplyBox.tier.displayName}"
        val inventory = Bukkit.createInventory(null, 27, inventoryTitle)
        
        // Add items to inventory
        items.forEachIndexed { index, lootItem ->
            if (index < inventory.size) {
                val itemStack = lootItem.toItemStack()
                
                // Add rarity to lore
                val meta = itemStack.itemMeta
                if (meta != null) {
                    val currentLore = meta.lore ?: mutableListOf()
                    currentLore.add("")
                    currentLore.add("${lootItem.rarity.color}${lootItem.rarity.displayName}")
                    meta.lore = currentLore
                    itemStack.itemMeta = meta
                }
                
                inventory.setItem(index, itemStack)
            }
        }
        
        // Add items directly to player inventory
        items.forEach { lootItem ->
            val itemStack = lootItem.toItemStack()
            val remaining = player.inventory.addItem(itemStack)
            
            // Drop items that don't fit
            remaining.values.forEach { item ->
                player.world.dropItem(player.location, item)
            }
        }
        
        // Show temporary inventory for preview
        player.openInventory(inventory)
        
        // Close inventory after 3 seconds
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.openInventory.topInventory == inventory) {
                player.closeInventory()
            }
        }, 60L) // 3 seconds
    }
    
    private fun startParticleEffects(supplyBoxes: List<SupplyBox>) {
        supplyBoxes.forEach { box ->
            if (box.tier != SupplyBoxTier.BASIC) {
                startParticleEffects(box)
            }
        }
    }
    
    private fun startParticleEffects(supplyBox: SupplyBox) {
        val location = supplyBox.location.toLocation() ?: return
        val particleLocation = location.clone().add(0.5, 1.2, 0.5)
        
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            when (supplyBox.tier) {
                SupplyBoxTier.LEGENDARY -> {
                    // Gold particles with enchantment table effect
                    location.world?.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        particleLocation,
                        3, 0.3, 0.3, 0.3, 0.0
                    )
                    location.world?.spawnParticle(
                        Particle.ENCHANT,
                        particleLocation,
                        5, 0.5, 0.5, 0.5, 0.0
                    )
                }
                SupplyBoxTier.ELITE -> {
                    // Purple enchantment particles
                    location.world?.spawnParticle(
                        Particle.ENCHANT,
                        particleLocation,
                        3, 0.3, 0.3, 0.3, 0.0
                    )
                    location.world?.spawnParticle(
                        Particle.PORTAL,
                        particleLocation,
                        2, 0.2, 0.2, 0.2, 0.0
                    )
                }
                SupplyBoxTier.ADVANCED -> {
                    // Blue sparkle particles
                    location.world?.spawnParticle(
                        Particle.BUBBLE_COLUMN_UP,
                        particleLocation,
                        2, 0.2, 0.2, 0.2, 0.0
                    )
                    location.world?.spawnParticle(
                        Particle.FIREWORK,
                        particleLocation,
                        1, 0.1, 0.1, 0.1, 0.0
                    )
                }
                else -> {
                    // No particles for basic tier
                }
            }
        }, 0L, 40L) // Every 2 seconds
        
        particleEffectTasks[supplyBox.id] = task
    }
    
    private fun stopParticleEffects(supplyBoxId: UUID) {
        particleEffectTasks[supplyBoxId]?.cancel()
        particleEffectTasks.remove(supplyBoxId)
    }
    
    private fun stopAllParticleEffects() {
        particleEffectTasks.values.forEach { it.cancel() }
        particleEffectTasks.clear()
    }
    
    private fun playOpeningEffects(player: Player, supplyBox: SupplyBox) {
        val location = supplyBox.location.toLocation() ?: return
        
        // Play sound effect
        when (supplyBox.tier) {
            SupplyBoxTier.LEGENDARY -> {
                player.playSound(location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                player.playSound(location, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f)
            }
            SupplyBoxTier.ELITE -> {
                player.playSound(location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f)
                player.playSound(location, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f)
            }
            SupplyBoxTier.ADVANCED -> {
                player.playSound(location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f)
            }
            SupplyBoxTier.BASIC -> {
                player.playSound(location, org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f)
            }
        }
        
        // Play particle burst
        val particleLocation = location.clone().add(0.5, 1.0, 0.5)
        when (supplyBox.tier) {
            SupplyBoxTier.LEGENDARY -> {
                location.world?.spawnParticle(
                    Particle.FIREWORK,
                    particleLocation,
                    20, 0.5, 0.5, 0.5, 0.1
                )
                location.world?.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    particleLocation,
                    15, 0.5, 0.5, 0.5, 0.0
                )
            }
            SupplyBoxTier.ELITE -> {
                location.world?.spawnParticle(
                    Particle.ENCHANT,
                    particleLocation,
                    15, 0.5, 0.5, 0.5, 0.0
                )
            }
            SupplyBoxTier.ADVANCED -> {
                location.world?.spawnParticle(
                    Particle.BUBBLE_COLUMN_UP,
                    particleLocation,
                    10, 0.3, 0.3, 0.3, 0.0
                )
            }
            else -> {
                // Minimal effects for basic tier
                location.world?.spawnParticle(
                    Particle.CLOUD,
                    particleLocation,
                    5, 0.2, 0.2, 0.2, 0.0
                )
            }
        }
        
        // Show tier-specific title
        val title = when (supplyBox.tier) {
            SupplyBoxTier.LEGENDARY -> "§6✦ LEGENDARY SUPPLY ✦"
            SupplyBoxTier.ELITE -> "§5◆ ELITE SUPPLY ◆"
            SupplyBoxTier.ADVANCED -> "§b● ADVANCED SUPPLY ●"
            SupplyBoxTier.BASIC -> "§7■ BASIC SUPPLY ■"
        }
        
        player.sendTitle(title, "§fSupply Box Opened!", 10, 40, 10)
        
        // Remove the physical chest
        val block = location.block
        if (block.type == Material.CHEST) {
            block.type = Material.AIR
        }
    }
    
    private fun getLocationKey(location: BoxLocation): String {
        return "${location.worldName}:${location.x.toInt()}:${location.y.toInt()}:${location.z.toInt()}"
    }
    
    private fun getLocationKey(location: Location): String {
        return "${location.world?.name}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }
    
    fun shutdown() {
        stopAllParticleEffects()
        managerScope.cancel()
    }
}