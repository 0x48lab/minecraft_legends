package com.hacklab.minecraft_legends.domain.repository

import com.hacklab.minecraft_legends.domain.entity.Game
import com.hacklab.minecraft_legends.domain.entity.GameState
import java.time.LocalDateTime
import java.util.*

interface GameRepository {
    suspend fun findById(id: UUID): Game?
    suspend fun save(game: Game): Game
    suspend fun delete(id: UUID): Boolean
    suspend fun exists(id: UUID): Boolean
    suspend fun findAll(): List<Game>
    
    // ゲーム状態管理
    suspend fun findActiveGame(): Game?
    suspend fun findByState(state: GameState): List<Game>
    suspend fun findRecentGames(limit: Int = 10): List<Game>
    suspend fun findGamesByDateRange(start: LocalDateTime, end: LocalDateTime): List<Game>
    
    // プレイヤー・チーム関連
    suspend fun findGamesByPlayerId(playerId: UUID): List<Game>
    suspend fun findGamesByTeamId(teamId: UUID): List<Game>
    
    // 統計
    suspend fun getGameCount(): Long
    suspend fun getAverageGameDuration(): Double
    suspend fun getWinRateByTeam(teamId: UUID): Double
    suspend fun getWinRateByPlayer(playerId: UUID): Double
    
    // ゲーム履歴のクリーンアップ
    suspend fun deleteOldGames(olderThan: LocalDateTime): Int
}