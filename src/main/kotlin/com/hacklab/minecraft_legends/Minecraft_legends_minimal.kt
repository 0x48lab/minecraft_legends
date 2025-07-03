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
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class Minecraft_legends_minimal : JavaPlugin(), CommandExecutor, Listener {
    
    private val games = mutableMapOf<UUID, BattleRoyaleGame>()
    private var currentGame: BattleRoyaleGame? = null
    private val playerTeams = mutableMapOf<UUID, UUID>() // プレイヤーID -> チームID
    private val teams = mutableMapOf<UUID, Team>() // チームID -> チーム情報
    private val playerStats = mutableMapOf<UUID, PlayerStats>() // プレイヤー統計
    private var ringCenter: org.bukkit.Location? = null
    private var currentRingRadius: Double = 300.0 // 初期リング半径
    private val ringDamageTask = mutableMapOf<UUID, Int>() // プレイヤーごとのダメージタスクID
    private var currentRingPhase: Int = 0 // 現在のリングフェーズ
    
    // サプライボックス関連
    private val supplyBoxes = mutableListOf<org.bukkit.Location>()
    private var supplyBoxTaskId: Int? = null
    
    // レジェンドシステム関連
    private val playerLegends = mutableMapOf<UUID, Legend>() // プレイヤーが選択したレジェンド
    private val legendAbilityCooldowns = mutableMapOf<UUID, Long>() // アビリティクールダウン
    private val legendSelectionDeadline = mutableMapOf<UUID, Long>() // 選択期限
    private val teamLegendSelections = mutableMapOf<UUID, MutableSet<Legend>>() // チーム内のレジェンド選択状況
    private val wraithInvisiblePlayers = mutableSetOf<UUID>() // 透明化中のプレイヤー
    
    // 実績システム関連
    private val playerAchievements = mutableMapOf<UUID, MutableSet<Achievement>>()
    
    // スコアボード関連
    private var gameScoreboard: org.bukkit.scoreboard.Scoreboard? = null
    private var scoreboardTaskId: Int? = null
    
    // キルフィード関連
    private val killFeed = mutableListOf<KillFeedEntry>()
    private val maxKillFeedEntries = 5
    
    // リング縮小タスクID
    private var ringShrinkTaskId: Int? = null
    private var currentRingCenter: org.bukkit.Location? = null // 現在のリング中心
    private var isRingShrinking = false // リングが縮小中か
    
    // リングフェーズ設定（40分ゲーム版）
    private val ringPhases = listOf(
        RingPhase(1, 360, 240, 0.02, 1500.0, 1200.0),  // 6分待機、4分縮小、0.02HP/秒（16分40秒で死亡）
        RingPhase(2, 180, 180, 0.03, 1200.0, 900.0),   // 3分待機、3分縮小、0.03HP/秒（11分で死亡）
        RingPhase(3, 180, 180, 0.05, 900.0, 600.0),    // 3分待機、3分縮小、0.05HP/秒（6分40秒で死亡）
        RingPhase(4, 160, 120, 0.067, 600.0, 400.0),   // 2分40秒待機、2分縮小、0.067HP/秒（5分で死亡）
        RingPhase(5, 160, 80, 0.15, 400.0, 200.0),     // 2分40秒待機、1分20秒縮小、0.15HP/秒（2分13秒で死亡）
        RingPhase(6, 120, 60, 0.4, 200.0, 100.0),      // 2分待機、1分縮小、0.4HP/秒（50秒で死亡）
        RingPhase(7, 60, 240, 1.0, 100.0, 0.0)         // 1分待機、4分縮小、1.0HP/秒（20秒で死亡）
    )
    
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
                    sender.sendMessage("§e/br ability - Use your legend ability")
                    return true
                }
                
                when (args[0].lowercase()) {
                    "join" -> joinGame(sender)
                    "leave" -> leaveGame(sender)
                    "stats" -> showStats(sender)
                    "ability" -> useAbility(sender)
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
                    sender.sendMessage("§e/bradmin beacons - Show beacon locations")
                    sender.sendMessage("§e/bradmin teams - Show team assignments")
                    sender.sendMessage("§e/bradmin setteam <player> <team> - Assign player to team")
                    sender.sendMessage("§e/bradmin ring - Show ring information")
                    sender.sendMessage("§e/bradmin togglering - Toggle ring boundary visibility")
                    sender.sendMessage("§e/bradmin supplybox - Spawn a supply box")
                    sender.sendMessage("§e/bradmin legend <player> <legend> - Set player legend")
                    sender.sendMessage("§e/bradmin border - Show WorldBorder debug info")
                    return true
                }
                
                when (args[0].lowercase()) {
                    "create" -> createGame(sender)
                    "start" -> startGame(sender)
                    "stop" -> stopGame(sender)
                    "status" -> showStatus(sender)
                    "beacons" -> showBeacons(sender)
                    "teams" -> showTeams(sender)
                    "setteam" -> setTeam(sender, args)
                    "ring" -> showRingInfo(sender)
                    "togglering" -> toggleRingVisualization(sender)
                    "supplybox" -> spawnSupplyBox(sender)
                    "legend" -> setPlayerLegend(sender, args)
                    "border" -> showWorldBorderInfo(sender)
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
        
        // Auto-assign to team if not already assigned
        if (!playerTeams.containsKey(player.uniqueId)) {
            autoAssignTeam(player)
        }
        
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
        val stats = playerStats.getOrDefault(player.uniqueId, PlayerStats())
        
        player.sendMessage("§e=== Your Battle Royale Stats ===")
        player.sendMessage("§eGames Played: §f${stats.gamesPlayed}")
        player.sendMessage("§eWins: §f${stats.wins}")
        player.sendMessage("§eKills: §f${stats.kills}")
        player.sendMessage("§eDeaths: §f${stats.deaths}")
        player.sendMessage("§eDamage Dealt: §f${String.format("%.1f", stats.damageDealt)}")
        player.sendMessage("§eTime Survived: §f${formatTime(stats.timeSurvived)}")
        
        val winRate = if (stats.gamesPlayed > 0) {
            (stats.wins.toDouble() / stats.gamesPlayed * 100)
        } else 0.0
        player.sendMessage("§eWin Rate: §f${String.format("%.1f", winRate)}%")
    }
    
    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "${minutes}m ${remainingSeconds}s"
    }
    
    private fun useAbility(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§cOnly players can use abilities!")
            return
        }
        
        if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
            sender.sendMessage("§cNo active game!")
            return
        }
        
        if (!currentGame!!.players.contains(sender.uniqueId)) {
            sender.sendMessage("§cYou are not in the game!")
            return
        }
        
        val legend = playerLegends[sender.uniqueId]
        if (legend == null) {
            sender.sendMessage("§cYou haven't selected a legend!")
            return
        }
        
        // クールダウンチェック
        val lastUsed = legendAbilityCooldowns[sender.uniqueId] ?: 0
        val currentTime = System.currentTimeMillis()
        
        when (legend) {
            Legend.PATHFINDER -> {
                sender.sendMessage("§cPathfinder uses fishing rod for grappling hook!")
            }
            Legend.WRAITH -> {
                if (currentTime - lastUsed < 25000L) { // 25秒クールダウン
                    val remaining = ((25000L - (currentTime - lastUsed)) / 1000)
                    sender.sendMessage("§cVoid walk on cooldown! ${remaining}s remaining")
                    return
                }
                activateWraithAbility(sender)
                legendAbilityCooldowns[sender.uniqueId] = currentTime
            }
            Legend.LIFELINE -> {
                if (currentTime - lastUsed < 30000L) { // 30秒クールダウン
                    val remaining = ((30000L - (currentTime - lastUsed)) / 1000)
                    sender.sendMessage("§cD.O.C. Heal on cooldown! ${remaining}s remaining")
                    return
                }
                activateLifelineAbility(sender)
                legendAbilityCooldowns[sender.uniqueId] = currentTime
            }
            Legend.BANGALORE -> {
                sender.sendMessage("§7Bangalore uses smoke launcher!")
            }
            Legend.GIBRALTAR -> {
                sender.sendMessage("§cGibraltar uses defensive bombardment!")
            }
        }
    }
    
    private fun activateWraithAbility(player: Player) {
        player.sendMessage("§5§lInto the Void activated!")
        player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f)
        
        // 透明化と無敵を付与
        player.addPotionEffect(org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.INVISIBILITY,
            60, // 3秒間
            0,
            false,
            false
        ))
        
        // スピードブースト
        player.addPotionEffect(org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SPEED,
            60,
            1,
            false,
            false
        ))
        
        // ダメージ無効化のため一時的にリストに追加
        wraithInvisiblePlayers.add(player.uniqueId)
        
        // パーティクル効果
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks++ >= 60) {
                    wraithInvisiblePlayers.remove(player.uniqueId)
                    player.sendMessage("§5Exiting the void...")
                    cancel()
                    return
                }
                
                // 紫色のパーティクル
                player.world.spawnParticle(
                    org.bukkit.Particle.PORTAL,
                    player.location.add(0.0, 1.0, 0.0),
                    10,
                    0.5, 0.5, 0.5,
                    0.1
                )
            }
        }.runTaskTimer(this@Minecraft_legends_minimal, 0L, 1L)
    }
    
    private fun activateLifelineAbility(player: Player) {
        player.sendMessage("§a§lD.O.C. Heal Drone deployed!")
        player.playSound(player.location, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
        
        // 自分と周囲のチームメイトを回復
        val teamId = playerTeams[player.uniqueId]
        
        object : BukkitRunnable() {
            var seconds = 0
            override fun run() {
                if (seconds++ >= 5) { // 5秒間
                    player.sendMessage("§aD.O.C. Heal Drone expired")
                    cancel()
                    return
                }
                
                // 自分を回復
                val currentHealth = player.health
                val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                if (currentHealth < maxHealth) {
                    player.health = Math.min(currentHealth + 4.0, maxHealth) // 4HP/秒
                }
                
                // 周囲のチームメイトを回復
                if (teamId != null) {
                    player.getNearbyEntities(10.0, 10.0, 10.0).forEach { entity ->
                        if (entity is Player && playerTeams[entity.uniqueId] == teamId) {
                            val memberHealth = entity.health
                            val memberMaxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                            if (memberHealth < memberMaxHealth) {
                                entity.health = Math.min(memberHealth + 4.0, memberMaxHealth)
                                entity.sendMessage("§aYou are being healed by Lifeline's D.O.C. Drone!")
                            }
                        }
                    }
                }
                
                // 緑色の回復パーティクル
                player.world.spawnParticle(
                    org.bukkit.Particle.HEART,
                    player.location.add(0.0, 2.0, 0.0),
                    5,
                    1.0, 0.5, 1.0,
                    0.0
                )
                
                // 回復音
                player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f)
            }
        }.runTaskTimer(this@Minecraft_legends_minimal, 0L, 20L) // 毎秒実行
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
        
        // チームを初期化
        initializeTeams()
        
        games[gameId] = currentGame!!
        sender.sendMessage("§aNew Battle Royale game created! ID: $gameId")
        sender.sendMessage("§ePlayers can now join with /br join")
        sender.sendMessage("§7Teams: Red, Blue, Green, Yellow (auto-assigned)")
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
        
        if (currentGame!!.players.size < 1) {
            sender.sendMessage("§cNeed at least 1 player to start!")
            return
        }
        
        currentGame!!.state = GameState.ACTIVE
        sender.sendMessage("§aGame started!")
        
        // 統計初期化
        initializeGameStats()
        
        // Create game world
        createGameWorld(currentGame!!.worldName)
        
        // リング初期化とWorldBorder設定を先に行う
        initializeRing()
        
        // WorldBorderを設定（teleportの前に必ず実行）
        val world = Bukkit.getWorlds()[0]
        val worldBorder = world.worldBorder
        
        // WorldBorderの初期設定
        val spawnLocation = world.spawnLocation
        worldBorder.center = spawnLocation
        // 初期リング半径の2倍（直径）を設定
        worldBorder.size = currentRingRadius * 2 // 1500 * 2 = 3000
        worldBorder.damageAmount = 0.0 // WorldBorderのダメージは無効化（カスタム実装を使用）
        worldBorder.damageBuffer = 0.0 // バッファなし
        worldBorder.warningDistance = 200 // 警告距離200ブロック（より見やすく）
        worldBorder.warningTime = 30 // 警告時間30秒
        
        logger.info("WorldBorder initialized - Center: (${spawnLocation.x}, ${spawnLocation.z}), Size: ${worldBorder.size}, Radius: ${currentRingRadius}")
        
        // WorldBorderが設定されたことを確認してからチームをテレポート
        teleportTeamsToRandomLocations()
        
        // 全プレイヤーを初期化
        currentGame!!.players.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null) {
                // プレイヤーを初期化
                initializePlayerForGame(player)
                
                player.sendMessage("§aThe Battle Royale has begun!")
                player.sendMessage("§7Your team has been deployed to a random location!")
                player.sendMessage("§7Find weapons and survive!")
            }
        }
        
        // Start ring shrinking timer
        startRingShrinking()
        
        // カスタムダメージ監視を開始
        startCustomBorderDamage()
        
        // Start supply box spawning
        startSupplyBoxSpawning()
        
        // スコアボードを初期化
        initializeScoreboard()
        
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
        
        // スコアボードのクリーンアップ
        cleanupScoreboard()
        
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
    
    private fun showBeacons(sender: CommandSender) {
        val world = Bukkit.getWorlds()[0]
        val spawnLocation = world.spawnLocation
        val centerX = spawnLocation.blockX
        val centerZ = spawnLocation.blockZ
        val radius = 150
        
        sender.sendMessage("§e=== Beacon Locations ===")
        sender.sendMessage("§eCenter: §f${centerX}, ${centerZ} (spawn)")
        sender.sendMessage("§eRadius: §f${radius} blocks")
        
        val beaconCount = 6
        for (i in 0 until beaconCount) {
            val angle = (2 * Math.PI * i) / beaconCount
            val x = centerX + (radius * kotlin.math.cos(angle)).toInt()
            val z = centerZ + (radius * kotlin.math.sin(angle)).toInt()
            val y = world.getHighestBlockYAt(x, z) + 1
            
            val direction = when (i) {
                0 -> "East"
                1 -> "NorthEast"
                2 -> "NorthWest"
                3 -> "West"
                4 -> "SouthWest"
                5 -> "SouthEast"
                else -> "Unknown"
            }
            
            sender.sendMessage("§7${i + 1}. §e$direction: §f$x, $y, $z")
        }
        
        if (sender is Player) {
            sender.sendMessage("§7Look for glowing beacon beams in the sky!")
        }
    }
    
    private fun initializeTeams() {
        teams.clear()
        playerTeams.clear()
        
        val teamColors = listOf(
            TeamColor.RED to "§cRed Team",
            TeamColor.BLUE to "§9Blue Team", 
            TeamColor.GREEN to "§aGreen Team",
            TeamColor.YELLOW to "§eYellow Team"
        )
        
        teamColors.forEach { (color, name) ->
            val teamId = UUID.randomUUID()
            teams[teamId] = Team(
                id = teamId,
                name = name,
                color = color,
                members = mutableListOf(),
                isAlive = true
            )
        }
    }
    
    private fun showTeams(sender: CommandSender) {
        if (currentGame == null) {
            sender.sendMessage("§cNo active game!")
            return
        }
        
        sender.sendMessage("§e=== Team Assignments ===")
        teams.values.forEach { team ->
            val members = team.members.mapNotNull { playerId ->
                Bukkit.getPlayer(playerId)?.name
            }.joinToString(", ")
            
            val status = if (team.isAlive) "§aAlive" else "§cEliminated"
            sender.sendMessage("${team.name} ($status): §f${members.ifEmpty { "Empty" }}")
        }
    }
    
    private fun setTeam(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§cUsage: /bradmin setteam <player> <team>")
            sender.sendMessage("§cTeams: red, blue, green, yellow")
            return
        }
        
        val playerName = args[1]
        val teamName = args[2].lowercase()
        
        val player = Bukkit.getPlayer(playerName)
        if (player == null) {
            sender.sendMessage("§cPlayer not found: $playerName")
            return
        }
        
        val targetTeam = teams.values.find { 
            it.name.lowercase().contains(teamName) 
        }
        
        if (targetTeam == null) {
            sender.sendMessage("§cInvalid team: $teamName")
            sender.sendMessage("§cAvailable teams: red, blue, green, yellow")
            return
        }
        
        // 既存のチームから削除
        playerTeams[player.uniqueId]?.let { oldTeamId ->
            teams[oldTeamId]?.members?.remove(player.uniqueId)
        }
        
        // 新しいチームに追加
        targetTeam.members.add(player.uniqueId)
        playerTeams[player.uniqueId] = targetTeam.id
        
        sender.sendMessage("§aAssigned ${player.name} to ${targetTeam.name}")
        player.sendMessage("§aYou have been assigned to ${targetTeam.name}!")
    }
    
    private fun autoAssignTeam(player: Player) {
        val availableTeams = teams.values.sortedBy { it.members.size }
        val targetTeam = availableTeams.first()
        
        targetTeam.members.add(player.uniqueId)
        playerTeams[player.uniqueId] = targetTeam.id
        
        player.sendMessage("§aYou have been assigned to ${targetTeam.name}!")
    }
    
    private fun showRingInfo(sender: CommandSender) {
        if (currentGame == null) {
            sender.sendMessage("§cNo active game!")
            return
        }
        
        sender.sendMessage("§9=== Storm Information ===")
        
        val world = Bukkit.getWorlds()[0]
        val worldBorder = world.worldBorder
        
        sender.sendMessage("§eStorm Center: §f${worldBorder.center.blockX}, ${worldBorder.center.blockZ}")
        sender.sendMessage("§eSafe Zone Size: §f${worldBorder.size.toInt()} blocks")
        sender.sendMessage("§eStorm Phase: §f$currentRingPhase / ${ringPhases.size}")
        
        if (currentRingPhase < ringPhases.size) {
            val phase = ringPhases[currentRingPhase]
            sender.sendMessage("§ePhase Status: §f${if (isRingShrinking) "Shrinking" else "Waiting"}")
            sender.sendMessage("§eCurrent Damage: §c${phase.damage} HP/second")
        }
        
        if (sender is Player) {
            val borderDistance = worldBorder.size / 2.0
            val playerDistance = sender.location.distance(worldBorder.center)
            val distanceFromBorder = borderDistance - playerDistance
            
            val status = if (distanceFromBorder > 0) "§aSafe Zone" else "§cDanger Zone"
            sender.sendMessage("§eYour Distance from Border: §f${distanceFromBorder.toInt()} blocks ($status)")
            
            if (distanceFromBorder < 50) {
                sender.sendMessage("§e⚠ Warning: You are close to the storm border!")
            }
        }
        
        // プレイヤーのリング状況
        sender.sendMessage("§e--- Player Ring Status ---")
        var safeCount = 0
        var dangerCount = 0
        
        currentGame!!.players.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null) {
                val borderDistance = worldBorder.size / 2.0
                val playerDistance = player.location.distance(worldBorder.center)
                val distanceFromBorder = borderDistance - playerDistance
                
                if (distanceFromBorder > 0) {
                    safeCount++
                } else {
                    dangerCount++
                }
            }
        }
        
        sender.sendMessage("§aPlayers in safe zone: §f$safeCount")
        sender.sendMessage("§cPlayers in danger zone: §f$dangerCount")
    }
    
    private fun showWorldBorderInfo(sender: CommandSender) {
        val world = Bukkit.getWorlds()[0]
        val worldBorder = world.worldBorder
        
        sender.sendMessage("§9=== WorldBorder Debug Info ===")
        sender.sendMessage("§eCenter: §f${worldBorder.center.blockX}, ${worldBorder.center.blockZ}")
        sender.sendMessage("§eSize (diameter): §f${worldBorder.size}")
        sender.sendMessage("§eRadius: §f${worldBorder.size / 2.0}")
        sender.sendMessage("§eDamage Amount: §f${worldBorder.damageAmount}")
        sender.sendMessage("§eDamage Buffer: §f${worldBorder.damageBuffer}")
        sender.sendMessage("§eWarning Distance: §f${worldBorder.warningDistance}")
        sender.sendMessage("§eWarning Time: §f${worldBorder.warningTime}")
        
        // Game ring info for comparison
        sender.sendMessage("§9=== Game Ring Info ===")
        sender.sendMessage("§eRing Center: §f${currentRingCenter?.blockX ?: "null"}, ${currentRingCenter?.blockZ ?: "null"}")
        sender.sendMessage("§eRing Radius: §f$currentRingRadius")
        sender.sendMessage("§eRing Phase: §f$currentRingPhase")
        sender.sendMessage("§eIs Shrinking: §f$isRingShrinking")
        
        // Check if sizes match
        val expectedSize = currentRingRadius * 2
        val actualSize = worldBorder.size
        if (kotlin.math.abs(expectedSize - actualSize) > 1.0) {
            sender.sendMessage("§c⚠ WARNING: WorldBorder size ($actualSize) doesn't match expected size ($expectedSize)!")
        } else {
            sender.sendMessage("§a✓ WorldBorder size matches game ring size")
        }
        
        // Test player position if sender is a player
        if (sender is Player) {
            val isInside = worldBorder.isInside(sender.location)
            sender.sendMessage("§9=== Player Position Test ===")
            sender.sendMessage("§eYour position: §f${sender.location.blockX}, ${sender.location.blockY}, ${sender.location.blockZ}")
            sender.sendMessage("§eWorldBorder.isInside(): §f$isInside")
            
            // Manual distance calculation for comparison
            val dx = sender.location.x - worldBorder.center.x
            val dz = sender.location.z - worldBorder.center.z
            val distance2D = kotlin.math.sqrt(dx * dx + dz * dz)
            sender.sendMessage("§e2D Distance from center: §f${distance2D.toInt()} blocks")
            sender.sendMessage("§eStatus: §f${if (isInside) "§aINSIDE" else "§cOUTSIDE"} the border")
        }
    }
    
    private fun toggleRingVisualization(sender: CommandSender) {
        if (currentGame == null) {
            sender.sendMessage("§cNo active game!")
            return
        }
        
        sender.sendMessage("§9§l⛈ Storm Ring System Active ⛈")
        sender.sendMessage("§7Players outside the safe zone experience:")
        sender.sendMessage("§7• Heavy rain and storm effects")
        sender.sendMessage("§7• Progressive damage based on phase")
        sender.sendMessage("§7• Lightning strikes in later phases")
        
        sender.sendMessage("")
        sender.sendMessage("§eCurrent storm radius: §f${currentRingRadius.toInt()} blocks")
        sender.sendMessage("§eStorm phase: §f$currentRingPhase / 7")
        
        if (sender is Player) {
            val distance = sender.location.distance(ringCenter!!)
            if (distance > currentRingRadius) {
                sender.sendMessage("§9You are currently in the storm!")
            } else {
                sender.sendMessage("§aYou are in the safe zone")
            }
        }
    }
    
    private fun createGameWorld(worldName: String) {
        // 既存のオーバーワールドを使用
        val world = Bukkit.getWorlds()[0] // メインのオーバーワールドを取得
        logger.info("Using existing overworld for game: $worldName")
        
        // オーバーワールドにビーコンを配置
        placeBeacons(world)
    }
    
    private fun placeBeacons(world: World) {
        val spawnLocation = world.spawnLocation
        val centerX = spawnLocation.blockX
        val centerZ = spawnLocation.blockZ
        val radius = 150 // スポーン地点から150ブロック離れた円形配置
        
        val beaconCount = 6
        for (i in 0 until beaconCount) {
            val angle = (2 * Math.PI * i) / beaconCount
            val x = centerX + (radius * kotlin.math.cos(angle)).toInt()
            val z = centerZ + (radius * kotlin.math.sin(angle)).toInt()
            
            // 地面の高さを取得
            val baseY = world.getHighestBlockYAt(x, z) + 1
            
            // 3x3のビーコンピラミッド（レベル1）を作成
            for (dx in -1..1) {
                for (dz in -1..1) {
                    world.getBlockAt(x + dx, baseY, z + dz).type = Material.IRON_BLOCK
                }
            }
            
            // ビーコンを中央に設置
            world.getBlockAt(x, baseY + 1, z).type = Material.BEACON
            
            // ビーコンに効果を設定（数tick後に実行）
            Bukkit.getScheduler().runTaskLater(this@Minecraft_legends_minimal, Runnable {
                val beacon = world.getBlockAt(x, baseY + 1, z)
                if (beacon.type == Material.BEACON) {
                    val beaconState = beacon.state as? org.bukkit.block.Beacon
                    beaconState?.let { state ->
                        state.setPrimaryEffect(org.bukkit.potion.PotionEffectType.SPEED)
                        state.update()
                    }
                }
            }, 20L) // 1秒後に実行
            
            logger.info("Placed beacon at $x, $baseY, $z (${String.format("%.1f", Math.toDegrees(angle))}°)")
        }
        
        logger.info("Placed $beaconCount beacons in circle around spawn (${centerX}, ${centerZ}) with radius $radius")
    }
    
    private fun initializeRing() {
        val world = Bukkit.getWorlds()[0]
        
        // 初期リング中心はスポーン地点
        ringCenter = world.spawnLocation.clone()
        currentRingCenter = world.spawnLocation.clone()
        currentRingRadius = 1500.0 // 仕様書通り1500ブロックから開始
        currentRingPhase = 0
        isRingShrinking = false
        
        // 全プレイヤーの天候をリセット
        Bukkit.getOnlinePlayers().forEach { player ->
            player.resetPlayerWeather()
        }
        
        logger.info("Storm Ring initialized at ${ringCenter!!.blockX}, ${ringCenter!!.blockZ} with radius $currentRingRadius")
        
    }
    
    private fun startRingShrinking() {
        // 標準のWorldBorder APIを使用
        val world = Bukkit.getWorlds()[0]
        val worldBorder = world.worldBorder
        
        // WorldBorderはすでにstartGameで初期化されているので、
        // ここでは現在の設定を確認するだけ
        logger.info("Starting ring shrinking - Current border size: ${worldBorder.size}, Center: (${worldBorder.center.x}, ${worldBorder.center.z})")
        
        // 現在位置を記録
        ringCenter = worldBorder.center
        currentRingCenter = worldBorder.center
        currentRingRadius = worldBorder.size / 2.0 // 半径は直径の半分
                
        
        // リングフェーズごとの縮小スケジュール
        ringShrinkTaskId = object : BukkitRunnable() {
            var phaseIndex = 0
            var isWaiting = true
            var waitTicks = 0
            var shrinkTicks = 0
            
            override fun run() {
                if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
                    cancel()
                    return
                }
                
                if (phaseIndex >= ringPhases.size) {
                    // ゲーム終了
                    currentGame!!.players.forEach { playerId ->
                        val player = Bukkit.getPlayer(playerId)
                        player?.sendMessage("§cThe ring has fully closed!")
                    }
                    currentGame!!.state = GameState.FINISHED
                    cancel()
                    return
                }
                
                val phase = ringPhases[phaseIndex]
                
                if (isWaiting) {
                    // 待機フェーズ
                    if (waitTicks == 0) {
                        // 待機開始
                        currentGame!!.players.forEach { playerId ->
                            val player = Bukkit.getPlayer(playerId)
                            player?.sendMessage("§eRing will shrink in §c${phase.waitTime}§e seconds!")
                        }
                    }
                    
                    waitTicks++
                    
                    // 残り時間の警告
                    val remainingSeconds = phase.waitTime - (waitTicks / 20)
                    if (remainingSeconds in listOf(60, 30, 10, 5)) {
                        currentGame!!.players.forEach { playerId ->
                            val player = Bukkit.getPlayer(playerId)
                            player?.let {
                                it.sendMessage("§cRing shrinking in ${remainingSeconds} seconds!")
                                it.playSound(it.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f)
                            }
                        }
                    }
                    
                    if (waitTicks >= phase.waitTime * 20) {
                        // 縮小フェーズへ移行
                        isWaiting = false
                        waitTicks = 0
                        isRingShrinking = true
                        
                        // WorldBorderの縮小を開始（視覚効果のみ）
                        worldBorder.setSize(phase.endRadius * 2, phase.shrinkTime.toLong())
                        currentRingRadius = phase.endRadius
                        
                        // WorldBorderのダメージは0のまま（カスタム実装を使用）
                        
                        // 縮小開始を通知
                        currentGame!!.players.forEach { playerId ->
                            val player = Bukkit.getPlayer(playerId)
                            player?.let {
                                it.sendMessage("§c§l⚠ RING IS SHRINKING! ⚠")
                                it.sendTitle("§c⛈ STORM CLOSING ⛈", "§eSafe zone: ${phase.endRadius.toInt()}m", 10, 60, 10)
                                it.playSound(it.location, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f)
                            }
                        }
                    }
                } else {
                    // 縮小フェーズ
                    shrinkTicks++
                    
                    if (shrinkTicks >= phase.shrinkTime * 20) {
                        // 次のフェーズへ
                        phaseIndex++
                        currentRingPhase = phaseIndex
                        isWaiting = true
                        isRingShrinking = false
                        shrinkTicks = 0
                        
                        // WorldBorderのダメージは0のまま（カスタム実装を使用）
                        
                        // フェーズ完了を通知
                        currentGame!!.players.forEach { playerId ->
                            val player = Bukkit.getPlayer(playerId)
                            player?.sendMessage("§aRing has stabilized at §e${phase.endRadius.toInt()}m")
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L).taskId // 毎ティック実行
    }
    
    private fun startCustomBorderDamage() {
        // カスタムダメージシステム - 毎秒実行
        object : BukkitRunnable() {
            override fun run() {
                if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
                    cancel()
                    return
                }
                
                val world = Bukkit.getWorlds()[0]
                val worldBorder = world.worldBorder
                val centerX = worldBorder.center.x
                val centerZ = worldBorder.center.z
                val borderRadius = worldBorder.size / 2.0
                
                // 現在のフェーズのダメージを取得
                val currentPhase = if (currentRingPhase < ringPhases.size) {
                    ringPhases[currentRingPhase]
                } else {
                    null
                }
                
                currentGame!!.players.forEach { playerId ->
                    val player = Bukkit.getPlayer(playerId) ?: return@forEach
                    if (player.isDead) return@forEach
                    
                    val playerX = player.location.x
                    val playerZ = player.location.z
                    
                    // WorldBorderのisInside()メソッドを使用して正確に判定
                    val world = player.world
                    val worldBorder = world.worldBorder
                    val isInsideBorder = worldBorder.isInside(player.location)
                    
                    // プレイヤーとボーダー中心との距離を計算（表示用）
                    val distanceFromCenter = kotlin.math.sqrt(
                        (playerX - centerX) * (playerX - centerX) + 
                        (playerZ - centerZ) * (playerZ - centerZ)
                    )
                    
                    // ボーダー外にいるかチェック
                    if (!isInsideBorder) {
                        // ボーダー外にいる場合、フェーズに応じたダメージを与える
                        if (currentPhase != null && player.gameMode == org.bukkit.GameMode.SURVIVAL) {
                            player.damage(currentPhase.damage)
                            
                            // ダメージ表示
                            val outsideDistance = (distanceFromCenter - borderRadius).toInt()
                            player.sendActionBar("§c⚠ OUTSIDE BORDER - ${outsideDistance}m from safe zone (${currentPhase.damage} damage/s)")
                        }
                    } else {
                        // 安全地帯にいる場合
                        val safeDistance = (borderRadius - distanceFromCenter).toInt()
                        if (safeDistance < 50) {
                            player.sendActionBar("§e⚠ BORDER NEARBY - ${safeDistance}m to border")
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L) // 毎秒（20ティック）実行
    }
    
    // 安全地帯からリング境界を見る
    private fun showRingBoundaryForSafePlayer(player: Player, distanceToEdge: Double) {
        val playerLoc = player.location
        val centerLoc = currentRingCenter ?: return
        
        // プレイヤーから中心への方向
        val directionFromCenter = playerLoc.toVector().subtract(centerLoc.toVector()).normalize()
        
        // リング境界の位置を計算
        val boundaryPoint = centerLoc.clone().add(directionFromCenter.clone().multiply(currentRingRadius))
        boundaryPoint.y = playerLoc.y
        
        // 境界までの距離に応じてパーティクルの密度を調整
        val particleDensity = when {
            distanceToEdge < 20 -> 1  // 非常に近い
            distanceToEdge < 50 -> 3  // 近い
            else -> 5  // やや近い
        }
        
        // 垂直の壁を表示
        for (y in 0..15 step particleDensity) {
            val particleLoc = boundaryPoint.clone().add(0.0, y.toDouble(), 0.0)
            
            // 青紫のパーティクル（安全地帯から見るとき）
            player.spawnParticle(
                org.bukkit.Particle.DUST,
                particleLoc,
                1,
                0.0, 0.0, 0.0,
                0.0,
                org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(100, 149, 237), 2.5f)
            )
        }
        
        // 周囲の境界も表示（近い場合）
        if (distanceToEdge < 30) {
            // 左右にも境界を表示
            val perpendicular = directionFromCenter.clone().crossProduct(org.bukkit.util.Vector(0, 1, 0)).normalize()
            
            for (offset in listOf(-10, -5, 5, 10)) {
                val sidePoint = boundaryPoint.clone().add(perpendicular.clone().multiply(offset.toDouble()))
                
                for (y in 0..10 step 2) {
                    player.spawnParticle(
                        org.bukkit.Particle.DUST,
                        sidePoint.clone().add(0.0, y.toDouble(), 0.0),
                        1,
                        0.0, 0.0, 0.0,
                        0.0,
                        org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(100, 149, 237), 2.0f)
                    )
                }
            }
        }
    }
    
    private fun calculateRingDamage(distanceOutside: Double): Double {
        // 現在のフェーズに対応するダメージを取得
        val damage = if (currentRingPhase == 0) {
            0.05 // ゲーム開始前は非常に少ないダメージ
        } else if (currentRingPhase <= ringPhases.size) {
            ringPhases[currentRingPhase - 1].damage
        } else {
            5.0 // 最終フェーズ後
        }
        
        return damage
    }
    
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.sendMessage("§eWelcome to the Battle Royale server!")
        event.player.sendMessage("§eUse /br to get started!")
        
        // レジェンド選択メニューを表示
        if (currentGame != null && currentGame!!.state == GameState.WAITING) {
            Bukkit.getScheduler().runTaskLater(this, Runnable {
                // 60秒の選択期限を設定
                legendSelectionDeadline[event.player.uniqueId] = System.currentTimeMillis() + 60000
                openLegendSelectionMenu(event.player)
                
                // 60秒後に自動割り当て
                Bukkit.getScheduler().runTaskLater(this@Minecraft_legends_minimal, Runnable {
                    if (!playerLegends.containsKey(event.player.uniqueId)) {
                        autoAssignLegend(event.player)
                    }
                }, 1200L) // 60秒
            }, 20L)
        }
    }
    
    private fun autoAssignLegend(player: Player) {
        val teamId = playerTeams[player.uniqueId]
        val teamSelections = teamId?.let { teamLegendSelections[it] } ?: mutableSetOf()
        
        // 未選択のレジェンドからランダムに選択
        val availableLegends = Legend.values().filter { !teamSelections.contains(it) }
        if (availableLegends.isNotEmpty()) {
            val legend = availableLegends.random()
            playerLegends[player.uniqueId] = legend
            teamSelections.add(legend)
            applyLegendAbilities(player, legend)
            player.sendMessage("§eYou were automatically assigned: ${legend.displayName}")
        }
    }
    
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val drops = event.drops
        
        // ストームトラッカーマップをドロップから削除
        drops.removeIf { item ->
            item.type == Material.FILLED_MAP && 
            item.itemMeta?.hasCustomModelData() == true &&
            item.itemMeta?.customModelData == 1001
        }
        
        if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
            return
        }
        
        if (!currentGame!!.players.contains(player.uniqueId)) {
            return
        }
        
        // キル情報を取得
        val killer = player.killer
        var deathCause = "died"
        
        if (killer != null && currentGame!!.players.contains(killer.uniqueId)) {
            // プレイヤーによるキル
            val killerLegend = playerLegends[killer.uniqueId]
            val victimLegend = playerLegends[player.uniqueId]
            
            updatePlayerStats(killer.uniqueId, kill = true)
            
            // キルフィードに追加
            addKillFeedEntry(
                killerName = killer.name,
                victimName = player.name,
                killerLegend = killerLegend,
                victimLegend = victimLegend,
                weapon = getWeaponName(killer.inventory.itemInMainHand)
            )
            
            // キルした通知
            killer.sendMessage("§aYou eliminated §c${player.name}!")
            killer.playSound(killer.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
            
        } else {
            // 環境死
            val lastDamageCause = player.lastDamageCause
            deathCause = when (lastDamageCause?.cause) {
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID -> "fell out of the world"
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL -> "fell to their death"
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE, 
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.LAVA -> "burned to death"
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.DROWNING -> "drowned"
                else -> {
                    // ストームダメージチェック
                    if (player.location.distance(currentRingCenter ?: player.location) > currentRingRadius) {
                        "died to the storm"
                    } else {
                        "died"
                    }
                }
            }
            
            // 環境死のキルフィード
            addEnvironmentDeathEntry(player.name, playerLegends[player.uniqueId], deathCause)
        }
        
        // デフォルトの死亡メッセージを無効化
        event.deathMessage = null
        
        // プレイヤーをゲームから除外
        currentGame!!.players.remove(player.uniqueId)
        
        // チームから除外
        val teamId = playerTeams[player.uniqueId]
        if (teamId != null) {
            teams[teamId]?.members?.remove(player.uniqueId)
            
            // チームが全員死亡したかチェック
            val team = teams[teamId]
            if (team != null && team.members.isEmpty()) {
                team.isAlive = false
                
                // 全プレイヤーにチーム敗北を通知
                currentGame!!.players.forEach { playerId ->
                    val p = Bukkit.getPlayer(playerId)
                    p?.sendMessage("§c${team.name} has been eliminated!")
                }
            }
        }
        
        player.sendMessage("§cYou have been eliminated from the Battle Royale!")
        player.gameMode = org.bukkit.GameMode.SPECTATOR
        
        // 統計を更新
        updatePlayerStats(player.uniqueId, died = true)
        
        // 残りプレイヤー数を通知
        currentGame!!.players.forEach { playerId ->
            val p = Bukkit.getPlayer(playerId)
            p?.sendMessage("§e${currentGame!!.players.size} players remaining")
        }
        
        // 勝利条件チェック
        checkWinCondition()
    }
    
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        
        if (currentGame != null && currentGame!!.state == GameState.ACTIVE) {
            // ゲーム中の場合はスペクテーターモードでスポーン
            val world = Bukkit.getWorlds()[0]
            val spawnLocation = world.spawnLocation.clone()
            spawnLocation.y = world.getHighestBlockYAt(spawnLocation) + 50.0
            event.respawnLocation = spawnLocation
            
            Bukkit.getScheduler().runTaskLater(this, Runnable {
                player.gameMode = org.bukkit.GameMode.SPECTATOR
                player.sendMessage("§7You are now spectating the Battle Royale!")
                
                // レジェンドアイテムを再配布（スペクテーターでも持っておく）
                giveLegendItems(player, true)
            }, 1L)
        }
    }
    
    private fun checkWinCondition() {
        if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
            return
        }
        
        val aliveTeams = teams.values.filter { it.isAlive && it.members.isNotEmpty() }
        
        when {
            aliveTeams.isEmpty() -> {
                // 引き分け
                currentGame!!.players.forEach { playerId ->
                    val player = Bukkit.getPlayer(playerId)
                    player?.sendMessage("§eGame ended in a draw!")
                }
                endGame()
            }
            aliveTeams.size == 1 -> {
                // 勝利チーム決定
                val winnerTeam = aliveTeams.first()
                
                // 全プレイヤーに勝利を通知
                Bukkit.getOnlinePlayers().forEach { player ->
                    player.sendMessage("§a${winnerTeam.name} wins the Battle Royale!")
                    player.sendTitle("§aVICTORY!", "§e${winnerTeam.name} wins!", 10, 100, 20)
                }
                
                // 勝利チームメンバーに特別メッセージと統計更新
                winnerTeam.members.forEach { playerId ->
                    val player = Bukkit.getPlayer(playerId)
                    player?.sendMessage("§6Congratulations! You are the champions!")
                    updatePlayerStats(playerId, won = true)
                }
                
                endGame()
            }
            currentGame!!.players.size <= 1 -> {
                // 最後の1人または0人
                if (currentGame!!.players.isNotEmpty()) {
                    val lastPlayer = Bukkit.getPlayer(currentGame!!.players.first())
                    lastPlayer?.let { player ->
                        Bukkit.getOnlinePlayers().forEach { p ->
                            p.sendMessage("§a${player.name} wins the Battle Royale!")
                            p.sendTitle("§aVICTORY!", "§e${player.name} wins!", 10, 100, 20)
                        }
                        player.sendMessage("§6Congratulations! You are the champion!")
                    }
                }
                endGame()
            }
        }
    }
    
    private fun endGame() {
        currentGame?.let { game ->
            game.state = GameState.FINISHED
            
            // 統計を確定
            finalizeGameStats()
            
            // 勝利画面を表示
            showVictoryScreen()
            
            // 生存時間のアチーブメントをチェック
            game.players.forEach { playerId ->
                val player = Bukkit.getPlayer(playerId)
                player?.let { checkAchievements(it, AchievementAction.SURVIVE) }
            }
            
            // 5秒後にプレイヤーをメインワールドに戻す
            Bukkit.getScheduler().runTaskLater(this, Runnable {
                // 全プレイヤーをメインワールドに戻す
                val mainWorld = Bukkit.getWorlds()[0]
                Bukkit.getOnlinePlayers().forEach { player ->
                    player.teleport(mainWorld.spawnLocation)
                    player.gameMode = org.bukkit.GameMode.SURVIVAL
                    player.sendMessage("§eThanks for playing Battle Royale!")
                    
                    // スコアボードをリセット
                    player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                }
                
                // ゲームをクリーンアップ
                cleanupGame()
            }, 100L) // 5秒後
        }
    }
    
    private fun showVictoryScreen() {
        val topKillers = getTopKillers(3)
        val winner = if (currentGame!!.players.size == 1) {
            Bukkit.getPlayer(currentGame!!.players.first())
        } else {
            null
        }
        
        // 花火を打ち上げる
        winner?.let { player ->
            for (i in 0..5) {
                Bukkit.getScheduler().runTaskLater(this, Runnable {
                    launchFirework(player.location)
                }, (i * 10).toLong())
            }
        }
        
        // 全プレイヤーに統計を表示
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage("§6§m                                          ")
            player.sendMessage("§6§l       BATTLE ROYALE ENDED       ")
            player.sendMessage("§6§m                                          ")
            
            winner?.let {
                player.sendMessage("§eWinner: §6§l${it.name}")
                val winnerLegend = playerLegends[it.uniqueId]
                if (winnerLegend != null) {
                    player.sendMessage("§eLegend: ${winnerLegend.displayName}")
                }
            }
            
            player.sendMessage("")
            player.sendMessage("§e§lTOP ELIMINATORS:")
            
            topKillers.forEachIndexed { index, (playerId, kills) ->
                val killerName = Bukkit.getOfflinePlayer(playerId).name ?: "Unknown"
                val medal = when (index) {
                    0 -> "§6🥇"
                    1 -> "§7🥈"
                    2 -> "§c🥉"
                    else -> "§7"
                }
                player.sendMessage("$medal §e${index + 1}. §f$killerName §7- §c$kills kills")
            }
            
            // 個人統計
            if (playerStats.containsKey(player.uniqueId)) {
                val stats = playerStats[player.uniqueId]!!
                player.sendMessage("")
                player.sendMessage("§e§lYOUR STATS:")
                player.sendMessage("§7Kills: §f${stats.kills}")
                player.sendMessage("§7Damage Dealt: §f${String.format("%.1f", stats.damageDealt)}")
                player.sendMessage("§7Time Survived: §f${formatTime(stats.timeSurvived)}")
            }
            
            player.sendMessage("§6§m                                          ")
            
            // サウンド再生
            player.playSound(player.location, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        }
    }
    
    private fun getTopKillers(limit: Int): List<Pair<UUID, Int>> {
        return playerStats.entries
            .filter { it.value.gameStartTime > 0 } // 今回のゲームに参加したプレイヤーのみ
            .sortedByDescending { it.value.kills }
            .take(limit)
            .map { it.key to it.value.kills }
    }
    
    private fun launchFirework(location: org.bukkit.Location) {
        val firework = location.world.spawn(location, org.bukkit.entity.Firework::class.java)
        val meta = firework.fireworkMeta
        
        val colors = listOf(
            org.bukkit.Color.RED,
            org.bukkit.Color.YELLOW,
            org.bukkit.Color.BLUE,
            org.bukkit.Color.GREEN,
            org.bukkit.Color.PURPLE
        )
        
        val effect = org.bukkit.FireworkEffect.builder()
            .with(org.bukkit.FireworkEffect.Type.values().random())
            .withColor(colors.random())
            .withFade(colors.random())
            .flicker(true)
            .trail(true)
            .build()
        
        meta.addEffect(effect)
        meta.power = 1 + (Math.random() * 2).toInt()
        firework.fireworkMeta = meta
    }
    
    private fun cleanupGame() {
        // チーム情報をクリア
        teams.clear()
        playerTeams.clear()
        playerLegends.clear()
        legendAbilityCooldowns.clear()
        wraithInvisiblePlayers.clear()
        
        // リング情報をクリア
        ringCenter = null
        currentRingCenter = null
        currentRingRadius = 300.0
        currentRingPhase = 0
        isRingShrinking = false
        ringDamageTask.clear()
        
        // タスクをキャンセル
        ringShrinkTaskId?.let {
            Bukkit.getScheduler().cancelTask(it)
            ringShrinkTaskId = null
        }
        
        supplyBoxTaskId?.let {
            Bukkit.getScheduler().cancelTask(it)
            supplyBoxTaskId = null
        }
        
        // スコアボードをクリーンアップ
        cleanupScoreboard()
        
        
        // キルフィードをクリア
        killFeed.clear()
        
        // 全プレイヤーの天候と視界効果をリセット
        Bukkit.getOnlinePlayers().forEach { player ->
            player.resetPlayerWeather()
            removeStormVisionEffect(player)
        }
        
        // サプライボックスをクリア
        supplyBoxes.clear()
        
        // WorldBorderをリセット
        val world = Bukkit.getWorlds()[0]
        val worldBorder = world.worldBorder
        worldBorder.size = 60000000.0 // デフォルトサイズに戻す
        worldBorder.damageAmount = 0.2 // デフォルトダメージに戻す
        worldBorder.damageBuffer = 5.0 // デフォルトバッファに戻す
        worldBorder.warningDistance = 5 // デフォルト警告距離に戻す
        worldBorder.warningTime = 15 // デフォルト警告時間に戻す
        
        currentGame = null
    }
    
    private fun teleportTeamsToRandomLocations() {
        val world = Bukkit.getWorlds()[0]
        val worldBorder = world.worldBorder
        val centerX = worldBorder.center.x
        val centerZ = worldBorder.center.z
        
        // 現在のWorldBorderサイズを取得（初期は3000）
        val currentBorderSize = worldBorder.size
        val borderRadius = currentBorderSize / 2.0
        
        // 安全マージンを考慮して60%以内に配置（より安全に）
        val safeRadius = borderRadius * 0.6
        
        logger.info("Teleporting teams - Border center: ($centerX, $centerZ), Border size: $currentBorderSize, Safe radius: $safeRadius")
        
        // チームごとにテレポート
        teams.forEach { (teamId, team) ->
            val teamPlayers = team.members.mapNotNull { Bukkit.getPlayer(it) }
            if (teamPlayers.isEmpty()) return@forEach
            
            // 安全な位置が見つかるまでリトライ
            var attempts = 0
            var teamX: Double
            var teamZ: Double
            
            do {
                // ランダムな角度を生成
                val angle = Math.random() * 2 * Math.PI
                
                // ランダムな距離（中心から離れた位置だが、境界内に確実に収まる）
                val distance = safeRadius * (0.3 + Math.random() * 0.6) // 安全半径の30%〜90%の範囲
                
                // チームのスポーン位置を計算
                teamX = centerX + distance * Math.cos(angle)
                teamZ = centerZ + distance * Math.sin(angle)
                
                // 境界内かチェック
                val distanceFromCenter = Math.sqrt(Math.pow(teamX - centerX, 2.0) + Math.pow(teamZ - centerZ, 2.0))
                
                attempts++
                if (distanceFromCenter < borderRadius - 50) { // 50ブロックの余裕を持って境界内
                    break
                }
            } while (attempts < 10)
            
            // チームメンバーを近くに配置
            teamPlayers.forEachIndexed { index, player ->
                // チームメンバーは10ブロック以内に配置
                val offsetX = (Math.random() - 0.5) * 10
                val offsetZ = (Math.random() - 0.5) * 10
                
                val x = teamX + offsetX
                val z = teamZ + offsetZ
                
                // 最終的な位置が境界内か確認
                val finalDistance = Math.sqrt(Math.pow(x - centerX, 2.0) + Math.pow(z - centerZ, 2.0))
                
                // 境界外の場合は補正
                val finalX = if (finalDistance > borderRadius - 30) {
                    centerX + (x - centerX) * (borderRadius - 30) / finalDistance
                } else x
                
                val finalZ = if (finalDistance > borderRadius - 30) {
                    centerZ + (z - centerZ) * (borderRadius - 30) / finalDistance
                } else z
                
                val y = world.getHighestBlockYAt(finalX.toInt(), finalZ.toInt()) + 2.0
                
                val spawnLocation = org.bukkit.Location(world, finalX, y, finalZ)
                player.teleport(spawnLocation)
                
                // チーム情報と境界までの距離を表示
                val spawnDistance = Math.sqrt(Math.pow(finalX - centerX, 2.0) + Math.pow(finalZ - centerZ, 2.0))
                val distanceToBorder = borderRadius - spawnDistance
                
                player.sendMessage("§aYou have been deployed with team §e${team.name}§a!")
                player.sendMessage("§7Location: X:${finalX.toInt()} Y:${y.toInt()} Z:${finalZ.toInt()}")
                player.sendMessage("§7Distance to border: §e${distanceToBorder.toInt()} blocks")
            }
            
            logger.info("Team ${team.name} deployed at X:${teamX.toInt()} Z:${teamZ.toInt()} (${attempts} attempts)")
        }
    }
    
    private fun initializePlayerForGame(player: Player) {
        // プレイヤーのインベントリをクリア
        player.inventory.clear()
        player.inventory.setHelmet(null)
        player.inventory.setChestplate(null)
        player.inventory.setLeggings(null)
        player.inventory.setBoots(null)
        
        // HP（体力）を最大に
        player.health = 20.0
        
        // 満腹度を最大に
        player.foodLevel = 20
        player.saturation = 20.0f
        
        // その他のステータスをリセット
        player.fireTicks = 0
        player.freezeTicks = 0
        player.exp = 0f
        player.level = 0
        player.gameMode = org.bukkit.GameMode.SURVIVAL
        
        // ポーション効果をクリア
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }
        
        // テスト用：全てのレジェンドスキルアイテムを配布
        giveLegendItems(player, true)
    }
    
    // レジェンドアイテムを配布
    private fun giveLegendItems(player: Player, allLegends: Boolean = false) {
        if (allLegends) {
            // テスト用：全てのレジェンドアイテムを配布
            giveLegendItem(player, Legend.PATHFINDER)
            giveLegendItem(player, Legend.WRAITH)
            giveLegendItem(player, Legend.LIFELINE)
            giveLegendItem(player, Legend.BANGALORE)
            giveLegendItem(player, Legend.GIBRALTAR)
        } else {
            // 通常：選択されたレジェンドのアイテムのみ配布
            val legend = playerLegends[player.uniqueId] ?: return
            giveLegendItem(player, legend)
        }
    }
    
    // 個別のレジェンドアイテムを配布
    private fun giveLegendItem(player: Player, legend: Legend) {
        val item = when (legend) {
            Legend.PATHFINDER -> ItemStack(Material.FISHING_ROD).apply {
                val meta = itemMeta!!
                meta.setDisplayName("§b§lGrappling Hook")
                meta.lore = listOf(
                    "§7Pathfinder's tactical ability",
                    "§7Right-click to grapple!",
                    "§7Cooldown: 15 seconds"
                )
                meta.isUnbreakable = true
                itemMeta = meta
            }
            
            Legend.WRAITH -> ItemStack(Material.ENDER_EYE).apply {
                val meta = itemMeta!!
                meta.setDisplayName("§5§lVoid Walk")
                meta.lore = listOf(
                    "§7Wraith's tactical ability",
                    "§7Right-click to phase!",
                    "§7Duration: 3 seconds",
                    "§7Cooldown: 25 seconds"
                )
                itemMeta = meta
            }
            
            Legend.LIFELINE -> ItemStack(Material.GOLDEN_APPLE).apply {
                val meta = itemMeta!!
                meta.setDisplayName("§a§lD.O.C. Heal Drone")
                meta.lore = listOf(
                    "§7Lifeline's tactical ability",
                    "§7Right-click to deploy healing drone!",
                    "§7Heals nearby players",
                    "§7Cooldown: 45 seconds"
                )
                itemMeta = meta
            }
            
            Legend.BANGALORE -> ItemStack(Material.IRON_BLOCK).apply {
                val meta = itemMeta!!
                meta.setDisplayName("§7§lDome Shield")
                meta.lore = listOf(
                    "§7Bangalore's tactical ability",
                    "§7Right-click to deploy dome shield!",
                    "§7Creates protective dome",
                    "§7Cooldown: 30 seconds"
                )
                itemMeta = meta
            }
            
            Legend.GIBRALTAR -> ItemStack(Material.FIRE_CHARGE).apply {
                val meta = itemMeta!!
                meta.setDisplayName("§c§lDefensive Bombardment")
                meta.lore = listOf(
                    "§7Gibraltar's tactical ability",
                    "§7Right-click to call airstrike!",
                    "§7Marks location for bombardment",
                    "§7Cooldown: 120 seconds"
                )
                itemMeta = meta
            }
        }
        
        // アイテムにカスタムタグを追加（ドロップ防止用）
        item.itemMeta = item.itemMeta?.apply {
            persistentDataContainer.set(
                org.bukkit.NamespacedKey(this@Minecraft_legends_minimal, "legend_item"),
                org.bukkit.persistence.PersistentDataType.STRING,
                legend.name
            )
        }
        
        player.inventory.addItem(item)
    }
    
    
    
    // Note: This old beacon menu is replaced by the respawn beacon system below
    
    private fun spawnLoot(location: org.bukkit.Location) {
        val world = location.world ?: return
        
        val lootItems = listOf(
            ItemStack(Material.DIAMOND_SWORD),
            ItemStack(Material.IRON_CHESTPLATE),
            ItemStack(Material.GOLDEN_APPLE, 2),
            ItemStack(Material.ENDER_PEARL, 1),
            ItemStack(Material.CROSSBOW),
            ItemStack(Material.SPECTRAL_ARROW, 32),
            ItemStack(Material.SHIELD),
            ItemStack(Material.TOTEM_OF_UNDYING)
        )
        
        val randomItem = lootItems.random()
        world.dropItem(location.add(0.0, 1.0, 0.0), randomItem)
        
        // 周囲のプレイヤーに通知
        world.getNearbyEntities(location, 50.0, 50.0, 50.0)
            .filterIsInstance<Player>()
            .filter { currentGame!!.players.contains(it.uniqueId) }
            .forEach { player ->
                player.sendMessage("§6A rare item has appeared nearby!")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
            }
    }
    
    
    private fun updatePlayerStats(
        playerId: UUID, 
        won: Boolean = false, 
        died: Boolean = false, 
        kill: Boolean = false,
        damage: Double = 0.0
    ) {
        val stats = playerStats.getOrDefault(playerId, PlayerStats())
        
        if (won) {
            stats.wins++
            stats.gamesPlayed++
        }
        if (died) {
            stats.deaths++
            if (!won) stats.gamesPlayed++ // ゲーム参加をカウント（勝利時は既にカウント済み）
        }
        if (kill) {
            stats.kills++
        }
        if (damage > 0) {
            stats.damageDealt += damage
        }
        
        playerStats[playerId] = stats
    }
    
    private fun initializeGameStats() {
        // ゲーム開始時に参加プレイヤーの統計を初期化
        currentGame?.players?.forEach { playerId ->
            val stats = playerStats.getOrDefault(playerId, PlayerStats())
            stats.gameStartTime = System.currentTimeMillis()
            playerStats[playerId] = stats
        }
    }
    
    private fun finalizeGameStats() {
        // ゲーム終了時に生存時間を計算
        val currentTime = System.currentTimeMillis()
        currentGame?.players?.forEach { playerId ->
            val stats = playerStats[playerId]
            if (stats != null && stats.gameStartTime > 0) {
                val survivalTime = (currentTime - stats.gameStartTime) / 1000
                stats.timeSurvived += survivalTime
                stats.gameStartTime = 0 // リセット
            }
        }
    }
    
    // サプライボックスシステム
    private fun startSupplyBoxSpawning() {
        supplyBoxTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, {
            if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
                return@scheduleSyncRepeatingTask
            }
            
            // ランダムな位置にサプライボックスをスポーン
            val world = Bukkit.getWorlds()[0]
            val x = ringCenter!!.blockX + (Math.random() - 0.5) * currentRingRadius * 2
            val z = ringCenter!!.blockZ + (Math.random() - 0.5) * currentRingRadius * 2
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1
            
            val location = org.bukkit.Location(world, x, y.toDouble(), z)
            
            // サプライボックスを設置
            spawnSupplyBoxAt(location)
            
            // 全プレイヤーに通知
            currentGame!!.players.forEach { playerId ->
                val player = Bukkit.getPlayer(playerId)
                player?.sendMessage("§b§l📦 Supply Box has landed!")
                player?.playSound(player.location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f)
            }
        }, 1200L, 1200L) // 1分ごとにスポーン
    }
    
    private fun spawnSupplyBoxAt(location: org.bukkit.Location) {
        val world = location.world ?: return
        
        // サプライボックスのベーコンを設置
        location.block.type = Material.YELLOW_SHULKER_BOX
        
        // サプライボックスの周りに光源を設置
        location.clone().add(1.0, 0.0, 0.0).block.type = Material.GLOWSTONE
        location.clone().add(-1.0, 0.0, 0.0).block.type = Material.GLOWSTONE
        location.clone().add(0.0, 0.0, 1.0).block.type = Material.GLOWSTONE
        location.clone().add(0.0, 0.0, -1.0).block.type = Material.GLOWSTONE
        
        // シュルカーボックスにアイテムを追加
        val shulkerBox = location.block.state as? org.bukkit.block.ShulkerBox
        shulkerBox?.let { box ->
            val inventory = box.inventory
            
            // レアアイテムプール
            val tierRoll = Math.random()
            val items = when {
                tierRoll < 0.1 -> listOf( // レジェンダリー (10%)
                    ItemStack(Material.NETHERITE_SWORD),
                    ItemStack(Material.NETHERITE_CHESTPLATE),
                    ItemStack(Material.TOTEM_OF_UNDYING, 2),
                    ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3),
                    ItemStack(Material.ENDER_PEARL, 8)
                )
                tierRoll < 0.35 -> listOf( // エピック (25%)
                    ItemStack(Material.DIAMOND_SWORD),
                    ItemStack(Material.DIAMOND_HELMET),
                    ItemStack(Material.DIAMOND_BOOTS),
                    ItemStack(Material.GOLDEN_APPLE, 5),
                    ItemStack(Material.ENDER_PEARL, 4)
                )
                else -> listOf( // コモン (65%)
                    ItemStack(Material.IRON_SWORD),
                    ItemStack(Material.IRON_HELMET),
                    ItemStack(Material.CROSSBOW),
                    ItemStack(Material.ARROW, 32),
                    ItemStack(Material.GOLDEN_APPLE, 2),
                    ItemStack(Material.SHIELD)
                )
            }
            
            // 回復アイテムを追加
            items.plus(listOf(
                ItemStack(Material.COOKED_BEEF, 16),
                ItemStack(Material.POTION).apply {
                    val meta = itemMeta as? org.bukkit.inventory.meta.PotionMeta
                    meta?.basePotionType = org.bukkit.potion.PotionType.HEALING
                    itemMeta = meta
                }
            )).forEach { item ->
                inventory.addItem(item)
            }
            
            
            box.update()
        }
        
        supplyBoxes.add(location)
        
        // パーティクルエフェクト
        object : BukkitRunnable() {
            var count = 0
            override fun run() {
                if (count++ > 200 || location.block.type != Material.YELLOW_SHULKER_BOX) {
                    cancel()
                    return
                }
                
                // 光るパーティクル
                world.spawnParticle(
                    org.bukkit.Particle.END_ROD,
                    location.clone().add(0.5, 0.5, 0.5),
                    2,
                    0.5, 0.5, 0.5,
                    0.02
                )
                
                // 黄色のパーティクル
                world.spawnParticle(
                    org.bukkit.Particle.DUST,
                    location.clone().add(0.5, 1.5, 0.5),
                    3,
                    0.3, 0.3, 0.3,
                    0.0,
                    org.bukkit.Particle.DustOptions(org.bukkit.Color.YELLOW, 2.0f)
                )
            }
        }.runTaskTimer(this, 0L, 5L)
    }
    
    private fun spawnSupplyBox(sender: CommandSender) {
        if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
            sender.sendMessage("§cNo active game!")
            return
        }
        
        if (sender is Player) {
            val location = sender.location
            spawnSupplyBoxAt(location)
            sender.sendMessage("§aSupply box spawned at your location!")
        } else {
            sender.sendMessage("§cThis command must be used by a player!")
        }
    }
    
    // レジェンドシステム
    private fun openLegendSelectionMenu(player: Player) {
        val inventory = Bukkit.createInventory(null, 27, "§6Select Your Legend")
        
        val teamId = playerTeams[player.uniqueId]
        val teamSelections = teamId?.let { teamLegendSelections[it] } ?: mutableSetOf()
        
        // パスファインダー
        val pathfinderItem = ItemStack(Material.FISHING_ROD).apply {
            val meta = itemMeta!!
            meta.setDisplayName("§b§lPathfinder")
            meta.lore = listOf(
                "§7Grappling hook specialist",
                "§e[Tactical] Grappling Hook",
                "§7Use fishing rod to grapple",
                "§7Cooldown: 10 seconds",
                if (teamSelections.contains(Legend.PATHFINDER)) "§c✖ Already taken by teammate" else "§a✓ Available"
            )
            itemMeta = meta
        }
        
        // レイス
        val wraithItem = ItemStack(Material.ENDER_PEARL).apply {
            val meta = itemMeta!!
            meta.setDisplayName("§5§lWraith")
            meta.lore = listOf(
                "§7Interdimensional skirmisher",
                "§e[Tactical] Into the Void",
                "§73 seconds of invisibility",
                "§7Cannot attack while invisible",
                "§7Cooldown: 25 seconds",
                if (teamSelections.contains(Legend.WRAITH)) "§c✖ Already taken by teammate" else "§a✓ Available"
            )
            itemMeta = meta
        }
        
        // ライフライン
        val lifelineItem = ItemStack(Material.GOLDEN_APPLE).apply {
            val meta = itemMeta!!
            meta.setDisplayName("§a§lLifeline")
            meta.lore = listOf(
                "§7Combat medic",
                "§e[Tactical] D.O.C. Heal",
                "§7Heals yourself and nearby allies",
                "§74 HP/second for 5 seconds",
                "§7Cooldown: 30 seconds",
                if (teamSelections.contains(Legend.LIFELINE)) "§c✖ Already taken by teammate" else "§a✓ Available"
            )
            itemMeta = meta
        }
        
        inventory.setItem(11, pathfinderItem)
        inventory.setItem(13, wraithItem)
        inventory.setItem(15, lifelineItem)
        
        // 時間制限の表示
        val remainingTime = legendSelectionDeadline[player.uniqueId]?.let { deadline ->
            ((deadline - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        } ?: 60
        
        val timerItem = ItemStack(Material.CLOCK).apply {
            val meta = itemMeta!!
            meta.setDisplayName("§e§lTime Remaining: ${remainingTime}s")
            meta.lore = listOf(
                "§7Select your legend before time runs out!",
                "§7Unselected players will be assigned randomly"
            )
            itemMeta = meta
        }
        inventory.setItem(22, timerItem)
        
        player.openInventory(inventory)
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // レジェンド選択メニューの処理
        if (event.view.title == "§6Select Your Legend") {
            event.isCancelled = true
            
            val clickedItem = event.currentItem ?: return
            val legend = when (clickedItem.type) {
                Material.FISHING_ROD -> Legend.PATHFINDER
                Material.ENDER_PEARL -> Legend.WRAITH
                Material.GOLDEN_APPLE -> Legend.LIFELINE
                else -> return
            }
            
            // チーム内重複チェック
            val teamId = playerTeams[player.uniqueId]
            if (teamId != null) {
                val teamSelections = teamLegendSelections.getOrPut(teamId) { mutableSetOf() }
                if (teamSelections.contains(legend)) {
                    player.sendMessage("§cThis legend is already taken by a teammate!")
                    player.closeInventory()
                    return
                }
                
                // 以前の選択を削除
                val previousLegend = playerLegends[player.uniqueId]
                if (previousLegend != null) {
                    teamSelections.remove(previousLegend)
                }
                
                teamSelections.add(legend)
            }
            
            playerLegends[player.uniqueId] = legend
            player.closeInventory()
            player.sendMessage("§aYou selected: §e${legend.displayName}")
            
            // レジェンドの能力を適用
            applyLegendAbilities(player, legend)
            
            return
        }
        
        if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
            return
        }
        
        if (!currentGame!!.players.contains(player.uniqueId)) {
            return
        }
        
        // インベントリ制限（特定のアイテムの使用を制限）
        val clickedItem = event.currentItem
        if (clickedItem?.type == Material.ELYTRA) {
            event.isCancelled = true
            player.sendMessage("§cElytra is not allowed in Battle Royale!")
        }
    }
    
    private fun applyLegendAbilities(player: Player, legend: Legend) {
        // 既存のエフェクトをクリア
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }
        
        when (legend) {
            Legend.PATHFINDER -> {
                // 釣竿を配布
                if (!player.inventory.contains(Material.FISHING_ROD)) {
                    val rod = ItemStack(Material.FISHING_ROD).apply {
                        val meta = itemMeta!!
                        meta.setDisplayName("§b§lGrappling Hook")
                        meta.lore = listOf(
                            "§7Pathfinder's tactical ability",
                            "§7Right-click to grapple"
                        )
                        meta.isUnbreakable = true
                        itemMeta = meta
                    }
                    player.inventory.addItem(rod)
                }
                player.sendMessage("§bPathfinder abilities activated! Use your grappling hook wisely.")
            }
            Legend.WRAITH -> {
                player.sendMessage("§5Wraith abilities activated! Use /br ability to enter the void.")
            }
            Legend.LIFELINE -> {
                player.sendMessage("§aLifeline abilities activated! Use /br ability to deploy D.O.C. heal drone.")
            }
            Legend.BANGALORE -> {
                player.sendMessage("§7Bangalore abilities activated! Professional soldier ready.")
            }
            Legend.GIBRALTAR -> {
                player.sendMessage("§cGibraltar abilities activated! Defensive bombardment ready.")
            }
        }
        
        player.health = player.maxHealth
    }
    
    private fun setPlayerLegend(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§cUsage: /bradmin legend <player> <legend>")
            sender.sendMessage("§cLegends: pathfinder, wraith, lifeline")
            return
        }
        
        val playerName = args[1]
        val legendName = args[2].lowercase()
        
        val player = Bukkit.getPlayer(playerName)
        if (player == null) {
            sender.sendMessage("§cPlayer not found: $playerName")
            return
        }
        
        val legend = when (legendName) {
            "pathfinder" -> Legend.PATHFINDER
            "wraith" -> Legend.WRAITH
            "lifeline" -> Legend.LIFELINE
            else -> {
                sender.sendMessage("§cInvalid legend: $legendName")
                return
            }
        }
        
        playerLegends[player.uniqueId] = legend
        applyLegendAbilities(player, legend)
        
        sender.sendMessage("§aSet ${player.name}'s legend to ${legend.displayName}")
        player.sendMessage("§aYour legend has been set to: ${legend.displayName}")
    }
    
    // チームチャット機能を追加
    @EventHandler
    fun onPlayerChat(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
        val player = event.player
        val teamId = playerTeams[player.uniqueId] ?: return
        val team = teams[teamId] ?: return
        
        // チームチャットのプレフィックスをチェック
        if (event.message.startsWith("!")) {
            event.isCancelled = true
            val teamMessage = event.message.substring(1)
            
            // チームメンバーにのみ送信
            team.members.forEach { memberId ->
                val member = Bukkit.getPlayer(memberId)
                member?.sendMessage("${team.name} §7${player.name}: §f$teamMessage")
            }
        }
    }
    
    // アチーブメントシステム
    private fun checkAchievements(player: Player, action: AchievementAction) {
        val achievements = playerAchievements.getOrPut(player.uniqueId) { mutableSetOf() }
        val stats = playerStats[player.uniqueId] ?: return
        
        // アチーブメントのチェック
        when (action) {
            AchievementAction.KILL -> {
                if (stats.kills >= 10 && !achievements.contains(Achievement.KILLER_10)) {
                    achievements.add(Achievement.KILLER_10)
                    grantAchievement(player, Achievement.KILLER_10)
                }
                if (stats.kills >= 50 && !achievements.contains(Achievement.KILLER_50)) {
                    achievements.add(Achievement.KILLER_50)
                    grantAchievement(player, Achievement.KILLER_50)
                }
            }
            AchievementAction.WIN -> {
                if (stats.wins >= 1 && !achievements.contains(Achievement.FIRST_WIN)) {
                    achievements.add(Achievement.FIRST_WIN)
                    grantAchievement(player, Achievement.FIRST_WIN)
                }
                if (stats.wins >= 10 && !achievements.contains(Achievement.CHAMPION)) {
                    achievements.add(Achievement.CHAMPION)
                    grantAchievement(player, Achievement.CHAMPION)
                }
            }
            AchievementAction.SURVIVE -> {
                if (stats.timeSurvived >= 1800 && !achievements.contains(Achievement.SURVIVOR)) { // 30分
                    achievements.add(Achievement.SURVIVOR)
                    grantAchievement(player, Achievement.SURVIVOR)
                }
            }
        }
    }
    
    private fun grantAchievement(player: Player, achievement: Achievement) {
        player.sendMessage("§6§lâ★ ACHIEVEMENT UNLOCKED!")
        player.sendMessage("§e${achievement.displayName}")
        player.sendMessage("§7${achievement.description}")
        
        // タイトルとして表示
        player.sendTitle("§6â★ Achievement!", "§e${achievement.displayName}", 10, 70, 20)
        player.playSound(player.location, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        
        // 全プレイヤーに通知
        Bukkit.getOnlinePlayers().forEach { p ->
            if (p != player) {
                p.sendMessage("§e${player.name} §7earned achievement: §e${achievement.displayName}")
            }
        }
    }
    
    // パスファインダーのグラップリングフック
    @EventHandler
    fun onProjectileLaunch(event: org.bukkit.event.entity.ProjectileLaunchEvent) {
        val projectile = event.entity
        if (projectile !is org.bukkit.entity.FishHook) return
        
        val shooter = projectile.shooter as? Player ?: return
        val legend = playerLegends[shooter.uniqueId]
        
        if (legend == Legend.PATHFINDER) {
            // クールダウンチェック
            val lastUsed = legendAbilityCooldowns[shooter.uniqueId] ?: 0
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastUsed < 10000L) { // 10秒
                val remaining = ((10000L - (currentTime - lastUsed)) / 1000)
                shooter.sendMessage("§cGrappling hook on cooldown! ${remaining}s remaining")
                event.isCancelled = true
                return
            }
            
            // グラップリングフックとして機能させる
            Bukkit.getScheduler().runTaskLater(this, Runnable {
                if (projectile.isValid && projectile.state == org.bukkit.entity.FishHook.HookState.BOBBING) {
                    val hookLocation = projectile.location
                    val playerLocation = shooter.location
                    
                    // プレイヤーをフックの位置に向かって引き寄せる
                    val direction = hookLocation.toVector().subtract(playerLocation.toVector()).normalize()
                    shooter.velocity = direction.multiply(2.5) // 速度調整
                    
                    shooter.sendMessage("§bGrappling!")
                    shooter.playSound(shooter.location, org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f)
                    
                    // クールダウン設定
                    legendAbilityCooldowns[shooter.uniqueId] = currentTime
                    
                    // フックを削除
                    projectile.remove()
                }
            }, 20L) // 1秒後
        }
    }
    
    @EventHandler
    fun onEntityDamageByEntity(event: org.bukkit.event.entity.EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager is Player && wraithInvisiblePlayers.contains(damager.uniqueId)) {
            // Wraithは透明化中攻撃できない
            event.isCancelled = true
            damager.sendMessage("§cYou cannot attack while in the void!")
            return
        }
        
        // ダメージ統計を記録
        if (damager is Player && event.entity is Player) {
            val victim = event.entity as Player
            if (currentGame != null && 
                currentGame!!.players.contains(damager.uniqueId) && 
                currentGame!!.players.contains(victim.uniqueId)) {
                
                val damage = event.finalDamage
                updatePlayerStats(damager.uniqueId, damage = damage)
            }
        }
    }
    
    
    @EventHandler
    fun onPlayerDropItem(event: org.bukkit.event.player.PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        
    }
    
    // 観戦モードの改善
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val clickedBlock = event.clickedBlock
        
        // スペクテーター機能
        if (player.gameMode == org.bukkit.GameMode.SPECTATOR) {
            if (event.action == org.bukkit.event.block.Action.LEFT_CLICK_AIR || 
                event.action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                
                // 最も近いプレイヤーにテレポート
                val nearestPlayer = currentGame?.players
                    ?.mapNotNull { Bukkit.getPlayer(it) }
                    ?.filter { it.gameMode != org.bukkit.GameMode.SPECTATOR }
                    ?.minByOrNull { it.location.distance(player.location) }
                
                if (nearestPlayer != null) {
                    player.spectatorTarget = nearestPlayer
                    player.sendMessage("§7Now spectating: §e${nearestPlayer.name}")
                }
            }
            return
        }
        
        if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
            return
        }
        
        if (!currentGame!!.players.contains(player.uniqueId)) {
            return
        }
        
        // ビーコンとの相互作用
        if (clickedBlock?.type == Material.BEACON) {
            event.isCancelled = true
            openBeaconMenu(player, clickedBlock.location)
        }
        
        // サプライボックスとの相互作用
        if (clickedBlock?.type == Material.YELLOW_SHULKER_BOX) {
            // サプライボックスを開いたら通知
            currentGame!!.players.forEach { playerId ->
                val p = Bukkit.getPlayer(playerId)
                if (p != player && p != null) {
                    val distance = p.location.distance(clickedBlock.location)
                    if (distance <= 100) {
                        p.sendMessage("§c⚠ A supply box is being looted nearby!")
                    }
                }
            }
        }
        
    }
    
    // チームカラーを適用
    private fun applyTeamColors() {
        object : BukkitRunnable() {
            override fun run() {
                if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
                    cancel()
                    return
                }
                
                // 各プレイヤーにチームカラーを適用
                currentGame!!.players.forEach { playerId ->
                    val player = Bukkit.getPlayer(playerId) ?: return@forEach
                    val teamId = playerTeams[playerId] ?: return@forEach
                    val team = teams[teamId] ?: return@forEach
                    
                    // ネームタグにチームカラーを追加
                    val colorCode = when (team.color) {
                        TeamColor.RED -> "§c"
                        TeamColor.BLUE -> "§9"
                        TeamColor.GREEN -> "§a"
                        TeamColor.YELLOW -> "§e"
                    }
                    
                    player.setDisplayName("$colorCode${player.name}§r")
                    player.setPlayerListName("$colorCode${player.name}§r")
                    
                    // チームメンバーに対して緑色のネームタグを表示
                    team.members.forEach { memberId ->
                        if (memberId != playerId) {
                            val member = Bukkit.getPlayer(memberId)
                            if (member != null && player.canSee(member)) {
                                // チームメイトは緑色で表示
                                player.scoreboard.getTeam(team.name) ?: player.scoreboard.registerNewTeam(team.name).apply {
                                    color = org.bukkit.ChatColor.GREEN
                                    setCanSeeFriendlyInvisibles(true)
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L)
    }
    
    // リング境界の可視化（リング外から見る）
    private fun showRingBoundary(player: Player) {
        val playerLoc = player.location
        val centerLoc = currentRingCenter ?: return
        
        // プレイヤーの位置からリング中心への方向を計算
        val directionToCenter = centerLoc.toVector().subtract(playerLoc.toVector()).normalize()
        
        // プレイヤーの前方に境界を表示（視界内）
        for (distance in 5..30 step 5) {
            val boundaryPoint = playerLoc.clone().add(directionToCenter.clone().multiply(distance))
            val distanceFromCenter = boundaryPoint.distance(centerLoc)
            
            // リング境界付近にパーティクルを表示
            if (Math.abs(distanceFromCenter - currentRingRadius) < 10) {
                // 垂直方向に壁を作る
                for (y in -5..10) {
                    val particleLoc = boundaryPoint.clone().add(0.0, y.toDouble(), 0.0)
                    
                    // バリアパーティクル（赤紫）
                    player.spawnParticle(
                        org.bukkit.Particle.DUST,
                        particleLoc,
                        1,
                        0.0, 0.0, 0.0,
                        0.0,
                        org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(138, 43, 226), 2.0f)
                    )
                    
                    // エンドロッドパーティクル（光る効果）
                    if (y % 3 == 0) {
                        player.spawnParticle(
                            org.bukkit.Particle.END_ROD,
                            particleLoc,
                            1,
                            0.0, 0.0, 0.0,
                            0.0
                        )
                    }
                }
            }
        }
        
        // 周囲360度にも境界を表示（近距離のみ）
        val distanceToRing = Math.abs(playerLoc.distance(centerLoc) - currentRingRadius)
        if (distanceToRing < 50) { // 50ブロック以内に近づいたら
            // 8方向にパーティクルを表示
            for (angle in 0 until 360 step 45) {
                val radian = Math.toRadians(angle.toDouble())
                val x = Math.cos(radian) * currentRingRadius
                val z = Math.sin(radian) * currentRingRadius
                
                val boundaryLoc = centerLoc.clone().add(x, 0.0, z)
                boundaryLoc.y = playerLoc.y
                
                // プレイヤーからの距離を確認
                if (boundaryLoc.distance(playerLoc) < 40) {
                    // 垂直の光の柱
                    for (y in 0..20 step 2) {
                        val particleLoc = boundaryLoc.clone().add(0.0, y.toDouble(), 0.0)
                        player.spawnParticle(
                            org.bukkit.Particle.DUST,
                            particleLoc,
                            1,
                            0.0, 0.0, 0.0,
                            0.0,
                            org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(138, 43, 226), 3.0f)
                        )
                    }
                }
            }
        }
    }
    
    // ストームの視界効果を適用
    private fun applyStormVisionEffect(player: Player, damage: Double) {
        // ダメージに応じた視界効果の強度
        val intensity = when {
            damage >= 2.0 -> 2  // 強い赤色
            damage >= 1.0 -> 1  // 中程度の赤色
            else -> 0           // 軽い赤色
        }
        
        // Nausea（吐き気）エフェクトで画面を歪ませる
        if (damage >= 1.0) {
            player.addPotionEffect(org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.NAUSEA,
                60, // 3秒間
                0,
                true,
                false,
                false
            ))
        }
        
        // 高ダメージ時には視界を制限
        if (damage >= 3.0) {
            player.addPotionEffect(org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.DARKNESS,
                40,
                0,
                true,
                false,
                false
            ))
        }
        
        // 赤いパーティクルをプレイヤーの周りに表示（血のエフェクト）
        for (i in 0..5) {
            val offset = Math.random() * 2 - 1
            player.world.spawnParticle(
                org.bukkit.Particle.DUST,
                player.eyeLocation.add(offset, Math.random() * 0.5, Math.random() * 2 - 1),
                1,
                0.0, 0.0, 0.0,
                0.0,
                org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.5f)
            )
        }
        
        // ダメージパーティクル
        player.world.spawnParticle(
            org.bukkit.Particle.DAMAGE_INDICATOR,
            player.location.add(0.0, 1.0, 0.0),
            3,
            0.5, 0.5, 0.5,
            0.1
        )
    }
    
    // ストームの視界効果を解除
    private fun removeStormVisionEffect(player: Player) {
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA)
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS)
    }
    
    
    
    private fun updateStormMap(player: Player) {
        if (currentGame == null || currentGame!!.state != GameState.ACTIVE) {
            player.sendMessage("§cNo active game to track!")
            return
        }
        
        if (currentRingCenter == null) {
            player.sendMessage("§cStorm not initialized!")
            return
        }
        
        
        // プレイヤーにリング情報を表示
        player.sendMessage("§9§l=== STORM TRACKER ===")
        player.sendMessage("§eStorm Center: §f${currentRingCenter!!.blockX}, ${currentRingCenter!!.blockZ}")
        player.sendMessage("§eSafe Zone Radius: §f${currentRingRadius.toInt()} blocks")
        player.sendMessage("§ePhase: §f$currentRingPhase / ${ringPhases.size}")
        
        val distance = player.location.distance(currentRingCenter!!)
        val status = if (distance <= currentRingRadius) "§a✓ SAFE" else "§c✖ IN STORM"
        player.sendMessage("§eYour Status: $status")
        player.sendMessage("§eDistance from center: §f${distance.toInt()} blocks")
        
        if (currentRingPhase > 0 && currentRingPhase <= ringPhases.size) {
            val phase = ringPhases[currentRingPhase - 1]
            if (isRingShrinking) {
                player.sendMessage("§c§lSTORM IS SHRINKING!")
            } else {
                player.sendMessage("§eNext shrink: §fSoon™")
            }
        }
        
        // コンパスのような方向指示
        showStormDirection(player)
        
        // マップをアニメーションで更新
        player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
        
        // パーティクル効果
        player.world.spawnParticle(
            org.bukkit.Particle.ENCHANT,
            player.location.add(0.0, 1.0, 0.0),
            20,
            0.5, 0.5, 0.5,
            0.1
        )
    }
    
    private fun showStormDirection(player: Player) {
        val playerLoc = player.location
        val centerLoc = currentRingCenter ?: return
        
        // プレイヤーから中心への方向を計算
        val direction = centerLoc.toVector().subtract(playerLoc.toVector())
        val angle = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90
        val playerYaw = playerLoc.yaw.toDouble()
        val relativeAngle = ((angle - playerYaw + 360) % 360)
        
        val arrow = when {
            relativeAngle < 22.5 || relativeAngle >= 337.5 -> "↑" // 上
            relativeAngle < 67.5 -> "↗" // 右上
            relativeAngle < 112.5 -> "→" // 右
            relativeAngle < 157.5 -> "↘" // 右下
            relativeAngle < 202.5 -> "↓" // 下
            relativeAngle < 247.5 -> "↙" // 左下
            relativeAngle < 292.5 -> "←" // 左
            else -> "↖" // 左上
        }
        
        player.sendMessage("§eStorm center direction: §f$arrow")
    }
    
    // スコアボードシステム
    private fun initializeScoreboard() {
        val manager = Bukkit.getScoreboardManager()
        gameScoreboard = manager.newScoreboard
        
        val objective = gameScoreboard!!.registerNewObjective("BR_Game", "dummy", "§6§lBATTLE ROYALE")
        objective.displaySlot = org.bukkit.scoreboard.DisplaySlot.SIDEBAR
        
        // スコアボード更新タスク
        scoreboardTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, {
            updateScoreboard()
        }, 0L, 20L) // 毎秒更新
        
        // 全プレイヤーにスコアボードを設定
        currentGame?.players?.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            player?.scoreboard = gameScoreboard!!
        }
    }
    
    private fun updateScoreboard() {
        if (gameScoreboard == null || currentGame == null) return
        
        val objective = gameScoreboard!!.getObjective("BR_Game") ?: return
        
        // 既存のエントリをクリア
        gameScoreboard!!.entries.forEach { entry ->
            gameScoreboard!!.resetScores(entry)
        }
        
        var line = 15
        
        // タイトル
        objective.getScore("§7§m                    ").score = line--
        
        // 生存者数
        val alivePlayers = currentGame!!.players.size
        val totalPlayers = currentGame!!.players.size + getDeadPlayersCount()
        objective.getScore("§fAlive: §a${alivePlayers}§7/${totalPlayers}").score = line--
        
        // 現在のフェーズ
        objective.getScore("§fPhase: §e${currentRingPhase + 1}/7").score = line--
        
        // リング状態
        if (isRingShrinking) {
            objective.getScore("§c§lSTORM CLOSING!").score = line--
        } else {
            objective.getScore("§fStorm: §aStable").score = line--
        }
        
        objective.getScore("§8                    ").score = line--
        
        // プレイヤー個人情報
        Bukkit.getOnlinePlayers().forEach { player ->
            if (currentGame!!.players.contains(player.uniqueId)) {
                val stats = playerStats[player.uniqueId] ?: PlayerStats()
                
                // キル数
                objective.getScore("§fYour Kills: §c${stats.kills}").score = line--
                
                // 所属チーム
                val teamId = playerTeams[player.uniqueId]
                val team = teamId?.let { teams[it] }
                if (team != null) {
                    val teamName = team.name.split(" ")[0] // "Red Team" -> "Red"
                    objective.getScore("§fTeam: ${team.name}").score = line--
                }
                
                // レジェンド
                val legend = playerLegends[player.uniqueId]
                if (legend != null) {
                    objective.getScore("§fLegend: ${legend.displayName}").score = line--
                }
            }
        }
        
        objective.getScore("§7§m                    ").score = line--
    }
    
    private fun cleanupScoreboard() {
        // スコアボードタスクをキャンセル
        scoreboardTaskId?.let {
            Bukkit.getScheduler().cancelTask(it)
            scoreboardTaskId = null
        }
        
        // プレイヤーのスコアボードをリセット
        Bukkit.getOnlinePlayers().forEach { player ->
            player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        }
        
        gameScoreboard = null
    }
    
    private fun getDeadPlayersCount(): Int {
        // 死亡したプレイヤー数を計算（統計から）
        return playerStats.values.count { it.deaths > 0 && it.gameStartTime > 0 }
    }
    
    // キルフィードシステム
    private fun addKillFeedEntry(
        killerName: String,
        victimName: String,
        killerLegend: Legend?,
        victimLegend: Legend?,
        weapon: String
    ) {
        val entry = KillFeedEntry(
            timestamp = System.currentTimeMillis(),
            killerName = killerName,
            victimName = victimName,
            killerLegend = killerLegend,
            victimLegend = victimLegend,
            weapon = weapon
        )
        
        killFeed.add(0, entry) // 最新のキルを先頭に追加
        
        // 最大エントリ数を超えたら古いものを削除
        while (killFeed.size > maxKillFeedEntries) {
            killFeed.removeAt(killFeed.size - 1)
        }
        
        // キルフィードを表示
        broadcastKillFeed(entry)
    }
    
    private fun addEnvironmentDeathEntry(
        victimName: String,
        victimLegend: Legend?,
        deathCause: String
    ) {
        val entry = KillFeedEntry(
            timestamp = System.currentTimeMillis(),
            victimName = victimName,
            victimLegend = victimLegend,
            deathCause = deathCause
        )
        
        killFeed.add(0, entry)
        
        while (killFeed.size > maxKillFeedEntries) {
            killFeed.removeAt(killFeed.size - 1)
        }
        
        broadcastKillFeed(entry)
    }
    
    private fun broadcastKillFeed(entry: KillFeedEntry) {
        val message = if (entry.killerName != null) {
            // プレイヤーキル
            val killerLegendTag = entry.killerLegend?.let { "[${it.displayName}§r] " } ?: ""
            val victimLegendTag = entry.victimLegend?.let { "[${it.displayName}§r] " } ?: ""
            val weaponIcon = getWeaponIcon(entry.weapon ?: "")
            
            "§7${killerLegendTag}§e${entry.killerName} §7${weaponIcon} §c${victimLegendTag}§c${entry.victimName}"
        } else {
            // 環境死
            val victimLegendTag = entry.victimLegend?.let { "[${it.displayName}§r] " } ?: ""
            "§c${victimLegendTag}§c${entry.victimName} §7${entry.deathCause}"
        }
        
        // 全プレイヤーにキルフィードを送信
        currentGame?.players?.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            player?.sendMessage(message)
        }
        
        // 観戦者にも送信
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.gameMode == org.bukkit.GameMode.SPECTATOR) {
                player.sendMessage(message)
            }
        }
    }
    
    private fun getWeaponName(item: ItemStack?): String {
        return when (item?.type) {
            Material.NETHERITE_SWORD -> "Netherite Sword"
            Material.DIAMOND_SWORD -> "Diamond Sword"
            Material.IRON_SWORD -> "Iron Sword"
            Material.STONE_SWORD -> "Stone Sword"
            Material.WOODEN_SWORD -> "Wooden Sword"
            Material.GOLDEN_SWORD -> "Golden Sword"
            
            Material.NETHERITE_AXE -> "Netherite Axe"
            Material.DIAMOND_AXE -> "Diamond Axe"
            Material.IRON_AXE -> "Iron Axe"
            Material.STONE_AXE -> "Stone Axe"
            Material.WOODEN_AXE -> "Wooden Axe"
            Material.GOLDEN_AXE -> "Golden Axe"
            
            Material.BOW -> "Bow"
            Material.CROSSBOW -> "Crossbow"
            Material.TRIDENT -> "Trident"
            
            Material.TNT -> "TNT"
            Material.END_CRYSTAL -> "End Crystal"
            
            null -> "Fists"
            else -> item.type.name.lowercase().replace('_', ' ').capitalize()
        }
    }
    
    private fun getWeaponIcon(weapon: String): String {
        return when {
            weapon.contains("Sword", true) -> "⚔"
            weapon.contains("Axe", true) -> "🪓"
            weapon.contains("Bow", true) -> "🏹"
            weapon.contains("Crossbow", true) -> "🏹"
            weapon.contains("Trident", true) -> "🔱"
            weapon.contains("TNT", true) -> "💥"
            weapon.contains("Crystal", true) -> "💎"
            weapon.contains("Fists", true) -> "👊"
            else -> "☠"
        }
    }
    
    // リスポーンビーコンシステム
    private fun openBeaconMenu(player: Player, beaconLocation: org.bukkit.Location) {
        val teamId = playerTeams[player.uniqueId] ?: return
        val team = teams[teamId] ?: return
        
        // ビーコンが使用可能かチェック
        if (!isBeaconActive(beaconLocation)) {
            player.sendMessage("§cThis respawn beacon has been destroyed!")
            return
        }
        
        val inventory = Bukkit.createInventory(null, 27, "§6Respawn Beacon")
        
        // チームメンバーの死亡プレイヤーを表示
        var slot = 0
        team.members.forEach { memberId ->
            val member = Bukkit.getPlayer(memberId)
            if (member != null && member.gameMode == org.bukkit.GameMode.SPECTATOR) {
                val skull = ItemStack(Material.PLAYER_HEAD).apply {
                    val meta = itemMeta as? org.bukkit.inventory.meta.SkullMeta
                    meta?.owningPlayer = member
                    meta?.setDisplayName("§e${member.name}")
                    meta?.lore = listOf(
                        "§7Click to respawn this player",
                        "§7They will spawn at this beacon"
                    )
                    itemMeta = meta
                }
                inventory.setItem(slot++, skull)
            }
        }
        
        if (slot == 0) {
            // リスポーン可能なプレイヤーがいない
            val info = ItemStack(Material.BARRIER).apply {
                val meta = itemMeta!!
                meta.setDisplayName("§cNo teammates to respawn")
                meta.lore = listOf(
                    "§7All team members are alive",
                    "§7or have left the game"
                )
                itemMeta = meta
            }
            inventory.setItem(13, info)
        }
        
        player.openInventory(inventory)
    }
    
    private fun isBeaconActive(location: org.bukkit.Location): Boolean {
        return location.block.type == Material.BEACON
    }
    
    @EventHandler
    fun onBeaconInteract(event: InventoryClickEvent) {
        val inventory = event.inventory
        val player = event.whoClicked as? Player ?: return
        
        if (event.view.title != "§6Respawn Beacon") return
        
        event.isCancelled = true
        
        val clicked = event.currentItem ?: return
        if (clicked.type != Material.PLAYER_HEAD) return
        
        val meta = clicked.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return
        val targetPlayer = meta.owningPlayer?.player ?: return
        
        if (targetPlayer.gameMode != org.bukkit.GameMode.SPECTATOR) {
            player.sendMessage("§cThis player is already alive!")
            player.closeInventory()
            return
        }
        
        // リスポーン実行
        respawnPlayer(targetPlayer, player)
        player.closeInventory()
        
        // ビーコンを破壊（1回使い切り）
        val beaconLoc = findNearestBeacon(player.location)
        beaconLoc?.block?.type = Material.AIR
        
        // エフェクト
        beaconLoc?.world?.spawnParticle(
            org.bukkit.Particle.CLOUD,
            beaconLoc.add(0.5, 0.5, 0.5),
            10,
            0.5, 0.5, 0.5,
            0.0
        )
        
        // 全体通知
        currentGame?.players?.forEach { playerId ->
            val p = Bukkit.getPlayer(playerId)
            p?.sendMessage("§a${targetPlayer.name} has been respawned by ${player.name}!")
        }
    }
    
    private fun respawnPlayer(deadPlayer: Player, reviver: Player) {
        // プレイヤーをゲームに復帰
        currentGame?.players?.add(deadPlayer.uniqueId)
        
        // ゲームモードを変更
        deadPlayer.gameMode = org.bukkit.GameMode.SURVIVAL
        
        // リバイバーの位置にテレポート
        deadPlayer.teleport(reviver.location)
        
        // 基本装備を付与
        giveRespawnKit(deadPlayer)
        
        // エフェクト
        deadPlayer.addPotionEffect(org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.RESISTANCE,
            100, // 5秒間
            1,
            false,
            false
        ))
        
        deadPlayer.sendMessage("§aYou have been respawned by ${reviver.name}!")
        deadPlayer.playSound(deadPlayer.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        
        // パーティクル
        deadPlayer.world.spawnParticle(
            org.bukkit.Particle.TOTEM_OF_UNDYING,
            deadPlayer.location.add(0.0, 1.0, 0.0),
            50,
            0.5, 1.0, 0.5,
            0.1
        )
    }
    
    private fun giveRespawnKit(player: Player) {
        player.inventory.clear()
        
        // 基本的な装備
        player.inventory.addItem(ItemStack(Material.IRON_SWORD))
        player.inventory.addItem(ItemStack(Material.SHIELD))
        player.inventory.addItem(ItemStack(Material.COOKED_BEEF, 16))
        
        // 軽い防具
        player.inventory.helmet = ItemStack(Material.LEATHER_HELMET)
        player.inventory.chestplate = ItemStack(Material.LEATHER_CHESTPLATE)
        player.inventory.leggings = ItemStack(Material.LEATHER_LEGGINGS)
        player.inventory.boots = ItemStack(Material.LEATHER_BOOTS)
        
        
        player.sendMessage("§7You have been given basic equipment.")
    }
    
    private fun findNearestBeacon(location: org.bukkit.Location): org.bukkit.Location? {
        val radius = 10.0
        for (x in -radius.toInt()..radius.toInt()) {
            for (y in -radius.toInt()..radius.toInt()) {
                for (z in -radius.toInt()..radius.toInt()) {
                    val checkLoc = location.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    if (checkLoc.block.type == Material.BEACON) {
                        return checkLoc
                    }
                }
            }
        }
        return null
    }
    
    // レジェンドアビリティのイベントハンドラー
    @EventHandler
    fun onLegendAbilityUse(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        
        // 右クリックのみ反応
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && 
            event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return
        }
        
        // レジェンドアイテムか確認
        val meta = item.itemMeta ?: return
        val legendName = meta.persistentDataContainer.get(
            org.bukkit.NamespacedKey(this, "legend_item"),
            org.bukkit.persistence.PersistentDataType.STRING
        ) ?: return
        
        val legend = try {
            Legend.valueOf(legendName)
        } catch (e: IllegalArgumentException) {
            return
        }
        
        // クールダウンチェック
        val cooldownKey = player.uniqueId.toString() + "_" + legend.name
        val lastUsed = legendAbilityCooldowns[player.uniqueId] ?: 0L
        val currentTime = System.currentTimeMillis()
        val cooldownTime = getCooldownTime(legend)
        
        if (currentTime - lastUsed < cooldownTime) {
            val remainingSeconds = ((cooldownTime - (currentTime - lastUsed)) / 1000)
            player.sendActionBar("§cアビリティはクールダウン中です！ あと${remainingSeconds}秒")
            return
        }
        
        // アビリティを実行
        when (legend) {
            Legend.PATHFINDER -> {
                // パスファインダーは釣竿の通常動作を使用するので、イベントをキャンセルしない
                return
            }
            Legend.WRAITH -> useWraithAbility(player)
            Legend.LIFELINE -> {
                useLifelineAbility(player)
                // 金のリンゴが消費されないように補充
                restoreLegendItem(player, legend)
            }
            Legend.BANGALORE -> useBangaloreAbility(player)
            Legend.GIBRALTAR -> {
                useGibraltarAbility(player, event)
                // ファイヤーチャージが消費されないように補充
                restoreLegendItem(player, legend)
            }
        }
        
        // クールダウンを設定
        legendAbilityCooldowns[player.uniqueId] = currentTime
        
        event.isCancelled = true
    }
    
    private fun getCooldownTime(legend: Legend): Long {
        return when (legend) {
            Legend.PATHFINDER -> 1000 // 1秒（テスト用）
            Legend.WRAITH -> 1000 // 1秒（テスト用）
            Legend.LIFELINE -> 1000 // 1秒（テスト用）
            Legend.BANGALORE -> 1000 // 1秒（テスト用）
            Legend.GIBRALTAR -> 1000 // 1秒（テスト用）
        }
    }
    
    // レジェンドアイテムを補充（消費されないようにする）
    private fun restoreLegendItem(player: Player, legend: Legend) {
        object : BukkitRunnable() {
            override fun run() {
                // アイテムが無くなっていたら再配布
                var hasItem = false
                for (item in player.inventory.contents) {
                    if (item == null) continue
                    val meta = item.itemMeta ?: continue
                    val legendName = meta.persistentDataContainer.get(
                        org.bukkit.NamespacedKey(this@Minecraft_legends_minimal, "legend_item"),
                        org.bukkit.persistence.PersistentDataType.STRING
                    )
                    if (legendName == legend.name) {
                        hasItem = true
                        break
                    }
                }
                
                if (!hasItem) {
                    giveLegendItem(player, legend)
                }
            }
        }.runTaskLater(this, 1L) // 次のtickで実行
    }
    
    // パスファインダー：グラップリングフック
    private fun usePathfinderAbility(player: Player) {
        // 釣竿の通常動作を許可（フックを投げる）
        player.sendMessage("§bグラップリングフック準備完了！")
    }
    
    // レイス：透明化
    private fun useWraithAbility(player: Player) {
        // 透明化
        player.addPotionEffect(
            org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY,
                60, // 3秒
                0,
                false,
                false
            )
        )
        
        // 移動速度上昇
        player.addPotionEffect(
            org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED,
                60, // 3秒
                1,
                false,
                false
            )
        )
        
        // パーティクル効果
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks >= 60) {
                    cancel()
                    return
                }
                
                player.world.spawnParticle(
                    org.bukkit.Particle.DRAGON_BREATH,
                    player.location.add(0.0, 1.0, 0.0),
                    5,
                    0.5, 0.5, 0.5,
                    0.01
                )
                
                ticks += 5
            }
        }.runTaskTimer(this, 0L, 5L)
        
        player.sendMessage("§5ヴォイドウォーク発動！")
        player.world.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f)
    }
    
    // ライフライン：回復ドローン
    private fun useLifelineAbility(player: Player) {
        val droneLocation = player.location.add(0.0, 2.0, 0.0)
        
        // ドローンの表示（エンドクリスタル）
        val drone = player.world.spawnEntity(droneLocation, org.bukkit.entity.EntityType.END_CRYSTAL) as org.bukkit.entity.EnderCrystal
        drone.isShowingBottom = false
        drone.isInvulnerable = true
        
        // 回復効果
        object : BukkitRunnable() {
            var duration = 0
            override fun run() {
                if (duration >= 100 || drone.isDead) { // 5秒間
                    drone.remove()
                    cancel()
                    return
                }
                
                // 範囲内のプレイヤーを回復
                player.getNearbyEntities(5.0, 5.0, 5.0)
                    .filterIsInstance<Player>()
                    .plus(player)
                    .filter { it.gameMode == org.bukkit.GameMode.SURVIVAL }
                    .forEach { p ->
                        if (p.health < p.healthScale * 2) {
                            p.health = kotlin.math.min(p.health + 1.0, p.healthScale * 2)
                        }
                    }
                
                // 回復パーティクル
                player.world.spawnParticle(
                    org.bukkit.Particle.HEART,
                    droneLocation,
                    3,
                    2.0, 2.0, 2.0,
                    0.0
                )
                
                duration += 10
            }
        }.runTaskTimer(this, 0L, 10L)
        
        player.sendMessage("§aD.O.C.ヒールドローン展開！")
        player.world.playSound(player.location, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f)
    }
    
    // バンガロール：ドームシールド
    private fun useBangaloreAbility(player: Player) {
        val center = player.location
        val radius = 5
        
        // ドームを作成
        for (x in -radius..radius) {
            for (y in -1..radius) {
                for (z in -radius..radius) {
                    val distance = kotlin.math.sqrt((x*x + y*y + z*z).toDouble())
                    if (distance >= radius - 0.5 && distance <= radius + 0.5) {
                        val blockLocation = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                        if (blockLocation.block.type == Material.AIR) {
                            // バリアブロックを配置
                            blockLocation.block.type = Material.WHITE_STAINED_GLASS
                            
                            // 20秒後に消去
                            object : BukkitRunnable() {
                                override fun run() {
                                    if (blockLocation.block.type == Material.WHITE_STAINED_GLASS) {
                                        blockLocation.block.type = Material.AIR
                                    }
                                }
                            }.runTaskLater(this, 400L) // 20秒
                        }
                    }
                }
            }
        }
        
        player.sendMessage("§7ドームシールド展開！")
        player.world.playSound(player.location, org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f)
    }
    
    // ジブラルタル：空爆
    private fun useGibraltarAbility(player: Player, event: PlayerInteractEvent) {
        // クリックした場所を取得
        val targetLocation = if (event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            event.clickedBlock?.location?.add(0.5, 1.0, 0.5)
        } else {
            player.getTargetBlock(null, 50)?.location?.add(0.5, 1.0, 0.5)
        }
        
        if (targetLocation == null) {
            player.sendMessage("§cターゲットが見つかりません！")
            return
        }
        
        // マーカーを表示
        player.sendMessage("§c空爆を要請！ 着弾まで3秒...")
        
        // 3秒後に爆撃開始
        object : BukkitRunnable() {
            override fun run() {
                // 爆撃を数回に分けて実行
                var bombCount = 0
                object : BukkitRunnable() {
                    override fun run() {
                        if (bombCount >= 6) {
                            cancel()
                            return
                        }
                        
                        // ランダムな位置に爆発
                        val randomX = targetLocation.x + (Math.random() - 0.5) * 10
                        val randomZ = targetLocation.z + (Math.random() - 0.5) * 10
                        val bombLocation = org.bukkit.Location(targetLocation.world, randomX, targetLocation.y, randomZ)
                        
                        // 爆発効果
                        bombLocation.world?.createExplosion(bombLocation, 3.0f, false, false)
                        
                        // パーティクル効果（爆発は既にcreateExplosionで表示される）
                        bombLocation.world?.spawnParticle(
                            org.bukkit.Particle.CLOUD,
                            bombLocation,
                            20,
                            1.0, 1.0, 1.0,
                            0.0
                        )
                        
                        bombCount++
                    }
                }.runTaskTimer(this@Minecraft_legends_minimal, 0L, 10L)
            }
        }.runTaskLater(this, 60L) // 3秒後
        
        player.world.playSound(player.location, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.5f)
    }
    
    // レジェンドアイテムのドロップを防ぐ
    @EventHandler
    fun onItemDrop(event: org.bukkit.event.player.PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        val meta = item.itemMeta ?: return
        
        if (meta.persistentDataContainer.has(
            org.bukkit.NamespacedKey(this, "legend_item"),
            org.bukkit.persistence.PersistentDataType.STRING
        )) {
            event.isCancelled = true
            event.player.sendMessage("§cレジェンドアイテムはドロップできません！")
        }
    }
    
    // レジェンドアイテムが死亡時にドロップされないように
    @EventHandler
    fun onPlayerDeathLegend(event: PlayerDeathEvent) {
        val drops = event.drops.iterator()
        while (drops.hasNext()) {
            val item = drops.next()
            val meta = item.itemMeta ?: continue
            
            if (meta.persistentDataContainer.has(
                org.bukkit.NamespacedKey(this, "legend_item"),
                org.bukkit.persistence.PersistentDataType.STRING
            )) {
                drops.remove()
            }
        }
    }
    
    
    // レジェンドアイテム（金のリンゴ）が消費されないようにする
    @EventHandler
    fun onItemConsume(event: org.bukkit.event.player.PlayerItemConsumeEvent) {
        val item = event.item
        val meta = item.itemMeta ?: return
        
        // レジェンドアイテムか確認
        if (meta.persistentDataContainer.has(
            org.bukkit.NamespacedKey(this, "legend_item"),
            org.bukkit.persistence.PersistentDataType.STRING
        )) {
            // 消費をキャンセル
            event.isCancelled = true
            
            // 代わりにアビリティを使用
            val player = event.player
            val legendName = meta.persistentDataContainer.get(
                org.bukkit.NamespacedKey(this, "legend_item"),
                org.bukkit.persistence.PersistentDataType.STRING
            )!!
            
            val legend = Legend.valueOf(legendName)
            
            // クールダウンチェック
            val lastUsed = legendAbilityCooldowns[player.uniqueId] ?: 0L
            val currentTime = System.currentTimeMillis()
            val cooldownTime = getCooldownTime(legend)
            
            if (currentTime - lastUsed < cooldownTime) {
                val remainingSeconds = ((cooldownTime - (currentTime - lastUsed)) / 1000)
                player.sendActionBar("§cアビリティはクールダウン中です！ あと${remainingSeconds}秒")
                return
            }
            
            // ライフラインの場合のみアビリティを実行
            if (legend == Legend.LIFELINE) {
                useLifelineAbility(player)
                legendAbilityCooldowns[player.uniqueId] = currentTime
            }
        }
    }
    
    // パスファインダーのフックメカニクス
    private val activeHooks = mutableMapOf<UUID, org.bukkit.entity.FishHook>()
    private val hookCooldowns = mutableMapOf<UUID, Long>()
    
    // Grappling Hook設定（snowgearsスタイル）
    private val velocityMultiplier = 2.5 // 引き寄せ速度係数
    private val maxHookDistance = 30.0 // 最大フック距離
    private val hookCooldown = 1000L // クールダウン（1秒）
    
    @EventHandler
    fun onPlayerFish(event: org.bukkit.event.player.PlayerFishEvent) {
        val player = event.player
        val hook = event.hook
        
        // パスファインダーのレジェンドアイテムか確認
        val item = player.inventory.itemInMainHand
        val meta = item.itemMeta ?: return
        
        val legendName = meta.persistentDataContainer.get(
            org.bukkit.NamespacedKey(this, "legend_item"),
            org.bukkit.persistence.PersistentDataType.STRING
        ) ?: return
        
        if (legendName != Legend.PATHFINDER.name) return
        
        // デバッグ: イベント状態をログ
        player.sendMessage("§7[DEBUG] Fish event state: ${event.state}")
        
        when (event.state) {
            org.bukkit.event.player.PlayerFishEvent.State.FISHING -> {
                // フックを投げた時
                activeHooks[player.uniqueId] = hook
                player.sendMessage("§bグラップリングフック発射！")
                
                // フックの見た目を強化
                object : BukkitRunnable() {
                    override fun run() {
                        if (!hook.isValid || hook.isDead) {
                            cancel()
                            return
                        }
                        
                        // フックの軌跡にパーティクル
                        hook.world.spawnParticle(
                            org.bukkit.Particle.DUST,
                            hook.location,
                            1,
                            org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0, 255, 255), 1.0f)
                        )
                    }
                }.runTaskTimer(this, 0L, 2L)
            }
            
            org.bukkit.event.player.PlayerFishEvent.State.IN_GROUND -> {
                // フックが地面に刺さった時（snowgearsスタイル）
                val hookLocation = hook.location
                val playerLocation = player.location
                val distance = playerLocation.distance(hookLocation)
                
                // クールダウンチェック
                val currentTime = System.currentTimeMillis()
                val lastUsed = hookCooldowns[player.uniqueId] ?: 0L
                if (currentTime - lastUsed < hookCooldown) {
                    val remaining = (hookCooldown - (currentTime - lastUsed)) / 1000.0
                    player.sendActionBar("§cグラップリングクールダウン: ${String.format("%.1f", remaining)}秒")
                    hook.remove()
                    return
                }
                
                if (distance > maxHookDistance) {
                    player.sendMessage("§c距離が遠すぎます！")
                    hook.remove()
                    return
                }
                
                // snowgearsスタイルの速度計算
                val dx = hookLocation.x - playerLocation.x
                val dy = hookLocation.y - playerLocation.y
                val dz = hookLocation.z - playerLocation.z
                
                // 水平距離と垂直距離を別々に計算
                val horizontalDistance = kotlin.math.sqrt(dx * dx + dz * dz)
                val time = distance / 10.0 // 時間ファクター
                
                // 速度ベクトルの計算（放物線運動を考慮）
                var vx = dx / time * velocityMultiplier
                var vy = (dy / time + 0.5 * 0.98 * time) * velocityMultiplier // 重力を考慮
                var vz = dz / time * velocityMultiplier
                
                // 垂直方向の調整
                if (dy > 0) {
                    vy += 0.4 // 上方向への追加ブースト
                } else if (dy < -5) {
                    vy *= 0.8 // 下方向への速度を制限
                }
                
                // 最大速度の制限
                val maxVelocity = 4.0
                val totalVelocity = kotlin.math.sqrt(vx * vx + vy * vy + vz * vz)
                if (totalVelocity > maxVelocity) {
                    val scale = maxVelocity / totalVelocity
                    vx *= scale
                    vy *= scale
                    vz *= scale
                }
                
                // 速度を適用
                player.velocity = org.bukkit.util.Vector(vx, vy, vz)
                
                // フックを即座に削除
                hook.remove()
                activeHooks.remove(player.uniqueId)
                hookCooldowns[player.uniqueId] = currentTime
                
                // エフェクト
                player.world.playSound(player.location, org.bukkit.Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f)
                player.sendMessage("§bグラップリング発動！")
                
                // 軌道パーティクルを表示
                object : BukkitRunnable() {
                    var count = 0
                    override fun run() {
                        if (count >= 20 || player.isOnGround) {
                            cancel()
                            return
                        }
                        
                        player.world.spawnParticle(
                            org.bukkit.Particle.END_ROD,
                            player.location,
                            5,
                            0.2, 0.2, 0.2,
                            0.05
                        )
                        count++
                    }
                }.runTaskTimer(this, 0L, 2L)
                
                // レジェンドアビリティのクールダウンも設定
                legendAbilityCooldowns[player.uniqueId] = currentTime
            }
            
            org.bukkit.event.player.PlayerFishEvent.State.REEL_IN -> {
                // リールを巻いた時（右クリックで切断）
                if (activeHooks.containsKey(player.uniqueId)) {
                    val activeHook = activeHooks[player.uniqueId]
                    if (activeHook != null && activeHook.isValid) {
                        activeHook.remove()
                    }
                    activeHooks.remove(player.uniqueId)
                    player.sendMessage("§7グラップリング切断")
                    player.world.playSound(player.location, org.bukkit.Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 2.0f)
                }
            }
            
            org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_ENTITY -> {
                // エンティティを釣った時
                val caught = event.caught
                if (caught != null && caught != player) {
                    // エンティティを引き寄せる
                    val playerLocation = player.location
                    val entityLocation = caught.location
                    val distance = playerLocation.distance(entityLocation)
                    
                    if (distance <= maxHookDistance) {
                        // エンティティを引き寄せる速度計算
                        val dx = playerLocation.x - entityLocation.x
                        val dy = playerLocation.y - entityLocation.y + 1.0
                        val dz = playerLocation.z - entityLocation.z
                        
                        val time = distance / 10.0
                        val vx = dx / time * 1.5
                        val vy = (dy / time + 0.5 * 0.98 * time) * 1.5
                        val vz = dz / time * 1.5
                        
                        caught.velocity = org.bukkit.util.Vector(vx, vy, vz)
                        
                        player.sendMessage("§aエンティティを引き寄せました！")
                        player.world.playSound(player.location, org.bukkit.Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.8f)
                    }
                }
            }
            
            org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH -> {
                // 魚を釣った時（通常は発生しないが念のため）
                event.isCancelled = true
            }
            
            else -> {
                // その他の状態
                activeHooks.remove(player.uniqueId)
            }
        }
    }
}

