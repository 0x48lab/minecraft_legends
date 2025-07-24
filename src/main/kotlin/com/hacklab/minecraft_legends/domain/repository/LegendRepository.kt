package com.hacklab.minecraft_legends.domain.repository

import com.hacklab.minecraft_legends.domain.entity.AbilityCooldown
import com.hacklab.minecraft_legends.domain.entity.Legend
import com.hacklab.minecraft_legends.domain.entity.LegendSelection
import java.util.*

interface LegendRepository {
    // レジェンド管理
    suspend fun findLegendById(id: String): Legend?
    suspend fun findAllLegends(): List<Legend>
    suspend fun findEnabledLegends(): List<Legend>
    suspend fun isLegendEnabled(id: String): Boolean
    
    // レジェンド選択管理
    suspend fun saveLegendSelection(selection: LegendSelection): Boolean
    suspend fun findLegendSelection(gameId: UUID, playerId: UUID): LegendSelection?
    suspend fun findLegendSelectionsByGame(gameId: UUID): List<LegendSelection>
    suspend fun findLegendSelectionsByTeam(gameId: UUID, teamId: UUID): List<LegendSelection>
    suspend fun deleteLegendSelection(gameId: UUID, playerId: UUID): Boolean
    suspend fun clearGameSelections(gameId: UUID): Boolean
    
    // アビリティクールダウン管理
    suspend fun saveAbilityCooldown(cooldown: AbilityCooldown): Boolean
    suspend fun findAbilityCooldown(
        playerId: UUID, 
        legendId: String, 
        abilityType: com.hacklab.minecraft_legends.domain.entity.AbilityType
    ): AbilityCooldown?
    suspend fun deleteAbilityCooldown(
        playerId: UUID, 
        legendId: String, 
        abilityType: com.hacklab.minecraft_legends.domain.entity.AbilityType
    ): Boolean
    suspend fun deleteExpiredCooldowns(): Int
    suspend fun deletePlayerCooldowns(playerId: UUID): Boolean
    
    // 統計・検索
    suspend fun getMostUsedLegends(limit: Int = 10): List<Pair<String, Long>>
    suspend fun getLegendUsageByPlayer(playerId: UUID): Map<String, Long>
    suspend fun getTeamLegendComposition(): Map<String, Int>
    
    // レジェンド可用性チェック
    suspend fun isLegendAvailableForTeam(gameId: UUID, teamId: UUID, legendId: String): Boolean
    suspend fun getAvailableLegendsForTeam(gameId: UUID, teamId: UUID): List<Legend>
}