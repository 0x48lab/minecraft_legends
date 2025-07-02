package com.hacklab.minecraft_legends.infrastructure.repository

import com.hacklab.minecraft_legends.domain.entity.*
import com.hacklab.minecraft_legends.domain.repository.SupplyBoxRepository
import com.hacklab.minecraft_legends.infrastructure.database.DatabaseManager
import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SupplyBoxRepositoryImpl(
    private val plugin: Plugin,
    private val databaseManager: DatabaseManager
) : SupplyBoxRepository {
    
    private val logger = LoggerFactory.getLogger()
    private val supplyBoxCache = ConcurrentHashMap<UUID, SupplyBox>()
    private val lootTableCache = ConcurrentHashMap<String, LootTable>()
    
    init {
        // Use runBlocking for init as it's called during plugin startup
        kotlinx.coroutines.runBlocking {
            initializeTables()
        }
        loadLootTablesFromFile()
    }
    
    private suspend fun initializeTables() {
        databaseManager.executeUpdate("""
            CREATE TABLE IF NOT EXISTS supply_boxes (
                id VARCHAR(36) PRIMARY KEY,
                game_id VARCHAR(36) NOT NULL,
                location_x DOUBLE NOT NULL,
                location_y DOUBLE NOT NULL,
                location_z DOUBLE NOT NULL,
                world_name VARCHAR(100) NOT NULL,
                tier VARCHAR(50) NOT NULL,
                status VARCHAR(50) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                opened_at TIMESTAMP,
                opened_by VARCHAR(36),
                INDEX idx_game_id (game_id),
                INDEX idx_status (status)
            )
        """)
        
        databaseManager.executeUpdate("""
            CREATE TABLE IF NOT EXISTS supply_box_contents (
                id INT AUTO_INCREMENT PRIMARY KEY,
                supply_box_id VARCHAR(36) NOT NULL,
                material VARCHAR(100) NOT NULL,
                amount INT NOT NULL,
                rarity VARCHAR(50) NOT NULL,
                custom_name TEXT,
                lore TEXT,
                potion_type VARCHAR(50),
                FOREIGN KEY (supply_box_id) REFERENCES supply_boxes(id) ON DELETE CASCADE
            )
        """)
        
        databaseManager.executeUpdate("""
            CREATE TABLE IF NOT EXISTS supply_box_enchantments (
                id INT AUTO_INCREMENT PRIMARY KEY,
                content_id INT NOT NULL,
                enchantment_type VARCHAR(100) NOT NULL,
                level INT NOT NULL,
                chance DOUBLE NOT NULL,
                FOREIGN KEY (content_id) REFERENCES supply_box_contents(id) ON DELETE CASCADE
            )
        """)
        
        databaseManager.executeUpdate("""
            CREATE TABLE IF NOT EXISTS supply_box_interactions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                supply_box_id VARCHAR(36) NOT NULL,
                player_id VARCHAR(36) NOT NULL,
                tier VARCHAR(50) NOT NULL,
                items_obtained TEXT NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                INDEX idx_player_id (player_id)
            )
        """)
    }
    
    override suspend fun findById(id: UUID): SupplyBox? = withContext(Dispatchers.IO) {
        supplyBoxCache[id] ?: run {
            val sql = "SELECT * FROM supply_boxes WHERE id = ?"
            val result = databaseManager.executeQuery(sql, listOf(id.toString())) { rs ->
                if (rs.next()) {
                    val supplyBox = mapResultSetToSupplyBox(rs)
                    supplyBoxCache[id] = supplyBox
                    supplyBox
                } else null
            }
            result
        }
    }
    
    override suspend fun findByGameId(gameId: UUID): List<SupplyBox> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM supply_boxes WHERE game_id = ? ORDER BY created_at"
        val result = databaseManager.executeQuery(sql, listOf(gameId.toString())) { rs ->
            val supplyBoxes = mutableListOf<SupplyBox>()
            while (rs.next()) {
                val supplyBox = mapResultSetToSupplyBox(rs)
                supplyBoxes.add(supplyBox)
                supplyBoxCache[supplyBox.id] = supplyBox
            }
            supplyBoxes
        }
        result ?: emptyList()
    }
    
    override suspend fun findAvailableByGameId(gameId: UUID): List<SupplyBox> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM supply_boxes WHERE game_id = ? AND status = ? ORDER BY created_at"
        val result = databaseManager.executeQuery(sql, listOf(gameId.toString(), SupplyBoxStatus.AVAILABLE.name)) { rs ->
            val supplyBoxes = mutableListOf<SupplyBox>()
            while (rs.next()) {
                val supplyBox = mapResultSetToSupplyBox(rs)
                supplyBoxes.add(supplyBox)
                supplyBoxCache[supplyBox.id] = supplyBox
            }
            supplyBoxes
        }
        result ?: emptyList()
    }
    
    override suspend fun save(supplyBox: SupplyBox): SupplyBox = withContext(Dispatchers.IO) {
        val existingBox = findById(supplyBox.id)
        
        if (existingBox == null) {
            // Insert new supply box
            val sql = """
                INSERT INTO supply_boxes (id, game_id, location_x, location_y, location_z, 
                    world_name, tier, status, created_at, opened_at, opened_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
            
            databaseManager.executeUpdate(sql, listOf(
                supplyBox.id.toString(),
                supplyBox.gameId.toString(),
                supplyBox.location.x,
                supplyBox.location.y,
                supplyBox.location.z,
                supplyBox.location.worldName,
                supplyBox.tier.name,
                supplyBox.status.name,
                java.sql.Timestamp.valueOf(supplyBox.createdAt),
                supplyBox.openedAt?.let { java.sql.Timestamp.valueOf(it) },
                supplyBox.openedBy?.toString()
            ))
            
            // Save contents
            saveSupplyBoxContents(supplyBox)
            
        } else {
            // Update existing supply box
            val sql = """
                UPDATE supply_boxes SET status = ?, opened_at = ?, opened_by = ? 
                WHERE id = ?
            """
            
            databaseManager.executeUpdate(sql, listOf(
                supplyBox.status.name,
                supplyBox.openedAt?.let { java.sql.Timestamp.valueOf(it) },
                supplyBox.openedBy?.toString(),
                supplyBox.id.toString()
            ))
        }
        
        supplyBoxCache[supplyBox.id] = supplyBox
        supplyBox
    }
    
    override suspend fun deleteByGameId(gameId: UUID): Boolean = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM supply_boxes WHERE game_id = ?"
        val result = databaseManager.executeUpdate(sql, listOf(gameId.toString()))
        
        // Remove from cache
        supplyBoxCache.values.removeIf { it.gameId == gameId }
        
        result > 0
    }
    
    override suspend fun markAsOpened(id: UUID, playerId: UUID): Boolean = withContext(Dispatchers.IO) {
        val sql = "UPDATE supply_boxes SET status = ?, opened_at = ?, opened_by = ? WHERE id = ?"
        val result = databaseManager.executeUpdate(sql, listOf(
            SupplyBoxStatus.OPENED.name,
            java.sql.Timestamp.valueOf(LocalDateTime.now()),
            playerId.toString(),
            id.toString()
        ))
        
        // Update cache
        supplyBoxCache[id]?.let { box ->
            supplyBoxCache[id] = box.open(playerId)
        }
        
        result > 0
    }
    
    override suspend fun markAsDestroyed(id: UUID): Boolean = withContext(Dispatchers.IO) {
        val sql = "UPDATE supply_boxes SET status = ? WHERE id = ?"
        val result = databaseManager.executeUpdate(sql, listOf(
            SupplyBoxStatus.DESTROYED.name,
            id.toString()
        ))
        
        // Update cache
        supplyBoxCache[id]?.let { box ->
            supplyBoxCache[id] = box.copy(status = SupplyBoxStatus.DESTROYED)
        }
        
        result > 0
    }
    
    override suspend fun loadSupplyBoxConfiguration(): SupplyBoxConfiguration = withContext(Dispatchers.IO) {
        // Return default configuration
        // TODO: Load from file
        SupplyBoxConfiguration()
    }
    
    override suspend fun saveSupplyBoxConfiguration(config: SupplyBoxConfiguration): Boolean = withContext(Dispatchers.IO) {
        // TODO: Save to file
        true
    }
    
    override suspend fun loadLootTable(tableName: String): LootTable? = withContext(Dispatchers.IO) {
        lootTableCache[tableName]
    }
    
    override suspend fun saveLootTable(table: LootTable): Boolean = withContext(Dispatchers.IO) {
        lootTableCache[table.name] = table
        // TODO: Save to file
        true
    }
    
    override suspend fun getSupplyBoxStatistics(gameId: UUID): SupplyBoxStatistics = withContext(Dispatchers.IO) {
        val boxes = findByGameId(gameId)
        val totalSpawned = boxes.size
        val totalOpened = boxes.count { it.status == SupplyBoxStatus.OPENED }
        val openedByTier = boxes.filter { it.status == SupplyBoxStatus.OPENED }
            .groupBy { it.tier }
            .mapValues { it.value.size }
        
        val averageItemsPerBox = if (totalOpened > 0) {
            boxes.filter { it.status == SupplyBoxStatus.OPENED }
                .map { it.contents.size }
                .average()
        } else 0.0
        
        SupplyBoxStatistics(
            totalSpawned = totalSpawned,
            totalOpened = totalOpened,
            openedByTier = openedByTier,
            averageItemsPerBox = averageItemsPerBox,
            mostCommonItems = emptyList() // TODO: Calculate from database
        )
    }
    
    override suspend fun getPlayerSupplyBoxHistory(playerId: UUID): List<SupplyBoxInteraction> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT supply_box_id, player_id, tier, items_obtained, timestamp 
            FROM supply_box_interactions 
            WHERE player_id = ? 
            ORDER BY timestamp DESC
        """
        
        databaseManager.executeQuery(sql, listOf(playerId.toString())) { rs ->
            val interactions = mutableListOf<SupplyBoxInteraction>()
            while (rs.next()) {
                interactions.add(
                    SupplyBoxInteraction(
                        supplyBoxId = UUID.fromString(rs.getString("supply_box_id")),
                        playerId = UUID.fromString(rs.getString("player_id")),
                        tier = SupplyBoxTier.valueOf(rs.getString("tier")),
                        itemsObtained = rs.getString("items_obtained").split(","),
                        timestamp = rs.getTimestamp("timestamp").toLocalDateTime()
                    )
                )
            }
            interactions
        } ?: emptyList()
    }
    
    private fun mapResultSetToSupplyBox(rs: ResultSet): SupplyBox {
        val id = UUID.fromString(rs.getString("id"))
        val contents = loadSupplyBoxContents(id)
        
        return SupplyBox(
            id = id,
            gameId = UUID.fromString(rs.getString("game_id")),
            location = BoxLocation(
                x = rs.getDouble("location_x"),
                y = rs.getDouble("location_y"),
                z = rs.getDouble("location_z"),
                worldName = rs.getString("world_name")
            ),
            tier = SupplyBoxTier.valueOf(rs.getString("tier")),
            status = SupplyBoxStatus.valueOf(rs.getString("status")),
            contents = contents,
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
            openedAt = rs.getTimestamp("opened_at")?.toLocalDateTime(),
            openedBy = rs.getString("opened_by")?.let { UUID.fromString(it) }
        )
    }
    
    private fun loadSupplyBoxContents(supplyBoxId: UUID): List<LootItem> {
        val sql = """
            SELECT c.*, e.enchantment_type, e.level, e.chance
            FROM supply_box_contents c
            LEFT JOIN supply_box_enchantments e ON c.id = e.content_id
            WHERE c.supply_box_id = ?
        """
        
        return databaseManager.executeQuery(sql, listOf(supplyBoxId.toString())) { rs ->
            val contentsMap = mutableMapOf<Int, MutableList<LootEnchantment>>()
            val itemsMap = mutableMapOf<Int, LootItem>()
            
            while (rs.next()) {
                val contentId = rs.getInt("id")
                
                if (!itemsMap.containsKey(contentId)) {
                    val material = Material.valueOf(rs.getString("material"))
                    val rarity = ItemRarity.valueOf(rs.getString("rarity"))
                    
                    itemsMap[contentId] = LootItem(
                        material = material,
                        amount = rs.getInt("amount"),
                        rarity = rarity,
                        customName = rs.getString("custom_name"),
                        lore = rs.getString("lore")?.split("\n") ?: emptyList(),
                        potionType = rs.getString("potion_type")
                    )
                }
                
                // Add enchantment if exists
                rs.getString("enchantment_type")?.let { enchantType ->
                    val enchantments = contentsMap.getOrPut(contentId) { mutableListOf() }
                    enchantments.add(
                        LootEnchantment(
                            type = enchantType,
                            level = rs.getInt("level"),
                            chance = rs.getDouble("chance")
                        )
                    )
                }
            }
            
            // Combine items with their enchantments
            itemsMap.map { (contentId, item) ->
                item.copy(enchantments = contentsMap[contentId] ?: emptyList())
            }
        } ?: emptyList()
    }
    
    private suspend fun saveSupplyBoxContents(supplyBox: SupplyBox) {
        supplyBox.contents.forEach { item ->
            val sql = """
                INSERT INTO supply_box_contents (supply_box_id, material, amount, rarity, 
                    custom_name, lore, potion_type)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """
            
            val contentId = databaseManager.executeUpdateWithGeneratedKeys(sql, listOf(
                supplyBox.id.toString(),
                item.material.name,
                item.amount,
                item.rarity.name,
                item.customName,
                item.lore.joinToString("\n"),
                item.potionType
            ))
            
            // Save enchantments
            if (contentId != null) {
                item.enchantments.forEach { enchant ->
                    val enchantSql = """
                        INSERT INTO supply_box_enchantments (content_id, enchantment_type, level, chance)
                        VALUES (?, ?, ?, ?)
                    """
                    
                    databaseManager.executeUpdate(enchantSql, listOf(
                        contentId,
                        enchant.type,
                        enchant.level,
                        enchant.chance
                    ))
                }
            }
        }
    }
    
    private fun loadLootTablesFromFile() {
        try {
            val lootTableFile = File(plugin.dataFolder, "loot-tables.yml")
            if (!lootTableFile.exists()) {
                logger.warn("Loot table file not found: ${lootTableFile.path}")
                return
            }
            
            val config = YamlConfiguration.loadConfiguration(lootTableFile)
            val lootTableSection = config.getConfigurationSection("loot-tables") ?: return
            
            for (tableName in lootTableSection.getKeys(false)) {
                val tableSection = lootTableSection.getConfigurationSection(tableName) ?: continue
                val items = mutableMapOf<ItemRarity, List<LootTableEntry>>()
                
                for (rarityName in tableSection.getKeys(false)) {
                    try {
                        val rarity = ItemRarity.valueOf(rarityName.uppercase())
                        val raritySection = tableSection.getConfigurationSection(rarityName) ?: continue
                        val entries = mutableListOf<LootTableEntry>()
                        
                        if (raritySection.isList("")) {
                            val itemList = raritySection.getList("") ?: continue
                            
                            for (itemData in itemList) {
                                if (itemData is Map<*, *>) {
                                    val material = Material.valueOf(itemData["material"] as String)
                                    val chance = (itemData["chance"] as Number).toDouble()
                                    val amount = itemData["amount"].toString()
                                    
                                    val enchantments = mutableListOf<LootEnchantment>()
                                    (itemData["enchantments"] as? List<*>)?.forEach { enchantData ->
                                        if (enchantData is Map<*, *>) {
                                            enchantments.add(
                                                LootEnchantment(
                                                    type = enchantData["type"] as String,
                                                    level = (enchantData["level"] as Number).toInt(),
                                                    chance = (enchantData["chance"] as? Number)?.toDouble() ?: 100.0
                                                )
                                            )
                                        }
                                    }
                                    
                                    entries.add(
                                        LootTableEntry(
                                            material = material,
                                            chance = chance,
                                            amount = amount,
                                            enchantments = enchantments,
                                            potionType = itemData["potion-type"] as? String
                                        )
                                    )
                                }
                            }
                        }
                        
                        items[rarity] = entries
                    } catch (e: Exception) {
                        logger.warn("Failed to parse rarity section: $rarityName", e)
                    }
                }
                
                lootTableCache[tableName] = LootTable(tableName, items)
                logger.info("Loaded loot table: $tableName with ${items.size} rarities")
            }
            
        } catch (e: Exception) {
            logger.error("Failed to load loot tables from file", e)
        }
    }
}