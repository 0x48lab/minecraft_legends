package com.hacklab.minecraft_legends.presentation.listener

import com.hacklab.minecraft_legends.application.service.GameService
import com.hacklab.minecraft_legends.infrastructure.i18n.MessageManager
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.Plugin

class GameEventListener(
    private val plugin: Plugin,
    private val gameService: GameService,
    private val messageManager: MessageManager,
    private val pluginScope: CoroutineScope
) : Listener {
    
    private val logger = LoggerFactory.getLogger()
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        pluginScope.launch {
            val currentGame = gameService.getCurrentGame()
            if (currentGame != null && currentGame.state == com.hacklab.minecraft_legends.domain.entity.GameState.WAITING) {
                // Send game invite
                player.sendMessage("§6§l===========================")
                player.sendMessage("§e  A Battle Royale game is waiting!")
                player.sendMessage("§e  Type §f/br join §eto participate")
                player.sendMessage("§6§l===========================")
            }
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        
        pluginScope.launch {
            // Remove player from game if they're in one
            val result = gameService.leaveGame(player)
            if (result.isSuccess && result.getOrThrow()) {
                logger.info("Player ${player.name} left the game due to disconnect")
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val killer = player.killer
        
        pluginScope.launch {
            val currentGame = gameService.getCurrentGame()
            if (currentGame == null || !currentGame.isActive) return@launch
            
            // Check if player is in the game
            val playerTeam = currentGame.teams.find { team ->
                team.players.any { it.id == player.uniqueId }
            } ?: return@launch
            
            // Custom death message
            val deathMessage = when (event.deathMessage) {
                null -> messageManager.getMessage("death.by-fall", player.locale, player.name)
                else -> {
                    if (killer != null) {
                        messageManager.getMessage("death.by-player", player.locale, player.name, killer.name)
                    } else if (event.deathMessage!!.contains("void") || event.deathMessage!!.contains("fell")) {
                        messageManager.getMessage("death.by-fall", player.locale, player.name)
                    } else {
                        messageManager.getMessage("death.by-ring", player.locale, player.name)
                    }
                }
            }
            
            // Broadcast death message to game participants
            currentGame.teams.forEach { team ->
                team.players.forEach { gamePlayer ->
                    plugin.server.getPlayer(gamePlayer.id)?.sendMessage(deathMessage)
                }
            }
            
            // Update game statistics
            // TODO: Update player statistics (deaths, killer's kills)
            
            event.deathMessage = null // Suppress default message
        }
    }
    
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        
        pluginScope.launch {
            val currentGame = gameService.getCurrentGame()
            if (currentGame == null || !currentGame.isActive) return@launch
            
            // Check if player is in the game
            val playerTeam = currentGame.teams.find { team ->
                team.players.any { it.id == player.uniqueId }
            } ?: return@launch
            
            // Set respawn location to spectator mode position
            // TODO: Implement spectator system
            val world = plugin.server.getWorld(currentGame.worldName) ?: return@launch
            event.respawnLocation = world.spawnLocation
            
            // Put player in spectator mode
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.gameMode = org.bukkit.GameMode.SPECTATOR
                player.sendMessage(messageManager.getMessage("death.eliminated", player))
                
                // Spectate teammate if any are alive
                val aliveTeammate = playerTeam.players
                    .filter { it.isAlive && it.id != player.uniqueId }
                    .mapNotNull { plugin.server.getPlayer(it.id) }
                    .firstOrNull()
                    
                if (aliveTeammate != null) {
                    player.spectatorTarget = aliveTeammate
                }
            })
        }
    }
    
    @EventHandler
    fun onPlayerDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damager as? Player ?: return
        
        pluginScope.launch {
            val currentGame = gameService.getCurrentGame()
            if (currentGame == null || !currentGame.isActive) return@launch
            
            // Check if both players are in the game
            val victimTeam = currentGame.teams.find { team ->
                team.players.any { it.id == victim.uniqueId }
            } ?: return@launch
            
            val attackerTeam = currentGame.teams.find { team ->
                team.players.any { it.id == attacker.uniqueId }
            } ?: return@launch
            
            // Prevent friendly fire
            if (victimTeam.id == attackerTeam.id) {
                event.isCancelled = true
                attacker.sendMessage("§cYou cannot damage teammates!")
                return@launch
            }
            
            // Track damage dealt
            // TODO: Update damage statistics
        }
    }
    
    @EventHandler
    fun onPlayerFallDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        val player = event.entity as? Player ?: return
        
        pluginScope.launch {
            val currentGame = gameService.getCurrentGame()
            if (currentGame == null || !currentGame.isActive) return@launch
            
            // Check if player is in the game
            val playerTeam = currentGame.teams.find { team ->
                team.players.any { it.id == player.uniqueId }
            } ?: return@launch
            
            // Reduce fall damage by 50% for all players in game
            event.damage = event.damage * 0.5
        }
    }
}

class RespawnBeaconListener(
    private val plugin: Plugin,
    private val gameService: GameService,
    private val messageManager: MessageManager,
    private val pluginScope: CoroutineScope
) : Listener {
    
    private val logger = LoggerFactory.getLogger()
    private val beaconCooldowns = mutableMapOf<org.bukkit.Location, Long>()
    
    @EventHandler
    fun onBeaconInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        
        val block = event.clickedBlock ?: return
        val player = event.player
        
        // Check if it's a beacon on iron blocks
        if (block.type != org.bukkit.Material.BEACON) return
        
        val beaconLocation = block.location
        if (!isRespawnBeacon(beaconLocation)) return
        
        event.isCancelled = true
        
        pluginScope.launch {
            val currentGame = gameService.getCurrentGame()
            if (currentGame == null || !currentGame.isActive) {
                player.sendMessage("§cNo active game!")
                return@launch
            }
            
            // Check if player is in the game
            val playerTeam = currentGame.teams.find { team ->
                team.players.any { it.id == player.uniqueId }
            } ?: run {
                player.sendMessage("§cYou are not in the game!")
                return@launch
            }
            
            // Check if player has a dead teammate's banner
            // TODO: Implement banner system
            
            // Check cooldown
            val lastUsed = beaconCooldowns[beaconLocation] ?: 0
            val cooldownRemaining = (lastUsed + 90000 - System.currentTimeMillis()) / 1000 // 90 second cooldown
            
            if (cooldownRemaining > 0) {
                val message = messageManager.getMessage("respawn.beacon-cooldown", player, cooldownRemaining)
                player.sendMessage(message)
                return@launch
            }
            
            // Use beacon
            beaconCooldowns[beaconLocation] = System.currentTimeMillis()
            
            val message = messageManager.getMessage("respawn.beacon-used", player)
            player.sendMessage(message)
            
            // Play effects
            plugin.server.scheduler.runTask(plugin, Runnable {
                beaconLocation.world?.playSound(beaconLocation, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
                beaconLocation.world?.spawnParticle(
                    org.bukkit.Particle.FIREWORK,
                    beaconLocation.clone().add(0.5, 1.0, 0.5),
                    50, 0.5, 1.0, 0.5, 0.1
                )
            })
            
            // TODO: Respawn dead teammate
        }
    }
    
    private fun isRespawnBeacon(location: org.bukkit.Location): Boolean {
        val world = location.world ?: return false
        
        // Check if beacon is on a 3x3 iron block platform
        for (x in -1..1) {
            for (z in -1..1) {
                val checkLocation = location.clone().add(x.toDouble(), -1.0, z.toDouble())
                if (world.getBlockAt(checkLocation).type != org.bukkit.Material.IRON_BLOCK) {
                    return false
                }
            }
        }
        
        return true
    }
}