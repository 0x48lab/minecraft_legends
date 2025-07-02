package com.hacklab.minecraft_legends.presentation.command

import com.hacklab.minecraft_legends.application.service.GameService
import com.hacklab.minecraft_legends.domain.entity.GameSettings
import com.hacklab.minecraft_legends.infrastructure.config.ConfigurationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class BRAdminCommand(
    private val gameService: GameService,
    private val configManager: ConfigurationManager,
    private val pluginScope: CoroutineScope
) : BaseCommand(
    name = "bradmin",
    description = "Battle Royale admin commands",
    usage = "/bradmin <subcommand>",
    permission = "br.admin"
) {
    
    init {
        // Register subcommands
        addSubCommand(CreateGameSubCommand())
        addSubCommand(StartGameSubCommand())
        addSubCommand(EndGameSubCommand())
        addSubCommand(StatusSubCommand())
        addSubCommand(ReloadSubCommand())
        addSubCommand(SetRingPhaseSubCommand())
        addSubCommand(ListPlayersSubCommand())
        addSubCommand(KickPlayerSubCommand())
    }
    
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        sendHelp(sender)
        return true
    }
    
    // Create Game Subcommand
    inner class CreateGameSubCommand : SubCommand(
        name = "create",
        description = "Create a new game",
        usage = "/bradmin create [worldSize] [maxPlayers]"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            val worldSize = args.getOrNull(0)?.toIntOrNull() ?: 3000
            val maxPlayers = args.getOrNull(1)?.toIntOrNull() ?: 24
            
            if (worldSize < 1000 || worldSize > 10000) {
                sender.sendMessage("§cWorld size must be between 1000 and 10000!")
                return true
            }
            
            if (maxPlayers < 2 || maxPlayers > 60) {
                sender.sendMessage("§cMax players must be between 2 and 60!")
                return true
            }
            
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame != null && currentGame.isActive) {
                    sender.sendMessage("§cA game is already active! End it first with /bradmin end")
                    return@launch
                }
                
                val settings = GameSettings(
                    maxPlayers = maxPlayers,
                    minPlayers = 2,
                    teamSize = 3,
                    worldSize = worldSize,
                    respawnBeaconCount = 6,
                )
                
                sender.sendMessage("§eCreating new game...")
                
                val result = gameService.createGame(settings)
                if (result.isSuccess) {
                    val game = result.getOrThrow()
                    sender.sendMessage("§aGame created successfully!")
                    sender.sendMessage("§aGame ID: §f${game.id}")
                    sender.sendMessage("§aWorld: §f${game.worldName}")
                    sender.sendMessage("§aMax Players: §f$maxPlayers")
                    sender.sendMessage("§aWorld Size: §f${worldSize}x${worldSize}")
                } else {
                    sender.sendMessage("§cFailed to create game: ${result.exceptionOrNull()?.message}")
                }
            }
            
            return true
        }
        
        override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
            return when (args.size) {
                1 -> listOf("3000", "5000", "7000")
                2 -> listOf("12", "24", "36", "48")
                else -> emptyList()
            }
        }
    }
    
    // Start Game Subcommand
    inner class StartGameSubCommand : SubCommand(
        name = "start",
        description = "Start the current game",
        usage = "/bradmin start"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame == null) {
                    sender.sendMessage("§cNo game is currently created! Use /bradmin create first")
                    return@launch
                }
                
                if (currentGame.isActive) {
                    sender.sendMessage("§cGame is already active!")
                    return@launch
                }
                
                sender.sendMessage("§eStarting game...")
                
                val result = gameService.startGame(currentGame.id)
                if (result.isSuccess) {
                    sender.sendMessage("§aGame started successfully!")
                    sender.sendMessage("§aPlayers will be teleported to the game world")
                } else {
                    sender.sendMessage("§cFailed to start game: ${result.exceptionOrNull()?.message}")
                }
            }
            
            return true
        }
    }
    
    // End Game Subcommand
    inner class EndGameSubCommand : SubCommand(
        name = "end",
        description = "End the current game",
        usage = "/bradmin end [winnerId]"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame == null) {
                    sender.sendMessage("§cNo game is currently active!")
                    return@launch
                }
                
                val winnerId = args.getOrNull(0)?.let { 
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                }
                
                sender.sendMessage("§eEnding game...")
                
                val result = gameService.endGame(currentGame.id, winnerId)
                if (result.isSuccess) {
                    sender.sendMessage("§aGame ended successfully!")
                    if (winnerId != null) {
                        sender.sendMessage("§aWinner Team: §f$winnerId")
                    }
                } else {
                    sender.sendMessage("§cFailed to end game: ${result.exceptionOrNull()?.message}")
                }
            }
            
            return true
        }
    }
    
    // Status Subcommand
    inner class StatusSubCommand : SubCommand(
        name = "status",
        description = "Show current game status",
        usage = "/bradmin status"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame == null) {
                    sender.sendMessage("§cNo game is currently active")
                    return@launch
                }
                
                sender.sendMessage("§6=== Game Status ===")
                sender.sendMessage("§eGame ID: §f${currentGame.id}")
                sender.sendMessage("§eWorld: §f${currentGame.worldName}")
                sender.sendMessage("§eState: §f${currentGame.state}")
                sender.sendMessage("§eTeams: §f${currentGame.teams.size}")
                sender.sendMessage("§ePlayers: §f${currentGame.teams.sumOf { it.players.size }}")
                sender.sendMessage("§eStarted: §f${currentGame.startTime ?: "Not started"}")
            }
            
            return true
        }
    }
    
    // Reload Subcommand
    inner class ReloadSubCommand : SubCommand(
        name = "reload",
        description = "Reload configuration files",
        usage = "/bradmin reload"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            sender.sendMessage("§eReloading configuration...")
            
            try {
                configManager.reloadConfig()
                sender.sendMessage("§aConfiguration reloaded successfully!")
            } catch (e: Exception) {
                sender.sendMessage("§cFailed to reload configuration: ${e.message}")
                logger.error("Failed to reload configuration", e)
            }
            
            return true
        }
    }
    
    // Set Ring Phase Subcommand
    inner class SetRingPhaseSubCommand : SubCommand(
        name = "setring",
        description = "Force set the ring phase",
        usage = "/bradmin setring <phase>"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            if (args.isEmpty()) {
                sender.sendMessage("§cUsage: $usage")
                return true
            }
            
            val phase = args[0].toIntOrNull()
            if (phase == null || phase < 1 || phase > 7) {
                sender.sendMessage("§cPhase must be a number between 1 and 7!")
                return true
            }
            
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame == null || !currentGame.isActive) {
                    sender.sendMessage("§cNo active game found!")
                    return@launch
                }
                
                // TODO: Implement ring phase control
                sender.sendMessage("§aRing phase set to $phase")
            }
            
            return true
        }
        
        override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
            return if (args.size == 1) {
                (1..7).map { it.toString() }.filter { it.startsWith(args[0]) }
            } else {
                emptyList()
            }
        }
    }
    
    // List Players Subcommand
    inner class ListPlayersSubCommand : SubCommand(
        name = "players",
        description = "List all players in the current game",
        usage = "/bradmin players"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame == null) {
                    sender.sendMessage("§cNo game is currently active!")
                    return@launch
                }
                
                sender.sendMessage("§6=== Players in Game ===")
                currentGame.teams.forEach { team ->
                    sender.sendMessage("§e${team.name}:")
                    team.players.forEach { player ->
                        val status = if (player.isAlive) "§aALIVE" else "§cDEAD"
                        sender.sendMessage("  §f- ${player.name} $status §7(${player.legend})")
                    }
                }
                
                val totalPlayers = currentGame.teams.sumOf { it.players.size }
                val alivePlayers = currentGame.teams.sumOf { team ->
                    team.players.count { it.isAlive }
                }
                sender.sendMessage("§eTotal: §f$totalPlayers players §7($alivePlayers alive)")
            }
            
            return true
        }
    }
    
    // Kick Player Subcommand
    inner class KickPlayerSubCommand : SubCommand(
        name = "kick",
        description = "Kick a player from the game",
        usage = "/bradmin kick <player>"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            if (args.isEmpty()) {
                sender.sendMessage("§cUsage: $usage")
                return true
            }
            
            val targetName = args[0]
            val target = sender.server.getPlayer(targetName)
            
            if (target == null) {
                sender.sendMessage("§cPlayer not found: $targetName")
                return true
            }
            
            pluginScope.launch {
                val result = gameService.leaveGame(target)
                if (result.isSuccess && result.getOrThrow()) {
                    sender.sendMessage("§aKicked ${target.name} from the game")
                    target.sendMessage("§cYou have been kicked from the game")
                } else {
                    sender.sendMessage("§cFailed to kick player: ${target.name} is not in a game")
                }
            }
            
            return true
        }
        
        override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
            return if (args.size == 1) {
                sender.server.onlinePlayers
                    .map { it.name }
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            } else {
                emptyList()
            }
        }
    }
}