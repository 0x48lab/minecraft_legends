package com.hacklab.minecraft_legends.domain.repository

import com.hacklab.minecraft_legends.domain.entity.Team
import java.util.*

interface TeamRepository {
    suspend fun findById(id: UUID): Team?
    suspend fun findByName(name: String): Team?
    suspend fun findByPlayerId(playerId: UUID): Team?
    suspend fun save(team: Team): Team
    suspend fun delete(id: UUID): Boolean
    suspend fun exists(id: UUID): Boolean
    suspend fun findAll(): List<Team>
    
    // チーム管理
    suspend fun findPublicTeams(): List<Team>
    suspend fun findTeamsWithSpace(): List<Team>
    suspend fun findTeamsByLeader(leaderId: UUID): List<Team>
    suspend fun findInactiveTeams(days: Int = 30): List<Team>
    
    // メンバー管理
    suspend fun addMemberToTeam(teamId: UUID, playerId: UUID): Boolean
    suspend fun removeMemberFromTeam(teamId: UUID, playerId: UUID): Boolean
    suspend fun changeTeamLeader(teamId: UUID, newLeaderId: UUID): Boolean
    
    // 統計
    suspend fun getTeamCount(): Long
    suspend fun getAverageTeamSize(): Double
}