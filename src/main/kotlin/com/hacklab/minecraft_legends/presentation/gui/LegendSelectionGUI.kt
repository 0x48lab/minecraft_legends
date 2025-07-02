package com.hacklab.minecraft_legends.presentation.gui

import com.hacklab.minecraft_legends.domain.entity.LegendInfo
import com.hacklab.minecraft_legends.infrastructure.i18n.MessageManager
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin
import java.util.*

class LegendSelectionGUI(
    private val plugin: Plugin,
    private val messageManager: MessageManager,
    private val onLegendSelected: (Player, LegendInfo) -> Unit
) : Listener {
    
    private val logger = LoggerFactory.getLogger()
    private val openInventories = mutableMapOf<UUID, Inventory>()
    
    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }
    
    fun openLegendSelection(player: Player, availableLegends: List<LegendInfo>) {
        val title = messageManager.getMessage("gui.select-legend", player)
        val inventory = Bukkit.createInventory(null, 27, title)
        
        // Add legend items
        availableLegends.forEachIndexed { index, legend ->
            val item = createLegendItem(legend, player)
            val slot = getSlotForIndex(index)
            inventory.setItem(slot, item)
        }
        
        // Add close button
        val closeItem = ItemStack(Material.BARRIER).apply {
            val meta = itemMeta
            meta?.setDisplayName(messageManager.getMessage("gui.close", player))
            itemMeta = meta
        }
        inventory.setItem(26, closeItem)
        
        // Fill empty slots with glass panes
        val glassPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            val meta = itemMeta
            meta?.setDisplayName(" ")
            itemMeta = meta
        }
        
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glassPane)
            }
        }
        
        openInventories[player.uniqueId] = inventory
        player.openInventory(inventory)
    }
    
    private fun createLegendItem(legend: LegendInfo, player: Player): ItemStack {
        val material = when (legend.id) {
            "pathfinder" -> Material.FISHING_ROD // Grappling hook
            "wraith" -> Material.ENDER_PEARL // Phase walk
            "lifeline" -> Material.GOLDEN_APPLE // Healing
            else -> Material.PAPER
        }
        
        val item = ItemStack(material)
        val meta = item.itemMeta
        
        if (meta != null) {
            // Set display name
            val legendName = messageManager.getMessage("legend.${legend.id}.name", player)
            meta.setDisplayName("§b§l$legendName")
            
            // Set lore
            val lore = mutableListOf<String>()
            
            // Description
            val description = messageManager.getMessage("legend.${legend.id}.description", player)
            lore.add("§7$description")
            lore.add("")
            
            // Ability
            val abilityName = messageManager.getMessage("legend.${legend.id}.ability", player)
            lore.add("§eAbility: §f$abilityName")
            
            // Ability description
            when (legend.id) {
                "pathfinder" -> {
                    lore.add("§7Shoot a grappling hook to quickly")
                    lore.add("§7reach distant locations")
                    lore.add("§7Cooldown: 15 seconds")
                }
                "wraith" -> {
                    lore.add("§7Enter the void to become invulnerable")
                    lore.add("§7and gain speed for 3 seconds")
                    lore.add("§7Cooldown: 25 seconds")
                }
                "lifeline" -> {
                    lore.add("§7Deploy a healing drone that")
                    lore.add("§7heals nearby teammates")
                    lore.add("§7Cooldown: 45 seconds")
                }
            }
            
            lore.add("")
            lore.add(messageManager.getMessage("gui.legend-info", player, legendName))
            
            meta.lore = lore
            item.itemMeta = meta
        }
        
        return item
    }
    
    private fun getSlotForIndex(index: Int): Int {
        // Center the legends in the middle row
        return when (index) {
            0 -> 11
            1 -> 13
            2 -> 15
            else -> 10 + index
        }
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = openInventories[player.uniqueId] ?: return
        
        if (event.inventory != inventory) return
        
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return
        
        // Handle close button
        if (clickedItem.type == Material.BARRIER) {
            player.closeInventory()
            return
        }
        
        // Handle legend selection
        val legend = when (clickedItem.type) {
            Material.FISHING_ROD -> LegendInfo(
                id = "pathfinder",
                name = "Pathfinder",
                description = "Forward Scout"
            )
            Material.ENDER_PEARL -> LegendInfo(
                id = "wraith",
                name = "Wraith", 
                description = "Interdimensional Skirmisher"
            )
            Material.GOLDEN_APPLE -> LegendInfo(
                id = "lifeline",
                name = "Lifeline",
                description = "Combat Medic"
            )
            else -> return
        }
        
        player.closeInventory()
        onLegendSelected(player, legend)
        
        // Send confirmation message
        val legendName = messageManager.getMessage("legend.${legend.id}.name", player)
        val message = messageManager.getMessage("legend.selected", player, legendName)
        player.sendMessage(message)
        
        // Play sound
        player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
    }
    
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        openInventories.remove(player.uniqueId)
    }
    
    fun closeAll() {
        openInventories.keys.toList().forEach { playerId ->
            Bukkit.getPlayer(playerId)?.closeInventory()
        }
        openInventories.clear()
    }
}

