package com.hacklab.minecraft_legends.domain.entity

import java.time.LocalDateTime
import java.util.*

data class Game(
    val id: UUID = UUID.randomUUID(),
    val worldName: String,
    val state: GameState,
    val teams: List<GameTeam>,
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null,
    val winnerTeamId: UUID? = null,
    val currentRingPhase: Int = 0,
    val nextRingTime: LocalDateTime? = null,
    val settings: GameSettings,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    val isActive: Boolean get() = state in listOf(GameState.WAITING, GameState.LEGEND_SELECTION, GameState.ACTIVE)
    val isFinished: Boolean get() = state == GameState.FINISHED
    val duration: Long? get() = if (startTime != null && endTime != null) {
        java.time.Duration.between(startTime, endTime).toMinutes()
    } else null
    
    fun start(): Game = copy(
        state = GameState.ACTIVE,
        startTime = LocalDateTime.now()
    )
    
    fun finish(winnerTeamId: UUID? = null): Game = copy(
        state = GameState.FINISHED,
        endTime = LocalDateTime.now(),
        winnerTeamId = winnerTeamId
    )
    
    fun updateState(newState: GameState): Game = copy(state = newState)
    
    fun updateRingPhase(phase: Int, nextTime: LocalDateTime? = null): Game = copy(
        currentRingPhase = phase,
        nextRingTime = nextTime
    )
    
    fun getTeamByPlayerId(playerId: UUID): GameTeam? =
        teams.find { team -> team.players.any { it.id == playerId } }
    
    fun getAliveTeams(): List<GameTeam> = teams.filter { it.isAlive }
    
    fun getAlivePlayers(): List<GamePlayer> = teams.flatMap { it.getAlivePlayers() }
}

data class GameTeam(
    val id: UUID,
    val name: String,
    val players: List<GamePlayer>,
    val kills: Int = 0,
    val placement: Int? = null,
    val eliminatedAt: LocalDateTime? = null
) {
    val isAlive: Boolean get() = players.any { it.isAlive }
    val aliveCount: Int get() = players.count { it.isAlive }
    
    fun getAlivePlayers(): List<GamePlayer> = players.filter { it.isAlive }
    
    fun addKill(): GameTeam = copy(kills = kills + 1)
    
    fun eliminate(placement: Int): GameTeam = copy(
        placement = placement,
        eliminatedAt = LocalDateTime.now()
    )
}

data class GamePlayer(
    val id: UUID,
    val name: String,
    val legend: String,
    val isAlive: Boolean = true,
    val kills: Int = 0,
    val damage: Long = 0,
    val revives: Int = 0,
    val deathTime: LocalDateTime? = null,
    val respawnBeaconUsed: Boolean = false
) {
    fun addKill(): GamePlayer = copy(kills = kills + 1)
    
    fun addDamage(amount: Long): GamePlayer = copy(damage = damage + amount)
    
    fun addRevive(): GamePlayer = copy(revives = revives + 1)
    
    fun die(): GamePlayer = copy(
        isAlive = false,
        deathTime = LocalDateTime.now()
    )
    
    fun respawn(): GamePlayer = copy(
        isAlive = true,
        respawnBeaconUsed = true,
        deathTime = null
    )
}

enum class GameState {
    WAITING,
    LEGEND_SELECTION,
    ACTIVE,
    FINISHED
}

data class GameSettings(
    val maxPlayers: Int = 24,
    val teamSize: Int = 3,
    val minPlayers: Int = 12,
    val worldSize: Int = 3000,
    val legendSelectionTime: Int = 60,
    val respawnBeaconCount: Int = 6,
    val enableCustomStatistics: Boolean = true
)