data class BattleRoyaleGame(
    val id: UUID,
    val worldName: String,
    var state: GameState,
    val players: MutableList<UUID>
)

data class Team(
    val id: UUID,
    val name: String,
    val color: TeamColor,
    val members: MutableList<UUID>,
    var isAlive: Boolean = true
)

enum class TeamColor {
    RED, BLUE, GREEN, YELLOW
}

data class PlayerStats(
    var gamesPlayed: Int = 0,
    var wins: Int = 0,
    var kills: Int = 0,
    var deaths: Int = 0,
    var damageDealt: Double = 0.0,
    var timeSurvived: Long = 0, // 秒
    var gameStartTime: Long = 0 // ゲーム開始時刻（ミリ秒）
)

enum class GameState {
    WAITING,
    ACTIVE,
    FINISHED
}

enum class Legend(val displayName: String, val description: String) {
    PATHFINDER("§b§lPathfinder", "Grappling hook mobility"),
    WRAITH("§5§lWraith", "Void walk invisibility"),  
    LIFELINE("§a§lLifeline", "Combat medic"),
    BANGALORE("§7§lBangalore", "Professional soldier with dome shield"),
    GIBRALTAR("§c§lGibraltar", "Shielded fortress with airstrike");
    
    companion object {
        fun fromOrdinal(ordinal: Int): Legend? = values().getOrNull(ordinal)
    }
}

enum class Achievement(val displayName: String, val description: String) {
    FIRST_WIN("First Victory", "Win your first Battle Royale match"),
    KILLER_10("Eliminator", "Get 10 eliminations total"),
    KILLER_50("Master Eliminator", "Get 50 eliminations total"),
    CHAMPION("Champion", "Win 10 Battle Royale matches"),
    SURVIVOR("Survivor", "Survive for 30 minutes in a single match")
}

enum class AchievementAction {
    KILL, WIN, SURVIVE
}

data class RingPhase(
    val phase: Int,
    val waitTime: Int,        // 縮小開始までの時間（秒）
    val shrinkTime: Int,      // 縮小時間（秒）  
    val damage: Double,       // リング外ダメージ/秒
    val startRadius: Double,  // 開始半径
    val endRadius: Double     // 終了半径
)

data class KillFeedEntry(
    val timestamp: Long,
    val killerName: String? = null,
    val victimName: String,
    val killerLegend: Legend? = null,
    val victimLegend: Legend? = null,
    val weapon: String? = null,
    val deathCause: String? = null
)