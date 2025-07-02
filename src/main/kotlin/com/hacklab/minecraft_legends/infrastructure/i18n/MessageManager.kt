package com.hacklab.minecraft_legends.infrastructure.i18n

import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.*

interface MessageManager {
    fun getMessage(key: String, locale: String = "en"): String
    fun getMessage(key: String, player: Player): String
    fun getMessage(key: String, locale: String, vararg args: Any): String
    fun getMessage(key: String, player: Player, vararg args: Any): String
    fun getPlayerLocale(player: Player): String
    fun reloadMessages()
}

class MessageManagerImpl(private val plugin: Plugin) : MessageManager {
    
    private val logger = LoggerFactory.getLogger()
    private val messages = mutableMapOf<String, MutableMap<String, String>>()
    private val defaultLocale = "en"
    private val supportedLocales = listOf("en", "ja")
    
    init {
        loadMessages()
    }
    
    override fun getMessage(key: String, locale: String): String {
        val localeMessages = messages[locale] ?: messages[defaultLocale] ?: return key
        return localeMessages[key] ?: messages[defaultLocale]?.get(key) ?: key
    }
    
    override fun getMessage(key: String, player: Player): String {
        val locale = getPlayerLocale(player)
        return getMessage(key, locale)
    }
    
    override fun getMessage(key: String, locale: String, vararg args: Any): String {
        val message = getMessage(key, locale)
        return String.format(message, *args)
    }
    
    override fun getMessage(key: String, player: Player, vararg args: Any): String {
        val locale = getPlayerLocale(player)
        return getMessage(key, locale, *args)
    }
    
    override fun getPlayerLocale(player: Player): String {
        // Get player's client locale
        val playerLocale = player.locale.lowercase()
        
        // Check if we support this locale
        return when {
            playerLocale.startsWith("ja") -> "ja"
            playerLocale.startsWith("en") -> "en"
            else -> defaultLocale
        }
    }
    
    override fun reloadMessages() {
        messages.clear()
        loadMessages()
    }
    
    private fun loadMessages() {
        supportedLocales.forEach { locale ->
            loadLocaleMessages(locale)
        }
        
        // Ensure default locale is loaded
        if (!messages.containsKey(defaultLocale)) {
            logger.warn("Default locale '$defaultLocale' not found, creating empty message set")
            messages[defaultLocale] = mutableMapOf()
        }
    }
    
    private fun loadLocaleMessages(locale: String) {
        val fileName = "messages_$locale.yml"
        val file = File(plugin.dataFolder, fileName)
        
        if (!file.exists()) {
            // Try to save default from resources
            plugin.saveResource(fileName, false)
        }
        
        if (file.exists()) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val localeMessages = mutableMapOf<String, String>()
                
                // Load all messages recursively
                loadMessagesFromSection(config, "", localeMessages)
                
                messages[locale] = localeMessages
                logger.info("Loaded ${localeMessages.size} messages for locale: $locale")
            } catch (e: Exception) {
                logger.error("Failed to load messages for locale: $locale", e)
            }
        } else {
            logger.warn("Message file not found: $fileName")
        }
    }
    
    private fun loadMessagesFromSection(
        config: org.bukkit.configuration.ConfigurationSection,
        prefix: String,
        messages: MutableMap<String, String>
    ) {
        config.getKeys(false).forEach { key ->
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            
            when (val value = config.get(key)) {
                is String -> messages[fullKey] = value
                is org.bukkit.configuration.ConfigurationSection -> {
                    loadMessagesFromSection(value, fullKey, messages)
                }
                else -> {
                    logger.warn("Unexpected value type for key '$fullKey': ${value?.javaClass?.simpleName}")
                }
            }
        }
    }
}

// Common message keys
object MessageKeys {
    // General
    const val GENERAL_NO_PERMISSION = "general.no-permission"
    const val GENERAL_PLAYER_ONLY = "general.player-only"
    const val GENERAL_PLAYER_NOT_FOUND = "general.player-not-found"
    
    // Game messages
    const val GAME_CREATED = "game.created"
    const val GAME_STARTED = "game.started"
    const val GAME_ENDED = "game.ended"
    const val GAME_JOINED = "game.joined"
    const val GAME_LEFT = "game.left"
    const val GAME_FULL = "game.full"
    const val GAME_NOT_FOUND = "game.not-found"
    const val GAME_ALREADY_ACTIVE = "game.already-active"
    const val GAME_NOT_ACTIVE = "game.not-active"
    const val GAME_WAITING_PLAYERS = "game.waiting-players"
    
    // Team messages
    const val TEAM_JOINED = "team.joined"
    const val TEAM_FULL = "team.full"
    const val TEAM_ELIMINATED = "team.eliminated"
    const val TEAM_WINNER = "team.winner"
    
    // Ring messages
    const val RING_WARNING = "ring.warning"
    const val RING_MOVING = "ring.moving"
    const val RING_DAMAGE = "ring.damage"
    const val RING_PHASE = "ring.phase"
    
    // Legend messages
    const val LEGEND_SELECTED = "legend.selected"
    const val LEGEND_UNAVAILABLE = "legend.unavailable"
    const val LEGEND_ABILITY_READY = "legend.ability-ready"
    const val LEGEND_ABILITY_COOLDOWN = "legend.ability-cooldown"
    
    // Supply box messages
    const val SUPPLY_BOX_OPENED = "supply-box.opened"
    const val SUPPLY_BOX_EMPTY = "supply-box.empty"
    const val SUPPLY_BOX_LEGENDARY = "supply-box.legendary"
    
    // Respawn messages
    const val RESPAWN_BEACON_USED = "respawn.beacon-used"
    const val RESPAWN_BEACON_COOLDOWN = "respawn.beacon-cooldown"
    const val RESPAWN_BANNER_COLLECTED = "respawn.banner-collected"
    const val RESPAWN_BANNER_EXPIRED = "respawn.banner-expired"
    
    // Death messages
    const val DEATH_BY_PLAYER = "death.by-player"
    const val DEATH_BY_RING = "death.by-ring"
    const val DEATH_BY_FALL = "death.by-fall"
    
    // Statistics messages
    const val STATS_KILLS = "stats.kills"
    const val STATS_DEATHS = "stats.deaths"
    const val STATS_WINS = "stats.wins"
    const val STATS_DAMAGE = "stats.damage"
    const val STATS_KD_RATIO = "stats.kd-ratio"
}