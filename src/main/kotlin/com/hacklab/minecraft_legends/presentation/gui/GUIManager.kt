package com.hacklab.minecraft_legends.presentation.gui

import com.hacklab.minecraft_legends.domain.entity.LegendInfo
import com.hacklab.minecraft_legends.infrastructure.i18n.MessageManager
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class GUIManager(
    private val plugin: Plugin,
    private val messageManager: MessageManager
) {
    
    private val legendSelectionGUI = LegendSelectionGUI(plugin, messageManager) { player, legend ->
        // Default handler - can be overridden
    }
    
    fun getLegendSelectionGUI(onSelection: (Player, LegendInfo) -> Unit): LegendSelectionGUI {
        return LegendSelectionGUI(plugin, messageManager, onSelection)
    }
    
    fun shutdown() {
        // Cleanup if needed
    }
}