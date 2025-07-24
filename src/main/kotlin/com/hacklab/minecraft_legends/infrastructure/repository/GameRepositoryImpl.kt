package com.hacklab.minecraft_legends.infrastructure.repository

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.GameRepository
import com.hacklab.minecraft_legends.infrastructure.database.DatabaseManager
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GameRepositoryImpl(
    private val databaseManager: DatabaseManager
) : GameRepository {
    
    private val logger = LoggerFactory.getLogger()
    private val gameCache = ConcurrentHashMap<UUID, Game>()
    
    init {
        kotlinx.coroutines.runBlocking {
            initializeTables()
        }
    }
    
    private suspend fun initializeTables() {
        // Create games table
        databaseManager.executeUpdate("""
            CREATE TABLE IF NOT EXISTS games (
                id VARCHAR(36) PRIMARY KEY,
                world_name VARCHAR(100) NOT NULL,
                state VARCHAR(50) NOT NULL,
                winner_team_id VARCHAR(36),
                max_players INT NOT NULL,
                min_players INT NOT NULL,
                team_size INT NOT NULL,
                world_size INT NOT NULL,
                respawn_beacon_count INT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                started_at TIMESTAMP,
                ended_at TIMESTAMP
            )
        """)
        
        // Create indexes separately
        databaseManager.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_games_state ON games(state)
        """)
        
        databaseManager.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_games_created_at ON games(created_at)
        """)
    }
    
    override suspend fun findById(id: UUID): Game? = withContext(Dispatchers.IO) {
        gameCache[id] ?: run {
            val sql = "SELECT * FROM games WHERE id = ?"
            val result = databaseManager.executeQuerySingle(sql, id.toString()) { rs ->
                Game(
                    id = UUID.fromString(rs.getString("id")),
                    worldName = rs.getString("world_name"),
                    state = GameState.valueOf(rs.getString("state")),
                    teams = emptyList(),
                    settings = GameSettings(
                        maxPlayers = rs.getInt("max_players"),
                        minPlayers = rs.getInt("min_players"),
                        teamSize = rs.getInt("team_size"),
                        worldSize = rs.getInt("world_size"),
                        respawnBeaconCount = rs.getInt("respawn_beacon_count")
                    ),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                    startTime = rs.getTimestamp("started_at")?.toLocalDateTime(),
                    endTime = rs.getTimestamp("ended_at")?.toLocalDateTime(),
                    winnerTeamId = rs.getString("winner_team_id")?.let { UUID.fromString(it) }
                )
            }
            
            result?.let { game ->
                gameCache[game.id] = game
            }
            result
        }
    }
    
    override suspend fun save(game: Game): Game = withContext(Dispatchers.IO) {
        val existingGame = findById(game.id)
        
        if (existingGame == null) {
            val sql = """
                INSERT INTO games (id, world_name, state, winner_team_id, max_players, 
                    min_players, team_size, world_size, respawn_beacon_count, 
                    created_at, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
            
            databaseManager.executeUpdate(sql, listOf(
                game.id.toString(),
                game.worldName,
                game.state.name,
                game.winnerTeamId?.toString(),
                game.settings.maxPlayers,
                game.settings.minPlayers,
                game.settings.teamSize,
                game.settings.worldSize,
                game.settings.respawnBeaconCount,
                Timestamp.valueOf(game.createdAt),
                game.startTime?.let { Timestamp.valueOf(it) },
                game.endTime?.let { Timestamp.valueOf(it) }
            ))
        } else {
            val sql = """
                UPDATE games SET state = ?, winner_team_id = ?, 
                    started_at = ?, ended_at = ? WHERE id = ?
            """
            
            databaseManager.executeUpdate(sql, listOf(
                game.state.name,
                game.winnerTeamId?.toString(),
                game.startTime?.let { Timestamp.valueOf(it) },
                game.endTime?.let { Timestamp.valueOf(it) },
                game.id.toString()
            ))
        }
        
        gameCache[game.id] = game
        game
    }

    override suspend fun delete(id: UUID): Boolean = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM games WHERE id = ?"
        val result = databaseManager.executeUpdate(sql, listOf(id.toString()))
        gameCache.remove(id)
        result > 0
    }

    override suspend fun exists(id: UUID): Boolean = withContext(Dispatchers.IO) {
        val sql = "SELECT 1 FROM games WHERE id = ? LIMIT 1"
        val result = databaseManager.executeQuerySingle(sql, id.toString()) { rs ->
            true
        }
        result != null
    }
    
    override suspend fun findAll(): List<Game> = withContext(Dispatchers.IO) {
        val sql = "SELECT id FROM games ORDER BY created_at DESC"
        val gameIds = databaseManager.executeQuery(sql) { rs ->
            UUID.fromString(rs.getString("id"))
        }
        
        val games = mutableListOf<Game>()
        gameIds.forEach { gameId ->
            findById(gameId)?.let { games.add(it) }
        }
        games
    }
    
    override suspend fun findActiveGame(): Game? = withContext(Dispatchers.IO) {
        val sql = "SELECT id FROM games WHERE state IN (?, ?) ORDER BY created_at DESC LIMIT 1"
        val result = databaseManager.executeQuerySingle(sql, GameState.WAITING.name, GameState.ACTIVE.name) { rs ->
            UUID.fromString(rs.getString("id"))
        }
        result?.let { gameId -> findById(gameId) }
    }
    
    override suspend fun findByState(state: GameState): List<Game> = withContext(Dispatchers.IO) {
        val sql = "SELECT id FROM games WHERE state = ? ORDER BY created_at DESC"
        val gameIds = databaseManager.executeQuery(sql, state.name) { rs ->
            UUID.fromString(rs.getString("id"))
        }
        
        val games = mutableListOf<Game>()
        gameIds.forEach { gameId ->
            findById(gameId)?.let { games.add(it) }
        }
        games
    }
    
    override suspend fun findRecentGames(limit: Int): List<Game> = withContext(Dispatchers.IO) {
        val sql = "SELECT id FROM games ORDER BY created_at DESC LIMIT ?"
        val gameIds = databaseManager.executeQuery(sql, limit) { rs ->
            UUID.fromString(rs.getString("id"))
        }
        
        val games = mutableListOf<Game>()
        gameIds.forEach { gameId ->
            findById(gameId)?.let { games.add(it) }
        }
        games
    }
    
    override suspend fun findGamesByDateRange(start: LocalDateTime, end: LocalDateTime): List<Game> = emptyList()
    override suspend fun findGamesByPlayerId(playerId: UUID): List<Game> = emptyList()
    override suspend fun findGamesByTeamId(teamId: UUID): List<Game> = emptyList()
    override suspend fun getGameCount(): Long = 0L
    override suspend fun getAverageGameDuration(): Double = 0.0
    override suspend fun getWinRateByTeam(teamId: UUID): Double = 0.0
    override suspend fun getWinRateByPlayer(playerId: UUID): Double = 0.0
    override suspend fun deleteOldGames(olderThan: LocalDateTime): Int = 0
}