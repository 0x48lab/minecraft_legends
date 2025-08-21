package com.hacklab.minecraft_legends.presentation.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent

class DamageListener : Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.isCancelled = true
        }
    }
}