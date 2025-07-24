package com.hacklab.minecraft_legends.presentation.command

import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import org.bukkit.plugin.Plugin

class CommandManager(private val plugin: Plugin) {
    private val logger = LoggerFactory.getLogger()
    private val commands = mutableMapOf<String, BaseCommand>()
    
    fun registerCommand(command: BaseCommand) {
        val pluginCommand = plugin.getCommand(command.name)
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command)
            pluginCommand.tabCompleter = command
            
            commands[command.name] = command
            logger.info("Registered command: ${command.name}")
        } else {
            logger.error("Failed to register command: ${command.name} (not found in plugin.yml)")
        }
    }
    
    fun unregisterAll() {
        commands.forEach { (name, _) ->
            val pluginCommand = plugin.getCommand(name)
            pluginCommand?.setExecutor(null)
            pluginCommand?.tabCompleter = null
        }
        commands.clear()
    }
    
    fun getCommand(name: String): BaseCommand? {
        return commands[name]
    }
}