package com.hacklab.minecraft_legends.domain.entity.legends

import com.hacklab.minecraft_legends.domain.entity.AbilityType
import com.hacklab.minecraft_legends.domain.entity.Legend
import com.hacklab.minecraft_legends.domain.entity.PassiveEffect
import com.hacklab.minecraft_legends.domain.entity.PassiveEffectType
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class Pathfinder(
    private val grappleRange: Int = 30,
    private val grappleCooldown: Long = 25
) : Legend(
    id = "pathfinder",
    name = "Pathfinder",
    description = "High-speed movement with grappling hook"
) {
    
    private val grappleHook = ItemStack(Material.FISHING_ROD).apply {
        itemMeta = itemMeta?.apply {
            setDisplayName("§6Grappling Hook")
            lore = listOf(
                "§7Right-click to use grappling hook",
                "§7Range: ${grappleRange} blocks",
                "§7Cooldown: ${grappleCooldown}s"
            )
            isUnbreakable = true
        }
    }
    
    override fun onSelect(player: Player) {
        // グラップリングフックを与える
        player.inventory.addItem(grappleHook)
    }
    
    override fun onDeselect(player: Player) {
        // グラップリングフックを削除
        player.inventory.removeItem(grappleHook)
    }
    
    override fun useAbility(player: Player, abilityType: AbilityType): Boolean {
        return when (abilityType) {
            AbilityType.TACTICAL -> useGrapple(player)
            AbilityType.PASSIVE -> true // パッシブは常時有効
            else -> false
        }
    }
    
    private fun useGrapple(player: Player): Boolean {
        if (!isAbilityReady(player, AbilityType.TACTICAL)) {
            return false
        }
        
        // グラップリングフック使用ロジック
        // 実際の実装はInfrastructure層で行う
        setCooldown(player, AbilityType.TACTICAL, grappleCooldown)
        return true
    }
    
    override fun getAbilityCooldown(abilityType: AbilityType): Long {
        return when (abilityType) {
            AbilityType.TACTICAL -> grappleCooldown
            else -> 0L
        }
    }
    
    override fun isAbilityReady(player: Player, abilityType: AbilityType): Boolean {
        return getCooldownRemaining(player, abilityType) <= 0
    }
    
    override fun getPassiveEffects(): List<PassiveEffect> {
        return listOf(
            PassiveEffect(
                type = PassiveEffectType.SPEED_BOOST,
                value = 0.1, // 10% speed boost
                description = "Increased movement speed on ziplines"
            )
        )
    }
}