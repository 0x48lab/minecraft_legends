package com.hacklab.minecraft_legends.infrastructure.database

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.PlayerRepository
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PlayerRepositoryImpl(
    private val databaseManager: DatabaseManager
) : PlayerRepository {
    
    private val logger = LoggerFactory.getLogger()
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val jsonParser = JSONParser()
    
    override suspend fun findById(id: UUID): Player? = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuerySingle(
                "SELECT * FROM players WHERE id = ?",
                id.toString()
            ) { rs ->
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to find player by id: $id", e)
            null
        }
    }
    
    override suspend fun findByName(name: String): Player? = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuerySingle(
                "SELECT * FROM players WHERE name = ?",
                name
            ) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to find player by name: $name", e)
            null
        }
    }
    
    override suspend fun save(player: Player): Player = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeTransaction { conn ->
                // プレイヤー基本情報を保存
                conn.prepareStatement("""
                    INSERT OR REPLACE INTO players (id, name, created_at, last_seen)
                    VALUES (?, ?, ?, ?)
                """).use { stmt ->
                    stmt.setString(1, player.id.toString())
                    stmt.setString(2, player.name)
                    stmt.setString(3, player.createdAt.format(timeFormatter))
                    stmt.setString(4, player.lastSeen.format(timeFormatter))
                    stmt.executeUpdate()
                }
                
                // 統計情報を保存
                runBlocking { savePlayerStatistics(player.id, player.statistics) }
                
                // 設定情報を保存
                runBlocking { savePlayerSettings(player.id, player.settings) }
            }
            
            logger.debug("Saved player: ${player.name} (${player.id})")
            player
        } catch (e: Exception) {
            logger.error("Failed to save player: ${player.name}", e)
            throw e
        }
    }
    
    override suspend fun delete(id: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = databaseManager.executeUpdate(
                "DELETE FROM players WHERE id = ?",
                id.toString()
            )
            logger.debug("Deleted player: $id")
            result > 0
        } catch (e: Exception) {
            logger.error("Failed to delete player: $id", e)
            false
        }
    }
    
    override suspend fun exists(id: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuerySingle(
                "SELECT 1 FROM players WHERE id = ?",
                id.toString()
            ) { rs -> rs.getInt(1) } != null
        } catch (e: Exception) {
            logger.error("Failed to check player existence: $id", e)
            false
        }
    }
    
    override suspend fun findAll(): List<Player> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery(
                "SELECT * FROM players ORDER BY last_seen DESC"
            ) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to find all players", e)
            emptyList()
        }
    }
    
    override suspend fun findByIds(ids: Set<UUID>): List<Player> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        
        try {
            val placeholders = ids.joinToString(",") { "?" }
            val idStrings = ids.map { it.toString() }.toTypedArray()
            
            databaseManager.executeQuery(
                "SELECT * FROM players WHERE id IN ($placeholders)",
                *idStrings
            ) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to find players by ids", e)
            emptyList()
        }
    }
    
    override suspend fun updateStatistics(playerId: UUID, statistics: PlayerStatistics): Boolean = withContext(Dispatchers.IO) {
        try {
            runBlocking { savePlayerStatistics(playerId, statistics) }
            true
        } catch (e: Exception) {
            logger.error("Failed to update statistics for player: $playerId", e)
            false
        }
    }
    
    override suspend fun getLeaderboard(limit: Int): List<Player> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery("""
                SELECT p.*, ps.kills, ps.deaths, ps.wins, ps.damage
                FROM players p
                LEFT JOIN player_statistics ps ON p.id = ps.player_id
                ORDER BY ps.wins DESC, ps.kills DESC
                LIMIT ?
            """, limit) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get leaderboard", e)
            emptyList()
        }
    }
    
    override suspend fun getLeaderboardByKills(limit: Int): List<Player> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery("""
                SELECT p.*, ps.kills
                FROM players p
                LEFT JOIN player_statistics ps ON p.id = ps.player_id
                ORDER BY ps.kills DESC
                LIMIT ?
            """, limit) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get kills leaderboard", e)
            emptyList()
        }
    }
    
    override suspend fun getLeaderboardByWins(limit: Int): List<Player> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery("""
                SELECT p.*, ps.wins
                FROM players p
                LEFT JOIN player_statistics ps ON p.id = ps.player_id
                ORDER BY ps.wins DESC
                LIMIT ?
            """, limit) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get wins leaderboard", e)
            emptyList()
        }
    }
    
    override suspend fun getLeaderboardByDamage(limit: Int): List<Player> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery("""
                SELECT p.*, ps.damage
                FROM players p
                LEFT JOIN player_statistics ps ON p.id = ps.player_id
                ORDER BY ps.damage DESC
                LIMIT ?
            """, limit) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get damage leaderboard", e)
            emptyList()
        }
    }
    
    override suspend fun findByMinKills(minKills: Long): List<Player> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery("""
                SELECT p.*
                FROM players p
                LEFT JOIN player_statistics ps ON p.id = ps.player_id
                WHERE ps.kills >= ?
                ORDER BY ps.kills DESC
            """, minKills) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to find players by min kills", e)
            emptyList()
        }
    }
    
    override suspend fun findByMinWins(minWins: Long): List<Player> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery("""
                SELECT p.*
                FROM players p
                LEFT JOIN player_statistics ps ON p.id = ps.player_id
                WHERE ps.wins >= ?
                ORDER BY ps.wins DESC
            """, minWins) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to find players by min wins", e)
            emptyList()
        }
    }
    
    override suspend fun findRecentlyActive(days: Int): List<Player> = withContext(Dispatchers.IO) {
        try {
            val cutoffDate = LocalDateTime.now().minusDays(days.toLong()).format(timeFormatter)
            
            databaseManager.executeQuery("""
                SELECT * FROM players 
                WHERE last_seen >= ?
                ORDER BY last_seen DESC
            """, cutoffDate) { rs ->
                val id = UUID.fromString(rs.getString("id"))
                val statistics = runBlocking { getPlayerStatistics(id) ?: PlayerStatistics(id) }
                val settings = runBlocking { getPlayerSettings(id) ?: PlayerSettings() }
                
                Player(
                    id = id,
                    name = rs.getString("name"),
                    statistics = statistics,
                    settings = settings,
                    createdAt = LocalDateTime.parse(rs.getString("created_at"), timeFormatter),
                    lastSeen = LocalDateTime.parse(rs.getString("last_seen"), timeFormatter)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to find recently active players", e)
            emptyList()
        }
    }
    
    private suspend fun getPlayerStatistics(playerId: UUID): PlayerStatistics? = withContext(Dispatchers.IO) {
        try {
            val baseStats = databaseManager.executeQuerySingle(
                "SELECT * FROM player_statistics WHERE player_id = ?",
                playerId.toString()
            ) { rs ->
                val usedLegendsJson = rs.getString("used_legends") ?: "[]"
                val usedLegends = try {
                    val jsonArray = jsonParser.parse(usedLegendsJson) as JSONArray
                    jsonArray.map { it.toString() }.toSet()
                } catch (e: Exception) {
                    emptySet()
                }
                
                PlayerStatistics(
                    playerId = playerId,
                    kills = rs.getLong("kills"),
                    deaths = rs.getLong("deaths"),
                    wins = rs.getLong("wins"),
                    damage = rs.getLong("damage"),
                    maxKillsInGame = rs.getInt("max_kills_in_game"),
                    reviveCount = rs.getLong("revive_count"),
                    usedLegends = usedLegends
                )
            } ?: return@withContext null
            
            // レジェンド統計を取得
            val legendStats = getLegendStatistics(playerId)
            
            // カスタム統計を取得
            val customStats = getCustomStatistics(playerId)
            
            baseStats.copy(
                legendStatistics = legendStats,
                customStatistics = customStats
            )
            
        } catch (e: Exception) {
            logger.error("Failed to get player statistics: $playerId", e)
            null
        }
    }
    
    private suspend fun getLegendStatistics(playerId: UUID): Map<String, LegendStatistics> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery(
                "SELECT * FROM legend_statistics WHERE player_id = ?",
                playerId.toString()
            ) { rs ->
                rs.getString("legend_id") to LegendStatistics(
                    kills = rs.getLong("kills"),
                    deaths = rs.getLong("deaths"),
                    wins = rs.getLong("wins"),
                    damage = rs.getLong("damage"),
                    timePlayed = rs.getLong("time_played")
                )
            }.toMap()
        } catch (e: Exception) {
            logger.error("Failed to get legend statistics: $playerId", e)
            emptyMap()
        }
    }
    
    private suspend fun getCustomStatistics(playerId: UUID): Map<String, StatisticValue> = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuery(
                "SELECT * FROM custom_statistics WHERE player_id = ?",
                playerId.toString()
            ) { rs ->
                val key = rs.getString("stat_key")
                val value = rs.getString("stat_value")
                val type = rs.getString("stat_type")
                
                val statisticValue = when (type) {
                    "long" -> StatisticValue.LongValue(value.toLong())
                    "double" -> StatisticValue.DoubleValue(value.toDouble())
                    "boolean" -> StatisticValue.BooleanValue(value.toBoolean())
                    else -> StatisticValue.StringValue(value)
                }
                
                key to statisticValue
            }.toMap()
        } catch (e: Exception) {
            logger.error("Failed to get custom statistics: $playerId", e)
            emptyMap()
        }
    }
    
    private suspend fun getPlayerSettings(playerId: UUID): PlayerSettings? = withContext(Dispatchers.IO) {
        try {
            databaseManager.executeQuerySingle(
                "SELECT * FROM player_settings WHERE player_id = ?",
                playerId.toString()
            ) { rs ->
                val customSettingsJson = rs.getString("custom_settings") ?: "{}"
                val customSettings = try {
                    val jsonObject = jsonParser.parse(customSettingsJson) as JSONObject
                    jsonObject.mapKeys { it.key.toString() }.mapValues { it.value.toString() }
                } catch (e: Exception) {
                    emptyMap()
                }
                
                PlayerSettings(
                    language = rs.getString("language"),
                    currentTitle = rs.getString("current_title"),
                    showTitleInChat = rs.getInt("show_title_in_chat") != 0,
                    showTitleInTab = rs.getInt("show_title_in_tab") != 0,
                    autoJoinGame = rs.getInt("auto_join_game") != 0,
                    preferredLegend = rs.getString("preferred_legend"),
                    showDamageNumbers = rs.getInt("show_damage_numbers") != 0,
                    showKillMessages = rs.getInt("show_kill_messages") != 0,
                    enableSounds = rs.getInt("enable_sounds") != 0,
                    customSettings = customSettings
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to get player settings: $playerId", e)
            null
        }
    }
    
    private suspend fun savePlayerStatistics(playerId: UUID, statistics: PlayerStatistics) = withContext(Dispatchers.IO) {
        // 基本統計を保存
        val usedLegendsJson = JSONArray().apply {
            statistics.usedLegends.forEach { add(it) }
        }.toJSONString()
        
        databaseManager.executeUpdate("""
            INSERT OR REPLACE INTO player_statistics 
            (player_id, kills, deaths, wins, damage, max_kills_in_game, revive_count, used_legends)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, 
            playerId.toString(),
            statistics.kills,
            statistics.deaths,
            statistics.wins,
            statistics.damage,
            statistics.maxKillsInGame,
            statistics.reviveCount,
            usedLegendsJson
        )
        
        // レジェンド統計を保存
        statistics.legendStatistics.forEach { (legendId, legendStats) ->
            databaseManager.executeUpdate("""
                INSERT OR REPLACE INTO legend_statistics 
                (player_id, legend_id, kills, deaths, wins, damage, time_played)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                playerId.toString(),
                legendId,
                legendStats.kills,
                legendStats.deaths,
                legendStats.wins,
                legendStats.damage,
                legendStats.timePlayed
            )
        }
        
        // カスタム統計を保存
        statistics.customStatistics.forEach { (key, value) ->
            val (valueString, valueType) = when (value) {
                is StatisticValue.LongValue -> value.value.toString() to "long"
                is StatisticValue.DoubleValue -> value.value.toString() to "double"
                is StatisticValue.BooleanValue -> value.value.toString() to "boolean"
                is StatisticValue.StringValue -> value.value to "string"
            }
            
            databaseManager.executeUpdate("""
                INSERT OR REPLACE INTO custom_statistics 
                (player_id, stat_key, stat_value, stat_type)
                VALUES (?, ?, ?, ?)
            """,
                playerId.toString(),
                key,
                valueString,
                valueType
            )
        }
    }
    
    private suspend fun savePlayerSettings(playerId: UUID, settings: PlayerSettings) = withContext(Dispatchers.IO) {
        val customSettingsJson = JSONObject().apply {
            settings.customSettings.forEach { (key, value) ->
                put(key, value)
            }
        }.toJSONString()
        
        databaseManager.executeUpdate("""
            INSERT OR REPLACE INTO player_settings 
            (player_id, language, current_title, show_title_in_chat, show_title_in_tab, 
             auto_join_game, preferred_legend, show_damage_numbers, show_kill_messages, 
             enable_sounds, custom_settings)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
            playerId.toString(),
            settings.language,
            settings.currentTitle ?: "",
            if (settings.showTitleInChat) 1 else 0,
            if (settings.showTitleInTab) 1 else 0,
            if (settings.autoJoinGame) 1 else 0,
            settings.preferredLegend ?: "",
            if (settings.showDamageNumbers) 1 else 0,
            if (settings.showKillMessages) 1 else 0,
            if (settings.enableSounds) 1 else 0,
            customSettingsJson
        )
    }
}