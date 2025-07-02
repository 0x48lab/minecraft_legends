package com.hacklab.minecraft_legends.domain.entity.legends

import com.hacklab.minecraft_legends.domain.entity.AbilityType
import com.hacklab.minecraft_legends.domain.entity.Legend
import com.hacklab.minecraft_legends.domain.entity.PassiveEffect
import com.hacklab.minecraft_legends.domain.entity.PassiveEffectType
import org.bukkit.entity.Player

class Wraith(
    private val phaseDuration: Long = 3, // seconds
    private val phaseCooldown: Long = 35  // seconds
) : Legend(
    id = "wraith",
    name = "Wraith",
    description = "Temporarily become invisible with void walk"
) {
    
    override fun onSelect(player: Player) {
        // レイス選択時の処理
        // 特別な初期装備は無し
    }
    
    override fun onDeselect(player: Player) {
        // レイス解除時の処理
        // 進行中のフェーズ効果を解除
        if (isInPhase(player)) {
            endPhase(player)
        }
    }
    
    override fun useAbility(player: Player, abilityType: AbilityType): Boolean {
        return when (abilityType) {
            AbilityType.TACTICAL -> usePhaseWalk(player)
            AbilityType.PASSIVE -> true // パッシブは常時有効
            else -> false
        }
    }
    
    private fun usePhaseWalk(player: Player): Boolean {
        if (!isAbilityReady(player, AbilityType.TACTICAL)) {
            return false
        }
        
        if (isInPhase(player)) {
            return false // 既にフェーズ中
        }
        
        // フェーズウォーク開始
        startPhase(player)
        setCooldown(player, AbilityType.TACTICAL, phaseCooldown)
        
        // 持続時間後に自動終了するタスクをスケジュール
        // 実際の実装はInfrastructure層で行う
        
        return true
    }
    
    private fun startPhase(player: Player) {
        // プレイヤーを透明化
        // 攻撃不可能にする
        // 移動速度を少し上げる
        // 実際の実装はInfrastructure層で行う
        setPhaseState(player, true)
    }
    
    private fun endPhase(player: Player) {
        // 透明化を解除
        // 攻撃可能に戻す
        // 移動速度を元に戻す
        // 実際の実装はInfrastructure層で行う
        setPhaseState(player, false)
    }
    
    private fun isInPhase(player: Player): Boolean {
        // プレイヤーがフェーズ中かどうかチェック
        // 実際の実装はInfrastructure層で行う
        return false
    }
    
    private fun setPhaseState(player: Player, inPhase: Boolean) {
        // フェーズ状態を設定
        // 実際の実装はInfrastructure層で行う
    }
    
    override fun getAbilityCooldown(abilityType: AbilityType): Long {
        return when (abilityType) {
            AbilityType.TACTICAL -> phaseCooldown
            else -> 0L
        }
    }
    
    override fun isAbilityReady(player: Player, abilityType: AbilityType): Boolean {
        return getCooldownRemaining(player, abilityType) <= 0
    }
    
    override fun getPassiveEffects(): List<PassiveEffect> {
        return listOf(
            PassiveEffect(
                type = PassiveEffectType.DAMAGE_REDUCTION,
                value = 0.05, // 5% damage reduction when low health
                description = "Reduced damage when health is below 25%"
            )
        )
    }
}