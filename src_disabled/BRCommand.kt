package com.hacklab.minecraft_legends.presentation.command

import com.hacklab.minecraft_legends.application.service.GameService
import com.hacklab.minecraft_legends.domain.repository.PlayerRepository
import com.hacklab.minecraft_legends.infrastructure.i18n.MessageManager
import com.hacklab.minecraft_legends.presentation.gui.GUIManager
import com.hacklab.minecraft_legends.domain.entity.LegendInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class BRCommand(
    private val gameService: GameService,
    private val playerRepository: PlayerRepository,
    private val messageManager: MessageManager,
    private val guiManager: GUIManager,
    private val pluginScope: CoroutineScope
) : BaseCommand(
    name = "br",
    description = "Battle Royale player commands",
    usage = "/br <subcommand>",
    playerOnly = true
) {
    
    init {
        // Register subcommands
        addSubCommand(JoinSubCommand())
        addSubCommand(LeaveSubCommand())
        addSubCommand(StatusSubCommand())
        addSubCommand(StatsSubCommand())
        addSubCommand(TeamSubCommand())
        addSubCommand(SelectLegendSubCommand())
        addSubCommand(HelpSubCommand())
    }
    
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        sendHelp(sender)
        return true
    }
    
    // Join Subcommand
    inner class JoinSubCommand : SubCommand(
        name = "join",
        description = "Join the current game",
        usage = "/br join",
        playerOnly = true
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            val player = sender as Player
            
            pluginScope.launch {
                val result = gameService.joinGame(player)
                
                if (result.isSuccess) {
                    val game = result.getOrThrow()
                    if (game != null) {
                        player.sendMessage("§aSuccessfully joined the game!")
                        player.sendMessage("§eWaiting for more players...")
                        player.sendMessage("§ePlayers: §f${game.teams.sumOf { it.players.size }}/${game.settings.maxPlayers}")
                    } else {
                        player.sendMessage("§cNo game is available to join!")
                        player.sendMessage("§eAsk an admin to create a game with /bradmin create")
                    }
                } else {
                    player.sendMessage("§cFailed to join game: ${result.exceptionOrNull()?.message}")
                }
            }
            
            return true
        }
    }
    
    // Leave Subcommand
    inner class LeaveSubCommand : SubCommand(
        name = "leave",
        description = "Leave the current game",
        usage = "/br leave",
        playerOnly = true
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            val player = sender as Player
            
            pluginScope.launch {
                val result = gameService.leaveGame(player)
                
                if (result.isSuccess && result.getOrThrow()) {
                    player.sendMessage("§aYou have left the game")
                } else {
                    player.sendMessage("§cYou are not in a game!")
                }
            }
            
            return true
        }
    }
    
    // Status Subcommand
    inner class StatusSubCommand : SubCommand(
        name = "status",
        description = "Show your game status",
        usage = "/br status",
        playerOnly = true
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            val player = sender as Player
            
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame == null) {
                    player.sendMessage("§cNo game is currently active")
                    return@launch
                }
                
                // Find player's team
                val playerTeam = currentGame.teams.find { team ->
                    team.players.any { it.id == player.uniqueId }
                }
                
                if (playerTeam == null) {
                    player.sendMessage("§cYou are not in the current game!")
                    player.sendMessage("§eUse §f/br join §eto join")
                    return@launch
                }
                
                val gamePlayer = playerTeam.players.find { it.id == player.uniqueId }!!
                
                player.sendMessage("§6=== Your Game Status ===")
                player.sendMessage("§eTeam: §f${playerTeam.name}")
                player.sendMessage("§eLegend: §f${gamePlayer.legend.ifEmpty { "Not selected" }}")
                player.sendMessage("§eStatus: §f${if (gamePlayer.isAlive) "§aAlive" else "§cDead"}")
                player.sendMessage("§eKills: §f${gamePlayer.kills}")
                player.sendMessage("§eDamage: §f${gamePlayer.damage}")
                
                if (playerTeam.players.size > 1) {
                    player.sendMessage("§eTeammates:")
                    playerTeam.players.filter { it.id != player.uniqueId }.forEach { teammate ->
                        val status = if (teammate.isAlive) "§a●" else "§c●"
                        player.sendMessage("  $status §f${teammate.name} §7(${teammate.legend})")
                    }
                }
                
                player.sendMessage("§eGame State: §f${currentGame.state}")
                player.sendMessage("§eTeams Alive: §f${currentGame.teams.count { team -> 
                    team.players.any { it.isAlive }
                }}/${currentGame.teams.size}")
            }
            
            return true
        }
    }
    
    // Stats Subcommand
    inner class StatsSubCommand : SubCommand(
        name = "stats",
        description = "Show your statistics",
        usage = "/br stats [player]",
        playerOnly = true
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            val player = sender as Player
            val targetName = args.getOrNull(0)
            
            pluginScope.launch {
                val targetId = if (targetName != null) {
                    val targetPlayer = sender.server.getPlayer(targetName)
                    if (targetPlayer == null) {
                        player.sendMessage("§cPlayer not found: $targetName")
                        return@launch
                    }
                    targetPlayer.uniqueId
                } else {
                    player.uniqueId
                }
                
                val playerData = playerRepository.findById(targetId)
                if (playerData == null) {
                    player.sendMessage("§cNo statistics found for ${if (targetName != null) targetName else "you"}")
                    return@launch
                }
                
                val stats = playerData.statistics
                val displayName = if (targetId == player.uniqueId) "Your" else "$targetName's"
                
                player.sendMessage("§6=== $displayName Statistics ===")
                player.sendMessage("§eTotal Kills: §f${stats.kills}")
                player.sendMessage("§eTotal Deaths: §f${stats.deaths}")
                player.sendMessage("§eK/D Ratio: §f${
                    if (stats.deaths > 0) 
                        String.format("%.2f", stats.kills.toDouble() / stats.deaths)
                    else 
                        stats.kills.toString()
                }")
                player.sendMessage("§eWins: §f${stats.wins}")
                player.sendMessage("§eTotal Damage: §f${stats.damage}")
                
                // Show titles if any (disabled for now)
                // if (playerData.titles.isNotEmpty()) {
                //     player.sendMessage("§eTitles:")
                //     playerData.titles.forEach { titleId ->
                //         player.sendMessage("  §7- §f$titleId")
                //     }
                // }
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
    
    // Team Subcommand
    inner class TeamSubCommand : SubCommand(
        name = "team",
        description = "Show team information",
        usage = "/br team",
        playerOnly = true
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            val player = sender as Player
            
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame == null) {
                    player.sendMessage("§cNo game is currently active")
                    return@launch
                }
                
                val playerTeam = currentGame.teams.find { team ->
                    team.players.any { it.id == player.uniqueId }
                }
                
                if (playerTeam == null) {
                    player.sendMessage("§cYou are not in a team!")
                    return@launch
                }
                
                player.sendMessage("§6=== Team Information ===")
                player.sendMessage("§eTeam: §f${playerTeam.name}")
                player.sendMessage("§eMembers:")
                
                playerTeam.players.forEach { member ->
                    val isYou = member.id == player.uniqueId
                    val status = if (member.isAlive) "§a●" else "§c●"
                    val you = if (isYou) " §7(You)" else ""
                    player.sendMessage("  $status §f${member.name}$you")
                    player.sendMessage("    §7Legend: ${member.legend.ifEmpty { "Not selected" }}")
                    player.sendMessage("    §7Kills: ${member.kills} | Damage: ${member.damage}")
                }
                
                val totalKills = playerTeam.players.sumOf { it.kills }
                val totalDamage = playerTeam.players.sumOf { it.damage }
                player.sendMessage("§eTeam Stats:")
                player.sendMessage("  §7Total Kills: §f$totalKills")
                player.sendMessage("  §7Total Damage: §f$totalDamage")
            }
            
            return true
        }
    }
    
    // Select Legend Subcommand
    inner class SelectLegendSubCommand : SubCommand(
        name = "legend",
        description = "Select your legend",
        usage = "/br legend <legend>",
        playerOnly = true
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            val player = sender as Player
            
            if (args.isEmpty()) {
                // Open legend selection GUI
                pluginScope.launch {
                    val currentGame = gameService.getCurrentGame()
                    if (currentGame == null || currentGame.isActive) {
                        player.sendMessage("§cYou can only select a legend before the game starts!")
                        return@launch
                    }
                    
                    val availableLegends = listOf(
                        LegendInfo(id = "pathfinder", name = "Pathfinder", description = "Forward Scout"),
                        LegendInfo(id = "wraith", name = "Wraith", description = "Interdimensional Skirmisher"),
                        LegendInfo(id = "lifeline", name = "Lifeline", description = "Combat Medic")
                    )
                    
                    val gui = guiManager.getLegendSelectionGUI { selectedPlayer, legend ->
                        // Handle legend selection
                        pluginScope.launch {
                            // TODO: Update player's legend in game
                            val message = messageManager.getMessage("legend.selected", selectedPlayer, legend.name)
                            selectedPlayer.sendMessage(message)
                        }
                    }
                    
                    gui.openLegendSelection(player, availableLegends)
                }
                return true
            }
            
            val legendName = args[0].lowercase()
            val validLegends = listOf("pathfinder", "wraith", "lifeline")
            
            if (!validLegends.contains(legendName)) {
                player.sendMessage("§cInvalid legend! Choose from: ${validLegends.joinToString(", ")}")
                return true
            }
            
            pluginScope.launch {
                val currentGame = gameService.getCurrentGame()
                if (currentGame == null || currentGame.isActive) {
                    player.sendMessage("§cYou can only select a legend before the game starts!")
                    return@launch
                }
                
                // TODO: Implement legend selection
                player.sendMessage("§aYou have selected §f${legendName.replaceFirstChar { it.uppercase() }}")
                player.sendMessage("§eAbility will be available once the game starts")
            }
            
            return true
        }
        
        override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
            return if (args.size == 1) {
                listOf("pathfinder", "wraith", "lifeline")
                    .filter { it.startsWith(args[0].lowercase()) }
            } else {
                emptyList()
            }
        }
    }
    
    // Help Subcommand
    inner class HelpSubCommand : SubCommand(
        name = "help",
        description = "Show help information",
        usage = "/br help"
    ) {
        override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
            sender.sendMessage("§6=== Battle Royale Mini-Game ===")
            sender.sendMessage("§eA battle royale game mode for Minecraft!")
            sender.sendMessage("")
            sender.sendMessage("§6How to Play:")
            sender.sendMessage("§e1. Join a game with §f/br join")
            sender.sendMessage("§e2. Select your legend with §f/br legend <name>")
            sender.sendMessage("§e3. Wait for the game to start")
            sender.sendMessage("§e4. Survive the shrinking ring!")
            sender.sendMessage("§e5. Be the last team standing!")
            sender.sendMessage("")
            sender.sendMessage("§6Game Features:")
            sender.sendMessage("§e- 3-player teams")
            sender.sendMessage("§e- Shrinking ring with 7 phases")
            sender.sendMessage("§e- Supply boxes with tiered loot")
            sender.sendMessage("§e- Respawn beacons")
            sender.sendMessage("§e- Unique legend abilities")
            sender.sendMessage("")
            sender.sendMessage("§eUse §f/br §eto see all commands")
            
            return true
        }
    }
}