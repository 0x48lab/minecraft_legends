package com.hacklab.minecraft_legends.presentation.listener

import com.hacklab.minecraft_legends.infrastructure.logger.Logger
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent

class DamageListener : Listener {
    private val logger = LoggerFactory.getLogger()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.isCancelled = true
        } else {
            logger.debug("onEntityDamage ${event.cause}")
        }
    }
}