package com.hacklab.minecraft_legends.domain.entity

import java.time.LocalDateTime
import java.util.*

data class Player(
    val id: UUID,
    val name: String,
    val statistics: PlayerStatistics,
    val settings: PlayerSettings = PlayerSettings(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastSeen: LocalDateTime = LocalDateTime.now()
) {
    fun updateLastSeen(): Player = copy(lastSeen = LocalDateTime.now())
    
    fun updateStatistics(newStats: PlayerStatistics): Player = copy(statistics = newStats)
    
    fun updateSettings(newSettings: PlayerSettings): Player = copy(settings = newSettings)
}