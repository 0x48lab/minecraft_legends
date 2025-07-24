package com.hacklab.minecraft_legends.infrastructure.repository

import com.hacklab.minecraft_legends.domain.entity.Team
import com.hacklab.minecraft_legends.domain.repository.TeamRepository
import com.hacklab.minecraft_legends.infrastructure.database.DatabaseManager
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TeamRepositoryImpl(
    private val databaseManager: DatabaseManager
) : TeamRepository {
    
    private val logger = LoggerFactory.getLogger()
    private val teamCache = ConcurrentHashMap<UUID, Team>()
    
    init {
        kotlinx.coroutines.runBlocking {
            initializeTables()
        }
    }
    
    private suspend fun initializeTables() {
        databaseManager.executeUpdate("""
            CREATE TABLE IF NOT EXISTS teams (
                id VARCHAR(36) PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                game_id VARCHAR(36),
                created_at TIMESTAMP NOT NULL,
                INDEX idx_game_id (game_id)
            )
        """)
        
        databaseManager.executeUpdate("""
            CREATE TABLE IF NOT EXISTS team_players (
                team_id VARCHAR(36) NOT NULL,
                player_id VARCHAR(36) NOT NULL,
                joined_at TIMESTAMP NOT NULL,
                PRIMARY KEY (team_id, player_id),
                FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
            )
        """)
    }
    
    override suspend fun findById(id: UUID): Team? = withContext(Dispatchers.IO) {
        teamCache[id] ?: run {
            val sql = "SELECT * FROM teams WHERE id = ?"
            val result = databaseManager.executeQuery(sql, listOf(id.toString())) { rs ->
                if (rs.next()) {
                    Team(
                        id = UUID.fromString(rs.getString("id")),
                        name = rs.getString("name"),
                        players = emptyList() // Load players separately
                    )
                } else null
            }
            
            result?.let { team ->
                teamCache[team.id] = team
            }
            result
        }
    }
    
    override suspend fun save(team: Team): Team = withContext(Dispatchers.IO) {
        val existingTeam = findById(team.id)
        
        if (existingTeam == null) {
            // Insert new team
            val sql = "INSERT INTO teams (id, name, game_id, created_at) VALUES (?, ?, ?, NOW())"
            databaseManager.executeUpdate(sql, listOf(
                team.id.toString(),
                team.name,
                null // game_id can be null for now
            ))
        } else {
            // Update existing team
            val sql = "UPDATE teams SET name = ? WHERE id = ?"
            databaseManager.executeUpdate(sql, listOf(team.name, team.id.toString()))
        }
        
        // Save team players
        saveTeamPlayers(team)
        
        teamCache[team.id] = team
        team
    }
    
    override suspend fun delete(id: UUID): Boolean = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM teams WHERE id = ?"
        val result = databaseManager.executeUpdate(sql, listOf(id.toString()))
        teamCache.remove(id)
        result > 0
    }
    
    override suspend fun findByPlayerId(playerId: UUID): Team? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT t.* FROM teams t
            INNER JOIN team_players tp ON t.id = tp.team_id
            WHERE tp.player_id = ?
            LIMIT 1
        """
        
        databaseManager.executeQuery(sql, listOf(playerId.toString())) { rs ->
            if (rs.next()) {
                Team(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    players = emptyList() // Load players separately
                )
            } else null
        }
    }
    
    override suspend fun findByGameId(gameId: UUID): List<Team> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM teams WHERE game_id = ?"
        val result = databaseManager.executeQuery(sql, listOf(gameId.toString())) { rs ->
            val teams = mutableListOf<Team>()
            while (rs.next()) {
                teams.add(
                    Team(
                        id = UUID.fromString(rs.getString("id")),
                        name = rs.getString("name"),
                        players = emptyList() // Load players separately
                    )
                )
            }
            teams
        }
        result ?: emptyList()
    }
    
    override suspend fun addPlayerToTeam(teamId: UUID, playerId: UUID): Boolean = withContext(Dispatchers.IO) {
        val sql = "INSERT INTO team_players (team_id, player_id, joined_at) VALUES (?, ?, NOW())"
        val result = databaseManager.executeUpdate(sql, listOf(teamId.toString(), playerId.toString()))
        result > 0
    }
    
    override suspend fun removePlayerFromTeam(teamId: UUID, playerId: UUID): Boolean = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM team_players WHERE team_id = ? AND player_id = ?"
        val result = databaseManager.executeUpdate(sql, listOf(teamId.toString(), playerId.toString()))
        result > 0
    }
    
    override suspend fun getTeamSize(teamId: UUID): Int = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM team_players WHERE team_id = ?"
        databaseManager.executeQuery(sql, listOf(teamId.toString())) { rs ->
            if (rs.next()) rs.getInt(1) else 0
        } ?: 0
    }
    
    override suspend fun isTeamFull(teamId: UUID, maxSize: Int): Boolean = withContext(Dispatchers.IO) {
        getTeamSize(teamId) >= maxSize
    }
    
    override suspend fun deleteTeamsByGameId(gameId: UUID): Int = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM teams WHERE game_id = ?"
        databaseManager.executeUpdate(sql, listOf(gameId.toString()))
    }
    
    private suspend fun saveTeamPlayers(team: Team) {
        // Clear existing players
        val deleteSql = "DELETE FROM team_players WHERE team_id = ?"
        databaseManager.executeUpdate(deleteSql, listOf(team.id.toString()))
        
        // Add current players
        team.players.forEach { player ->
            addPlayerToTeam(team.id, player.id)
        }
    }
}