package com.hacklab.minecraft_legends.domain.entity.legends

import com.hacklab.minecraft_legends.domain.entity.AbilityType
import com.hacklab.minecraft_legends.domain.entity.Legend
import com.hacklab.minecraft_legends.domain.entity.PassiveEffect
import com.hacklab.minecraft_legends.domain.entity.PassiveEffectType
import org.bukkit.entity.Player

class Lifeline(
    private val healAmount: Double = 6.0, // hearts
    private val healRange: Double = 5.0,   // blocks
    private val healCooldown: Long = 45    // seconds
) : Legend(
    id = "lifeline",
    name = "Lifeline",
    description = "Heal and revive teammates"
) {
    
    override fun onSelect(player: Player) {
        // ライフライン選択時の処理
        // 特別な初期装備は無し
    }
    
    override fun onDeselect(player: Player) {
        // ライフライン解除時の処理
        // 進行中のヒール効果を解除
        cancelActiveHealing(player)
    }
    
    override fun useAbility(player: Player, abilityType: AbilityType): Boolean {
        return when (abilityType) {
            AbilityType.TACTICAL -> useCombatMedic(player)
            AbilityType.PASSIVE -> true // パッシブは常時有効
            else -> false
        }
    }
    
    private fun useCombatMedic(player: Player): Boolean {
        if (!isAbilityReady(player, AbilityType.TACTICAL)) {
            return false
        }
        
        // 範囲内のチームメイトを検索して回復
        val healedPlayers = healNearbyTeammates(player)
        
        if (healedPlayers.isEmpty()) {
            return false // 回復対象がいない
        }
        
        setCooldown(player, AbilityType.TACTICAL, healCooldown)
        return true
    }
    
    private fun healNearbyTeammates(player: Player): List<Player> {
        val healedPlayers = mutableListOf<Player>()
        
        // 範囲内のプレイヤーを検索
        val nearbyPlayers = player.world.players.filter { target ->
            target != player && 
            target.location.distance(player.location) <= healRange &&
            isTeammate(player, target)
        }
        
        // チームメイトを回復
        nearbyPlayers.forEach { teammate ->
            healPlayer(teammate)
            healedPlayers.add(teammate)
        }
        
        // 自分自身も回復
        healPlayer(player)
        healedPlayers.add(player)
        
        return healedPlayers
    }
    
    private fun healPlayer(player: Player) {
        val currentHealth = player.health
        val maxHealth = player.maxHealth
        val newHealth = minOf(currentHealth + healAmount, maxHealth)
        player.health = newHealth
        
        // ヒールエフェクトを表示
        // 実際の実装はInfrastructure層で行う
    }
    
    private fun isTeammate(player1: Player, player2: Player): Boolean {
        // チームメイトかどうかチェック
        // 実際の実装はInfrastructure層で行う
        return false
    }
    
    private fun cancelActiveHealing(player: Player) {
        // 進行中のヒール効果をキャンセル
        // 実際の実装はInfrastructure層で行う
    }
    
    override fun getAbilityCooldown(abilityType: AbilityType): Long {
        return when (abilityType) {
            AbilityType.TACTICAL -> healCooldown
            else -> 0L
        }
    }
    
    override fun isAbilityReady(player: Player, abilityType: AbilityType): Boolean {
        return getCooldownRemaining(player, abilityType) <= 0
    }
    
    override fun getPassiveEffects(): List<PassiveEffect> {
        return listOf(
            PassiveEffect(
                type = PassiveEffectType.HEALING_BOOST,
                value = 0.25, // 25% faster healing
                description = "Faster healing and reviving for self and teammates"
            ),
            PassiveEffect(
                type = PassiveEffectType.RANGE_INCREASE,
                value = 1.5, // 1.5x range for reviving
                description = "Increased revive range"
            )
        )
    }
}