package com.hacklab.minecraft_legends.domain.repository

import com.hacklab.minecraft_legends.domain.entity.PlayerTitle
import com.hacklab.minecraft_legends.domain.entity.Title
import java.util.*

interface TitleRepository {
    suspend fun findTitleById(id: String): Title?
    suspend fun findAllTitles(): List<Title>
    suspend fun saveTitles(titles: List<Title>): Boolean
    suspend fun reloadTitles(): Boolean
    
    // プレイヤー称号管理
    suspend fun findPlayerTitlesByPlayerId(playerId: UUID): List<PlayerTitle>
    suspend fun findPlayerTitle(playerId: UUID, titleId: String): PlayerTitle?
    suspend fun savePlayerTitle(playerTitle: PlayerTitle): Boolean
    suspend fun deletePlayerTitle(playerId: UUID, titleId: String): Boolean
    suspend fun setEquippedTitle(playerId: UUID, titleId: String?): Boolean
    suspend fun getEquippedTitle(playerId: UUID): PlayerTitle?
    
    // 称号検索・統計
    suspend fun findTitlesByPriority(): List<Title>
    suspend fun findVisibleTitles(): List<Title>
    suspend fun getTitleEarnCount(titleId: String): Long
    suspend fun getRarestTitles(limit: Int = 10): List<Pair<Title, Long>>
    
    // 称号チェック
    suspend fun checkNewTitles(playerId: UUID): List<Title>
}