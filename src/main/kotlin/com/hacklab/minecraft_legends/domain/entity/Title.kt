package com.hacklab.minecraft_legends.domain.entity

data class Title(
    val id: String,
    val name: String,
    val description: String,
    val conditions: TitleConditions,
    val priority: Int = 10,
    val isHidden: Boolean = false
) {
    fun checkConditions(statistics: PlayerStatistics): Boolean = conditions.evaluate(statistics)
}

sealed class TitleConditions {
    abstract fun evaluate(statistics: PlayerStatistics): Boolean
    
    data class Simple(val requirements: Map<String, Long>) : TitleConditions() {
        override fun evaluate(statistics: PlayerStatistics): Boolean {
            return requirements.all { (key, requiredValue) ->
                when (key) {
                    "kills" -> statistics.kills >= requiredValue
                    "deaths" -> statistics.deaths >= requiredValue
                    "wins" -> statistics.wins >= requiredValue
                    "damage" -> statistics.damage >= requiredValue
                    "revive_count" -> statistics.reviveCount >= requiredValue
                    "max_kills_in_game" -> statistics.maxKillsInGame >= requiredValue
                    "used_legends" -> statistics.usedLegends.size >= requiredValue
                    else -> {
                        // レジェンド固有の勝利数チェック
                        if (key.startsWith("legend_wins_")) {
                            val legend = key.removePrefix("legend_wins_")
                            val legendStats = statistics.legendStatistics[legend]
                            legendStats?.wins ?: 0 >= requiredValue
                        } else {
                            // カスタム統計チェック
                            val customStat = statistics.customStatistics[key]
                            when (customStat) {
                                is StatisticValue.LongValue -> customStat.value >= requiredValue
                                is StatisticValue.DoubleValue -> customStat.value >= requiredValue.toDouble()
                                else -> false
                            }
                        }
                    }
                }
            }
        }
    }
    
    data class And(val conditions: List<TitleConditions>) : TitleConditions() {
        override fun evaluate(statistics: PlayerStatistics): Boolean =
            conditions.all { it.evaluate(statistics) }
    }
    
    data class Or(val conditions: List<TitleConditions>) : TitleConditions() {
        override fun evaluate(statistics: PlayerStatistics): Boolean =
            conditions.any { it.evaluate(statistics) }
    }
    
    data class Not(val condition: TitleConditions) : TitleConditions() {
        override fun evaluate(statistics: PlayerStatistics): Boolean =
            !condition.evaluate(statistics)
    }
    
    data class Custom(val evaluator: (PlayerStatistics) -> Boolean) : TitleConditions() {
        override fun evaluate(statistics: PlayerStatistics): Boolean = evaluator(statistics)
    }
}

data class PlayerTitle(
    val playerId: java.util.UUID,
    val titleId: String,
    val earnedAt: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    val isEquipped: Boolean = false
)