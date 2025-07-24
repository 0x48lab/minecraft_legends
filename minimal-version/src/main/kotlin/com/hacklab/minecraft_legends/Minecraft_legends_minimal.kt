package com.hacklab.minecraft_legends

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class Minecraft_legends_minimal : JavaPlugin(), CommandExecutor, Listener {
    
    private val games = mutableMapOf<UUID, BattleRoyaleGame>()
    private var currentGame: BattleRoyaleGame? = null
    
    override fun onEnable() {
        logger.info("Battle Royale plugin enabled!")
        
        // Register commands
        getCommand("br")?.setExecutor(this)
        getCommand("bradmin")?.setExecutor(this)
        
        // Register events
        server.pluginManager.registerEvents(this, this)
        
        logger.info("Battle Royale plugin successfully loaded!")
    }
    
    override fun onDisable() {
        logger.info("Battle Royale plugin disabled!")
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (command.name.lowercase()) {
            "br" -> {
                if (sender !is Player) {
                    sender.sendMessage("§cThis command can only be used by players!")
                    return true
                }
                
                if (args.isEmpty()) {
                    sender.sendMessage("§e=== Battle Royale Commands ===")
                    sender.sendMessage("§e/br join - Join the current game")
                    sender.sendMessage("§e/br leave - Leave the current game")
                    sender.sendMessage("§e/br stats - View your statistics")
                    return true
                }
                
                when (args[0].lowercase()) {
                    "join" -> joinGame(sender)
                    "leave" -> leaveGame(sender)
                    "stats" -> showStats(sender)
                    else -> {
                        sender.sendMessage("§cUnknown subcommand! Use /br for help.")
                    }
                }
                return true
            }
            
            "bradmin" -> {
                if (!sender.hasPermission("br.admin")) {
                    sender.sendMessage("§cYou don't have permission to use this command!")
                    return true
                }
                
                if (args.isEmpty()) {
                    sender.sendMessage("§e=== Battle Royale Admin Commands ===")
                    sender.sendMessage("§e/bradmin create - Create a new game")
                    sender.sendMessage("§e/bradmin start - Start the current game")
                    sender.sendMessage("§e/bradmin stop - Stop the current game")
                    sender.sendMessage("§e/bradmin status - View game status")
                    return true
                }
                
                when (args[0].lowercase()) {
                    "create" -> createGame(sender)
                    "start" -> startGame(sender)
                    "stop" -> stopGame(sender)
                    "status" -> showStatus(sender)
                    else -> {
                        sender.sendMessage("§cUnknown subcommand! Use /bradmin for help.")
                    }
                }
                return true
            }
        }
        return false
    }
    
    private fun joinGame(player: Player) {
        if (currentGame == null) {
            player.sendMessage("§cNo active game to join!")
            return
        }
        
        if (currentGame!!.players.contains(player.uniqueId)) {
            player.sendMessage("§cYou are already in the game!")
            return
        }
        
        if (currentGame!!.state != GameState.WAITING) {
            player.sendMessage("§cThe game has already started!")
            return
        }
        
        currentGame!!.players.add(player.uniqueId)
        player.sendMessage("§aYou joined the Battle Royale game!")
        
        // Broadcast to all players
        currentGame!!.players.forEach { playerId ->
            val p = Bukkit.getPlayer(playerId)
            p?.sendMessage("§e${player.name} joined the game! (${currentGame!!.players.size}/24)")
        }
    }
    
    private fun leaveGame(player: Player) {
        if (currentGame == null || !currentGame!!.players.contains(player.uniqueId)) {
            player.sendMessage("§cYou are not in any game!")
            return
        }
        
        currentGame!!.players.remove(player.uniqueId)
        player.sendMessage("§eYou left the Battle Royale game!")
        
        // Broadcast to remaining players
        currentGame!!.players.forEach { playerId ->
            val p = Bukkit.getPlayer(playerId)
            p?.sendMessage("§e${player.name} left the game! (${currentGame!!.players.size}/24)")
        }
    }
    
    private fun showStats(player: Player) {
        player.sendMessage("§e=== Your Battle Royale Stats ===")
        player.sendMessage("§eGames Played: §f0") // Placeholder
        player.sendMessage("§eWins: §f0") // Placeholder
        player.sendMessage("§eKills: §f0") // Placeholder
        player.sendMessage("§eDamage: §f0") // Placeholder
        player.sendMessage("§7Statistics tracking coming soon!")
    }
    
    private fun createGame(sender: CommandSender) {
        if (currentGame != null) {
            sender.sendMessage("§cA game is already active! Stop it first.")
            return
        }
        
        val gameId = UUID.randomUUID()
        currentGame = BattleRoyaleGame(
            id = gameId,
            worldName = "br_world_$gameId",
            state = GameState.WAITING,
            players = mutableListOf()
        )
        
        games[gameId] = currentGame!!
        sender.sendMessage("§aNew Battle Royale game created! ID: $gameId")
        sender.sendMessage("§ePlayers can now join with /br join")
    }
    
    private fun startGame(sender: CommandSender) {
        if (currentGame == null) {
            sender.sendMessage("§cNo game to start! Create one first.")
            return
        }
        
        if (currentGame!!.state != GameState.WAITING) {
            sender.sendMessage("§cGame is not in waiting state!")
            return
        }
        
        if (currentGame!!.players.size < 2) {
            sender.sendMessage("§cNeed at least 2 players to start!")
            return
        }
        
        currentGame!!.state = GameState.ACTIVE
        sender.sendMessage("§aGame started!")
        
        // Create game world
        createGameWorld(currentGame!!.worldName)
        
        // Teleport all players
        val world = Bukkit.getWorld(currentGame!!.worldName)
        currentGame!!.players.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null && world != null) {
                player.teleport(world.spawnLocation)
                player.sendMessage("§aThe Battle Royale has begun!")
            }
        }
        
        // Start ring shrinking timer
        startRingShrinking()
    }
    
    private fun stopGame(sender: CommandSender) {
        if (currentGame == null) {
            sender.sendMessage("§cNo active game to stop!")
            return
        }
        
        // Teleport players back to main world
        val mainWorld = Bukkit.getWorlds()[0]
        currentGame!!.players.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            player?.teleport(mainWorld.spawnLocation)
            player?.sendMessage("§eThe Battle Royale game has ended!")
        }
        
        currentGame!!.state = GameState.FINISHED
        sender.sendMessage("§aGame stopped!")
        
        currentGame = null
    }
    
    private fun showStatus(sender: CommandSender) {
        if (currentGame == null) {
            sender.sendMessage("§eNo active game")
            return
        }
        
        sender.sendMessage("§e=== Game Status ===")
        sender.sendMessage("§eID: §f${currentGame!!.id}")
        sender.sendMessage("§eState: §f${currentGame!!.state}")
        sender.sendMessage("§ePlayers: §f${currentGame!!.players.size}/24")
        sender.sendMessage("§eWorld: §f${currentGame!!.worldName}")
    }
    
    private fun createGameWorld(worldName: String) {
        val worldCreator = WorldCreator(worldName)
        worldCreator.type(WorldType.FLAT)
        worldCreator.generateStructures(false)
        
        val world = worldCreator.createWorld()
        world?.let {
            it.setSpawnLocation(0, 100, 0)
            logger.info("Created game world: $worldName")
            
            // Place some basic beacons (simple iron blocks for now)
            placeBeacons(it)
        }
    }
    
    private fun placeBeacons(world: World) {
        val beaconLocations = listOf(
            Pair(100, 50),
            Pair(-100, 50),
            Pair(50, 100),
            Pair(-50, -100),
            Pair(0, 150),
            Pair(0, -150)
        )
        
        beaconLocations.forEach { (x, z) ->
            val y = world.getHighestBlockYAt(x, z) + 1
            world.getBlockAt(x, y, z).type = Material.IRON_BLOCK
            world.getBlockAt(x, y + 1, z).type = Material.BEACON
            logger.info("Placed beacon at $x, $y, $z")
        }
    }
    
    private fun startRingShrinking() {
        object : BukkitRunnable() {
            var phase = 0
            
            override fun run() {
                if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
                    cancel()
                    return
                }
                
                phase++
                
                // Broadcast ring warning
                currentGame!!.players.forEach { playerId ->
                    val player = Bukkit.getPlayer(playerId)
                    player?.sendMessage("§cRing shrinking! Phase $phase")
                }
                
                if (phase >= 7) {
                    // End game
                    currentGame!!.players.forEach { playerId ->
                        val player = Bukkit.getPlayer(playerId)
                        player?.sendMessage("§aGame ended! Ring fully closed.")
                    }
                    currentGame!!.state = GameState.FINISHED
                    cancel()
                }
            }
        }.runTaskTimer(this, 200L, 600L) // Start after 10 seconds, repeat every 30 seconds
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.sendMessage("§eWelcome to the Battle Royale server!")
        event.player.sendMessage("§eUse /br to get started!")
    }
}

data class BattleRoyaleGame(
    val id: UUID,
    val worldName: String,
    var state: GameState,
    val players: MutableList<UUID>
)

enum class GameState {
    WAITING,
    ACTIVE,
    FINISHED
}