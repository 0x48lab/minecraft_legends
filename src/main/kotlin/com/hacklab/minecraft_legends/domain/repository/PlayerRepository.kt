package com.hacklab.minecraft_legends.domain.repository

import com.hacklab.minecraft_legends.domain.entity.Player
import com.hacklab.minecraft_legends.domain.entity.PlayerStatistics
import java.util.*

interface PlayerRepository {
    suspend fun findById(id: UUID): Player?
    suspend fun findByName(name: String): Player?
    suspend fun save(player: Player): Player
    suspend fun delete(id: UUID): Boolean
    suspend fun exists(id: UUID): Boolean
    suspend fun findAll(): List<Player>
    suspend fun findByIds(ids: Set<UUID>): List<Player>
    
    // 統計関連
    suspend fun updateStatistics(playerId: UUID, statistics: PlayerStatistics): Boolean
    suspend fun getLeaderboard(limit: Int = 10): List<Player>
    suspend fun getLeaderboardByKills(limit: Int = 10): List<Player>
    suspend fun getLeaderboardByWins(limit: Int = 10): List<Player>
    suspend fun getLeaderboardByDamage(limit: Int = 10): List<Player>
    
    // 検索・フィルタリング
    suspend fun findByMinKills(minKills: Long): List<Player>
    suspend fun findByMinWins(minWins: Long): List<Player>
    suspend fun findRecentlyActive(days: Int = 7): List<Player>
}