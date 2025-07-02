package com.hacklab.minecraft_legends.domain.entity

import java.time.LocalDateTime
import java.util.*

data class Team(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val leaderId: UUID,
    val memberIds: Set<UUID>,
    val maxSize: Int = 3,
    val isPublic: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastActive: LocalDateTime = LocalDateTime.now()
) {
    val size: Int get() = memberIds.size
    val isFull: Boolean get() = size >= maxSize
    val isEmpty: Boolean get() = memberIds.isEmpty()
    
    fun addMember(playerId: UUID): Team {
        require(!isFull) { "Team is already full" }
        require(playerId !in memberIds) { "Player is already in the team" }
        return copy(
            memberIds = memberIds + playerId,
            lastActive = LocalDateTime.now()
        )
    }
    
    fun removeMember(playerId: UUID): Team {
        require(playerId in memberIds) { "Player is not in the team" }
        val newMembers = memberIds - playerId
        val newLeader = if (leaderId == playerId && newMembers.isNotEmpty()) {
            newMembers.first()
        } else {
            leaderId
        }
        return copy(
            memberIds = newMembers,
            leaderId = newLeader,
            lastActive = LocalDateTime.now()
        )
    }
    
    fun changeLeader(newLeaderId: UUID): Team {
        require(newLeaderId in memberIds) { "New leader must be a team member" }
        return copy(
            leaderId = newLeaderId,
            lastActive = LocalDateTime.now()
        )
    }
    
    fun updateActivity(): Team = copy(lastActive = LocalDateTime.now())
    
    fun isLeader(playerId: UUID): Boolean = leaderId == playerId
    
    fun contains(playerId: UUID): Boolean = playerId in memberIds
}