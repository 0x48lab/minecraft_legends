package com.hacklab.minecraft_legends.domain.entity

import java.util.*

data class PlayerStatistics(
    val playerId: UUID,
    val kills: Long = 0,
    val deaths: Long = 0,
    val wins: Long = 0,
    val damage: Long = 0,
    val maxKillsInGame: Int = 0,
    val reviveCount: Long = 0,
    val usedLegends: Set<String> = emptySet(),
    val legendStatistics: Map<String, LegendStatistics> = emptyMap(),
    val customStatistics: Map<String, StatisticValue> = emptyMap()
) {
    val kdRatio: Double
        get() = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()
    
    val winRate: Double
        get() {
            val totalGames = wins + deaths
            return if (totalGames > 0) (wins.toDouble() / totalGames) * 100.0 else 0.0
        }
    
    fun addKill(): PlayerStatistics = copy(kills = kills + 1)
    
    fun addDeath(): PlayerStatistics = copy(deaths = deaths + 1)
    
    fun addWin(): PlayerStatistics = copy(wins = wins + 1)
    
    fun addDamage(amount: Long): PlayerStatistics = copy(damage = damage + amount)
    
    fun addRevive(): PlayerStatistics = copy(reviveCount = reviveCount + 1)
    
    fun updateMaxKills(kills: Int): PlayerStatistics = 
        copy(maxKillsInGame = maxOf(maxKillsInGame, kills))
    
    fun addUsedLegend(legend: String): PlayerStatistics = 
        copy(usedLegends = usedLegends + legend)
    
    fun updateLegendStatistics(legend: String, stats: LegendStatistics): PlayerStatistics =
        copy(legendStatistics = legendStatistics + (legend to stats))
    
    fun setCustomStatistic(key: String, value: StatisticValue): PlayerStatistics =
        copy(customStatistics = customStatistics + (key to value))
    
    fun incrementCustomStatistic(key: String, amount: Long = 1): PlayerStatistics {
        val current = customStatistics[key] ?: StatisticValue.LongValue(0)
        return when (current) {
            is StatisticValue.LongValue -> setCustomStatistic(key, StatisticValue.LongValue(current.value + amount))
            is StatisticValue.DoubleValue -> setCustomStatistic(key, StatisticValue.DoubleValue(current.value + amount))
            else -> this
        }
    }
}

data class LegendStatistics(
    val kills: Long = 0,
    val deaths: Long = 0,
    val wins: Long = 0,
    val damage: Long = 0,
    val timePlayed: Long = 0  // minutes
) {
    val kdRatio: Double
        get() = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()
    
    fun addKill(): LegendStatistics = copy(kills = kills + 1)
    fun addDeath(): LegendStatistics = copy(deaths = deaths + 1)
    fun addWin(): LegendStatistics = copy(wins = wins + 1)
    fun addDamage(amount: Long): LegendStatistics = copy(damage = damage + amount)
    fun addTimePlayed(minutes: Long): LegendStatistics = copy(timePlayed = timePlayed + minutes)
}

sealed class StatisticValue {
    data class LongValue(val value: Long) : StatisticValue()
    data class DoubleValue(val value: Double) : StatisticValue()
    data class StringValue(val value: String) : StatisticValue()
    data class BooleanValue(val value: Boolean) : StatisticValue()
}