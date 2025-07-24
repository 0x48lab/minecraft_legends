package com.hacklab.minecraft_legends.domain.entity

import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.*

abstract class Legend(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean = true
) {
    abstract fun onSelect(player: Player)
    abstract fun onDeselect(player: Player)
    abstract fun useAbility(player: Player, abilityType: AbilityType): Boolean
    abstract fun getAbilityCooldown(abilityType: AbilityType): Long
    abstract fun isAbilityReady(player: Player, abilityType: AbilityType): Boolean
    abstract fun getPassiveEffects(): List<PassiveEffect>
    
    protected fun setCooldown(player: Player, abilityType: AbilityType, seconds: Long) {
        val cooldownKey = "${this.id}_${abilityType.name}_cooldown"
        // プレイヤーのメタデータに保存する実装はInfrastructure層で行う
    }
    
    protected fun getCooldownRemaining(player: Player, abilityType: AbilityType): Long {
        val cooldownKey = "${this.id}_${abilityType.name}_cooldown"
        // プレイヤーのメタデータから取得する実装はInfrastructure層で行う
        return 0L
    }
}

enum class AbilityType {
    TACTICAL,
    ULTIMATE,
    PASSIVE
}

data class PassiveEffect(
    val type: PassiveEffectType,
    val value: Double,
    val description: String
)

enum class PassiveEffectType {
    SPEED_BOOST,
    DAMAGE_REDUCTION,
    HEALING_BOOST,
    RANGE_INCREASE,
    COOLDOWN_REDUCTION
}

data class LegendSelection(
    val gameId: UUID,
    val teamId: UUID,
    val playerId: UUID,
    val selectedLegend: String?,
    val isLocked: Boolean = false,
    val selectionTime: LocalDateTime = LocalDateTime.now()
) {
    fun selectLegend(legendId: String): LegendSelection = copy(
        selectedLegend = legendId,
        selectionTime = LocalDateTime.now()
    )
    
    fun lock(): LegendSelection = copy(isLocked = true)
    
    fun unlock(): LegendSelection = copy(isLocked = false)
}

data class AbilityCooldown(
    val playerId: UUID,
    val legendId: String,
    val abilityType: AbilityType,
    val cooldownEndTime: LocalDateTime
) {
    val isExpired: Boolean get() = LocalDateTime.now().isAfter(cooldownEndTime)
    val remainingSeconds: Long get() = if (isExpired) 0L else 
        java.time.Duration.between(LocalDateTime.now(), cooldownEndTime).seconds
}

// Simple data class for legend information (not the gameplay implementation)
data class LegendInfo(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean = true
)