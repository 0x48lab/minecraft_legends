package com.hacklab.minecraft_legends.presentation.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

abstract class BaseCommand(
    val name: String,
    val description: String,
    val usage: String,
    val permission: String? = null,
    val playerOnly: Boolean = false
) : CommandExecutor, TabCompleter {
    
    private val subCommands = mutableMapOf<String, SubCommand>()
    
    protected fun addSubCommand(subCommand: SubCommand) {
        subCommands[subCommand.name] = subCommand
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Check permission
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage("§cYou don't have permission to use this command!")
            return true
        }
        
        // Check if player only
        if (playerOnly && sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players!")
            return true
        }
        
        // Handle subcommands
        if (args.isNotEmpty()) {
            val subCommand = subCommands[args[0].lowercase()]
            if (subCommand != null) {
                val subArgs = args.drop(1).toTypedArray()
                return subCommand.execute(sender, subArgs)
            }
        }
        
        // Execute main command
        return execute(sender, args)
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1) {
            return subCommands.keys.filter { it.startsWith(args[0].lowercase()) }
        }
        
        if (args.size > 1) {
            val subCommand = subCommands[args[0].lowercase()]
            if (subCommand != null) {
                val subArgs = args.drop(1).toTypedArray()
                return subCommand.tabComplete(sender, subArgs)
            }
        }
        
        return emptyList()
    }
    
    protected fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§e=== $description ===")
        sender.sendMessage("§eUsage: $usage")
        
        if (subCommands.isNotEmpty()) {
            sender.sendMessage("§eSubcommands:")
            subCommands.values.forEach { subCommand ->
                sender.sendMessage("  §7- §e${subCommand.name}: §f${subCommand.description}")
            }
        }
    }
    
    abstract fun execute(sender: CommandSender, args: Array<out String>): Boolean
}

abstract class SubCommand(
    val name: String,
    val description: String,
    val usage: String = "",
    val permission: String? = null
) {
    abstract fun execute(sender: CommandSender, args: Array<out String>): Boolean
    
    open fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        return emptyList()
    }
}