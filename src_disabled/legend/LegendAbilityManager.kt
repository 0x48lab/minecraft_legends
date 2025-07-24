package com.hacklab.minecraft_legends.infrastructure.legend

import com.hacklab.minecraft_legends.application.service.GameService
import com.hacklab.minecraft_legends.infrastructure.i18n.MessageManager
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class LegendAbility(
    val id: String,
    val name: String,
    val cooldown: Long, // in milliseconds
    val description: String
) {
    abstract suspend fun execute(player: Player, gameService: GameService): Boolean
    
    open fun canUse(player: Player): Boolean = true
    open fun getUseRequirements(): List<String> = emptyList()
}

class LegendAbilityManager(
    private val plugin: Plugin,
    private val gameService: GameService,
    private val messageManager: MessageManager,
    private val pluginScope: CoroutineScope
) : Listener {
    
    private val logger = LoggerFactory.getLogger()
    private val abilityCooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val abilities = mutableMapOf<String, LegendAbility>()
    private val activeDrones = ConcurrentHashMap<UUID, ArmorStand>()
    private val phaseWalkPlayers = mutableSetOf<UUID>()
    
    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        registerAbilities()
    }
    
    private fun registerAbilities() {
        abilities["pathfinder_grapple"] = PathfinderGrappleAbility()
        abilities["wraith_phase"] = WraithPhaseAbility()
        abilities["lifeline_drone"] = LifelineHealDroneAbility()
    }
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && 
            event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        // Check if holding ability item
        if (!isAbilityItem(item)) return
        
        event.isCancelled = true
        
        pluginScope.launch {
            val currentGame = gameService.getCurrentGame()
            if (currentGame == null || !currentGame.isActive) return@launch
            
            // Get player's legend
            val playerTeam = currentGame.teams.find { team ->
                team.players.any { it.id == player.uniqueId }
            } ?: return@launch
            
            val gamePlayer = playerTeam.players.find { it.id == player.uniqueId } ?: return@launch
            val legend = gamePlayer.legend
            
            val abilityId = when (legend) {
                "pathfinder" -> "pathfinder_grapple"
                "wraith" -> "wraith_phase"
                "lifeline" -> "lifeline_drone"
                else -> return@launch
            }
            
            useAbility(player, abilityId)
        }
    }
    
    suspend fun useAbility(player: Player, abilityId: String) {
        val ability = abilities[abilityId] ?: return
        
        // Check cooldown
        val playerCooldowns = abilityCooldowns.getOrPut(player.uniqueId) { ConcurrentHashMap() }
        val lastUsed = playerCooldowns[abilityId] ?: 0
        val cooldownRemaining = (lastUsed + ability.cooldown - System.currentTimeMillis()) / 1000
        
        if (cooldownRemaining > 0) {
            val message = messageManager.getMessage("legend.ability-cooldown", player, cooldownRemaining)
            player.sendMessage(message)
            return
        }
        
        // Check if ability can be used
        if (!ability.canUse(player)) {
            player.sendMessage("§cCannot use ability right now!")
            return
        }
        
        // Execute ability
        val success = ability.execute(player, gameService)
        if (success) {
            playerCooldowns[abilityId] = System.currentTimeMillis()
            val message = messageManager.getMessage("legend.${abilityId.split("_")[0]}.ability-used", player)
            player.sendMessage(message)
        }
    }
    
    private fun isAbilityItem(item: ItemStack): Boolean {
        return when (item.type) {
            Material.FISHING_ROD, Material.ENDER_PEARL, Material.GOLDEN_APPLE -> true
            else -> false
        }
    }
    
    // Pathfinder Grappling Hook
    inner class PathfinderGrappleAbility : LegendAbility(
        id = "pathfinder_grapple",
        name = "Grappling Hook",
        cooldown = 15000, // 15 seconds
        description = "Shoot a grappling hook to quickly reach distant locations"
    ) {
        
        override suspend fun execute(player: Player, gameService: GameService): Boolean {
            val targetLocation = getGrappleTarget(player) ?: return false
            
            // Launch player towards target
            launchPlayer(player, targetLocation)
            
            // Play sound
            player.world.playSound(player.location, org.bukkit.Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 0.8f)
            
            return true
        }
        
        private fun getGrappleTarget(player: Player): Location? {
            val rayTrace = player.rayTraceBlocks(30.0) ?: return null
            return rayTrace.hitBlock?.location?.add(0.0, 1.0, 0.0)
        }
        
        private fun launchPlayer(player: Player, target: Location) {
            val direction = target.toVector().subtract(player.location.toVector()).normalize()
            val velocity = direction.multiply(1.5) // Adjust speed
            velocity.y = kotlin.math.max(velocity.y, 0.3) // Minimum upward velocity
            
            player.velocity = velocity
            
            // Add temporary speed boost
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 1)) // 3 seconds
        }
    }
    
    // Wraith Phase Walk
    inner class WraithPhaseAbility : LegendAbility(
        id = "wraith_phase",
        name = "Phase Walk",
        cooldown = 25000, // 25 seconds
        description = "Enter the void to become invulnerable and gain speed"
    ) {
        
        override suspend fun execute(player: Player, gameService: GameService): Boolean {
            // Enter phase mode
            phaseWalkPlayers.add(player.uniqueId)
            
            // Apply effects
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 2)) // 3 seconds speed
            player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60, 4)) // Near invulnerability
            player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 60, 0)) // Visible outline
            
            // Visual effects
            spawnPhaseParticles(player)
            
            // Auto-exit after 3 seconds
            pluginScope.launch {
                delay(2000) // 2 seconds warning
                val warningMessage = messageManager.getMessage("legend.wraith.ability-warning", player)
                player.sendMessage(warningMessage)
                
                delay(1000) // 1 more second
                exitPhaseWalk(player)
            }
            
            return true
        }
        
        private fun spawnPhaseParticles(player: Player) {
            val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                if (phaseWalkPlayers.contains(player.uniqueId)) {
                    player.world.spawnParticle(
                        org.bukkit.Particle.PORTAL,
                        player.location.add(0.0, 1.0, 0.0),
                        10, 0.3, 0.5, 0.3, 0.0
                    )
                }
            }, 0L, 5L)
            
            // Cancel task when phase ends
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                task.cancel()
            }, 60L)
        }
        
        private fun exitPhaseWalk(player: Player) {
            phaseWalkPlayers.remove(player.uniqueId)
            player.removePotionEffect(PotionEffectType.RESISTANCE)
            player.removePotionEffect(PotionEffectType.GLOWING)
        }
    }
    
    // Lifeline Healing Drone
    inner class LifelineHealDroneAbility : LegendAbility(
        id = "lifeline_drone",
        name = "Healing Drone",
        cooldown = 45000, // 45 seconds
        description = "Deploy a healing drone that heals nearby teammates"
    ) {
        
        override suspend fun execute(player: Player, gameService: GameService): Boolean {
            // Remove existing drone if any
            activeDrones[player.uniqueId]?.remove()
            
            // Spawn healing drone
            val drone = spawnHealingDrone(player.location)
            activeDrones[player.uniqueId] = drone
            
            // Start healing task
            startHealingTask(player, drone)
            
            return true
        }
        
        private fun spawnHealingDrone(location: Location): ArmorStand {
            val world = location.world!!
            val drone = world.spawn(location.add(0.0, 1.5, 0.0), ArmorStand::class.java)
            
            drone.isVisible = false
            drone.isSmall = true
            drone.setGravity(false)
            drone.customName = "§aHealing Drone"
            drone.isCustomNameVisible = true
            drone.equipment?.helmet = ItemStack(Material.BEACON)
            
            return drone
        }
        
        private fun startHealingTask(owner: Player, drone: ArmorStand) {
            var duration = 0
            val maxDuration = 300 // 15 seconds (20 ticks per second)
            
            val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                if (duration >= maxDuration || drone.isDead) {
                    removeDrone(owner.uniqueId)
                    return@Runnable
                }
                
                // Heal nearby players
                val nearbyPlayers = drone.location.world!!.getNearbyEntities(drone.location, 5.0, 5.0, 5.0)
                    .filterIsInstance<Player>()
                    .filter { it.gameMode != org.bukkit.GameMode.SPECTATOR }
                
                nearbyPlayers.forEach { player ->
                    if (player.health < player.maxHealth) {
                        player.health = kotlin.math.min(player.maxHealth, player.health + 0.5)
                        
                        // Healing particles
                        player.world.spawnParticle(
                            org.bukkit.Particle.HEART,
                            player.location.add(0.0, 1.0, 0.0),
                            2, 0.3, 0.3, 0.3, 0.0
                        )
                    }
                }
                
                // Drone particles
                drone.world.spawnParticle(
                    org.bukkit.Particle.HAPPY_VILLAGER,
                    drone.location,
                    3, 0.2, 0.2, 0.2, 0.0
                )
                
                duration++
            }, 0L, 1L)
            
            // Auto-remove after duration
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                task.cancel()
                removeDrone(owner.uniqueId)
                
                val message = messageManager.getMessage("legend.lifeline.drone-expired", owner)
                owner.sendMessage(message)
            }, maxDuration.toLong())
        }
        
        private fun removeDrone(playerId: UUID) {
            activeDrones[playerId]?.remove()
            activeDrones.remove(playerId)
        }
    }
    
    fun cleanup() {
        activeDrones.values.forEach { it.remove() }
        activeDrones.clear()
        phaseWalkPlayers.clear()
        abilityCooldowns.clear()
    }
}